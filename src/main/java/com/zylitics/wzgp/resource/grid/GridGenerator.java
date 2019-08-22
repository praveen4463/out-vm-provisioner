package com.zylitics.wzgp.resource.grid;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.springframework.util.Assert;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.AttachedDiskInitializeParams;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Scheduling;
import com.google.api.services.compute.model.ServiceAccount;
import com.google.api.services.compute.model.Tags;
import com.zylitics.wzgp.resource.CompletedOperation;
import com.zylitics.wzgp.resource.SharedDependencies;
import com.zylitics.wzgp.resource.APICoreProperties.GridDefault;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.util.BuildGridLabels;
import com.zylitics.wzgp.util.Randoms;

public class GridGenerator extends AbstractGrid {
  
  private final Image sourceImage;
  private final Random random;
  
  public GridGenerator(SharedDependencies sharedDep
      , GridProperty gridProp
      , Image sourceImage
      , ResourceExecutor executor) {
    super(sharedDep, gridProp, executor);
    
    Assert.notNull(sourceImage, "sourceImage can't be null.");
    this.sourceImage = sourceImage;
    random = new Random();
  }
  
  /*
   * !!! Remember that after grid creation, grid's zone should always be taken from Operation
   * rather than the zone given by requester, since the zone could be different than the given. 
   */
  public CompletedOperation create() throws Exception {
    // first try creating with the zone given by requester, and re-attempt on random zones if
    // this fails.
    Compute.Instances.Insert insertInstance = buildNewGrid(sharedDep.zone());
    return executor.executeWithZonalReattempt(insertInstance
        , randomZone -> buildNewGrid(randomZone));
  }
  
  private Compute.Instances.Insert buildNewGrid(String gridZone) {
    GridDefault gridDefault = sharedDep.apiCoreProps().getGridDefault();
    
    String randomChars = new Randoms(random).generateRandom(10);
    String instanceName = String.join("-", sourceImage.getFamily(), randomChars, "vm");
    
    Set<String> tags = gridDefault.getTags();
    Map<String, String> labels = new BuildGridLabels(sourceImage
        , gridDefault.getLabels()
        , gridProp.getCustomLabels()
        , gridDefault.getImageSpecificLabelsKey()).build();
    Map<String, String> metadata = mergedMetadata();
    
    String machineType =
        Optional.ofNullable(gridProp.getMachineType()).orElse(gridDefault.getMachineType());
    String network = gridDefault.getNetwork();
    String serviceAccountEmail =
        Optional.ofNullable(gridProp.getServiceAccount()).orElse(gridDefault.getServiceAccount());
    boolean preemptible = gridProp.isPreemptible();
    
    // ************************************************************************
    
    // Initialize instance object.
    Instance instance = new Instance();
    instance.setName(instanceName);
    instance.setMachineType(String.format("zones/%s/machineTypes/%s"
        , gridZone, machineType));
    instance.setZone(gridZone);
    
    // Attach network interface
    NetworkInterface nif = new NetworkInterface();
    nif.setNetwork(String.format("global/networks/%s", network));
    AccessConfig accessConfig = new AccessConfig();
    accessConfig.setType("ONE_TO_ONE_NAT");
    accessConfig.setName("External NAT");
    nif.setAccessConfigs(Collections.singletonList(accessConfig));
    instance.setNetworkInterfaces(Collections.singletonList(nif));
    
    // Attach disk
    AttachedDisk disk = new AttachedDisk();
    disk.setBoot(true);
    disk.setAutoDelete(true);
    disk.setType("PERSISTENT");
    AttachedDiskInitializeParams initializeParams = new AttachedDiskInitializeParams();
    initializeParams.setDiskName(instanceName);
    initializeParams.setDiskSizeGb(50L);
    initializeParams.setSourceImage(String.format("global/images/family/%s"
        , sourceImage.getFamily()));
    initializeParams.setDiskType(String.format("zones/%s/diskTypes/pd-ssd"
        , gridZone));
    disk.setInitializeParams(initializeParams);
    instance.setDisks(Collections.singletonList(disk));
    
    // Add service account
    ServiceAccount serviceAccount = new ServiceAccount();
    serviceAccount.setEmail(serviceAccountEmail);
    serviceAccount.setScopes(
        Collections.singletonList(ComputeScopes.CLOUD_PLATFORM));
    instance.setServiceAccounts(Collections.singletonList(serviceAccount));
    
    instance.setScheduling(new Scheduling().setPreemptible(preemptible));
    
    // Add network tags
    Tags networkTags = new Tags();
    networkTags.setItems(new ArrayList<>(tags));
    instance.setTags(networkTags);
    
    // Add metadata
    Metadata md = new Metadata();
    md.putAll(metadata);
    instance.setMetadata(md);
    
    // Add labels
    instance.setLabels(labels);
    
    // Complete instance build
    // Won't use ComputeCalls here.
    try {
      return sharedDep.compute().instances().insert(
          sharedDep.apiCoreProps().getProjectId(), gridZone, instance);
    } catch (IOException io) {
      // Wrap, so that compiler won't complain using this method in lambda.
      throw new RuntimeException(io);
    }
  }
}
