package com.zylitics.wzgp.resource.grid;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.springframework.util.Assert;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.AttachedDiskInitializeParams;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Scheduling;
import com.google.api.services.compute.model.ServiceAccount;
import com.google.api.services.compute.model.Tags;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.CompletedOperation;
import com.zylitics.wzgp.resource.APICoreProperties.GridDefault;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.util.ResourceUtil;
import com.zylitics.wzgp.util.Randoms;

public class GridGenerator {
  
  private final Compute compute;
  private final APICoreProperties apiCoreProps; 
  private final ResourceExecutor executor;
  private final BuildProperty buildProp;
  private final GridProperty gridProp;
  private final Image sourceImage;
  
  private final String instanceName;
  
  public GridGenerator(Compute compute
      , APICoreProperties apiCoreProps
      , ResourceExecutor executor
      , BuildProperty buildProp
      , GridProperty gridProp
      , Image sourceImage) {
    this.compute = compute;
    this.apiCoreProps = apiCoreProps;
    this.executor = executor;
    this.buildProp = buildProp;
    this.gridProp = gridProp;
    
    Assert.notNull(sourceImage, "'sourceImage' can't be null.");
    this.sourceImage = sourceImage;
    
    String randomChars = new Randoms().generateRandom(10);
    instanceName = String.join("-", sourceImage.getFamily(), randomChars, "vm");
  }
  
  /*
   * !!! Remember that after grid creation, grid's zone should always be taken from Operation
   * rather than the zone given by requester, since the zone could be different than the given. 
   */
  public CompletedOperation create(String zone) throws Exception {
    // first try creating with the zone given by requester, and re-attempt on random zones if
    // this fails.
    Compute.Instances.Insert insertInstance = buildNewGrid(zone);
    return executor.executeWithZonalReattempt(insertInstance
        , randomZone -> buildNewGrid(randomZone), buildProp);
  }
  
  private Compute.Instances.Insert buildNewGrid(String gridZone) {
    GridDefault gridDefault = apiCoreProps.getGridDefault();
    
    Set<String> tags = gridDefault.getTags();
    Map<String, String> labels = buildGridLabels(sourceImage
        , gridDefault.getLabels()
        , gridProp.getCustomLabels()
        , gridDefault.getImageSpecificLabelsKey());
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
    
    // Attach machine
    instance.setMachineType(String.format("zones/%s/machineTypes/%s"
        , gridZone, machineType));
    
    // Attach network interface
    NetworkInterface nif = new NetworkInterface();
    nif.setNetwork(String.format("global/networks/%s", network));
    nif.setSubnetwork(ResourceUtil.getSubnetURLFromZone(gridZone));
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
    instance.setMetadata(ResourceUtil.getGCPMetadata(metadata));
    
    // Add labels
    instance.setLabels(labels);
    
    // Finish instance build
    // Won't use ComputeService here.
    try {
      return compute.instances().insert(apiCoreProps.getProjectId(), gridZone, instance);
    } catch (IOException io) {
      // Wrap, so that compiler won't complain using this method in lambda.
      throw new RuntimeException(io);
    }
  }
  
  /**
   * Merge image and server defined labels, image defined labels take precedence. Following are
   * the customizations required to build valid set of labels for new grid instance:
   * 1. There are few labels specific to image, we'll exclude them.
   * 2. We'll put some labels known only at runtime for the grid, such as source-image-family
   * 3. We'll customize some labels based on the specific inputs to the api.
   */
  private Map<String, String> buildGridLabels(Image image
      , Map<String, String> defaultLabels
      , Map<String, String> customLabels
      , Set<String> imageSpecificLabelKeys) {
    // first put default labels specified by server
    Map<String, String> mergedLabels = new HashMap<>();
    mergedLabels.putAll(defaultLabels);
    
    // put after getting labels from image and filter image specific keys.
    Map<String, String> gridLabelsFromImage = image.getLabels().entrySet().stream()
        .filter(entry -> !imageSpecificLabelKeys.contains(entry.getKey()))
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    mergedLabels.putAll(gridLabelsFromImage);
    
    // put in custom labels so that it overrides any matching entry.
    mergedLabels.putAll(customLabels);
    
    // put in labels using image properties.
    
    // reference source image of the grid as label.
    mergedLabels.put(ResourceUtil.LABEL_SOURCE_FAMILY, image.getFamily());
    
    return mergedLabels;
  }
  
  private Map<String, String> mergedMetadata() {
    GridDefault gridDefault = apiCoreProps.getGridDefault();
    Map<String, String> metadata = new HashMap<>();
    
    // first put server defined grid defaults.
    metadata.putAll(gridDefault.getMetadata());
    // merge user defined grid properties, replacing if there's a match.
    metadata.putAll(gridProp.getMetadata());
    return metadata;
  }
  
}
