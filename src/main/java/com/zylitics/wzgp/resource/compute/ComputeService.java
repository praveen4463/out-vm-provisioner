package com.zylitics.wzgp.resource.compute;

import java.util.Collections;
import java.util.Map;

import javax.annotation.Nullable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.Compute.Images;
import com.google.api.services.compute.Compute.Instances;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.compute.model.Disk;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.ImageList;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceList;
import com.google.api.services.compute.model.InstancesSetLabelsRequest;
import com.google.api.services.compute.model.InstancesSetMachineTypeRequest;
import com.google.api.services.compute.model.InstancesSetServiceAccountRequest;
import com.google.api.services.compute.model.Operation;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.util.ResourceUtil;
import com.zylitics.wzgp.web.FingerprintBasedUpdater;

@Service
@Scope("singleton")
public class ComputeService {

  private final Compute compute;
  private final ResourceExecutor executor;
  private final String project;
  
  @Autowired
  public ComputeService(Compute compute
      , ResourceExecutor executor
      , APICoreProperties apiCoreProps) {
    this.compute = compute;
    this.executor = executor;
    this.project = apiCoreProps.getProjectId();
  }
  
  public Operation startInstance(String instanceName
      , String zoneName
      , @Nullable BuildProperty buildProp) throws Exception {
    Instances.Start startInstance = compute.instances().start(project, zoneName, instanceName);
    return executor.executeWithReattempt(startInstance, buildProp);
  }
  
  public Operation stopInstance(String instanceName
      , String zoneName
      , @Nullable BuildProperty buildProp) throws Exception {
    Instances.Stop stopInstance = compute.instances().stop(project, zoneName, instanceName);
    return executor.executeWithReattempt(stopInstance, buildProp);
  }
  
  public Operation deleteInstance(String instanceName
      , String zoneName
      , @Nullable BuildProperty buildProp) throws Exception {
    Instances.Delete deleteInstance =
        compute.instances().delete(project, zoneName, instanceName);
    return executor.executeWithReattempt(deleteInstance, buildProp);
  }
  
  public Instance getInstance(String instanceName
      , String zoneName
      , @Nullable BuildProperty buildProp) throws Exception {
    Instances.Get getInstance =
        compute.instances().get(project, zoneName, instanceName);
    return executor.executeWithReattempt(getInstance, buildProp);
  }
  
  public Operation setMachineType(String instanceName
      , String machineType
      , String zoneName
      , @Nullable BuildProperty buildProp) throws Exception {
    InstancesSetMachineTypeRequest machineTypeReq = new InstancesSetMachineTypeRequest();
    machineTypeReq.setMachineType(String.format("zones/%s/machineTypes/%s"
        , zoneName, machineType));
    Instances.SetMachineType setMachineType =
        compute.instances().setMachineType(project, zoneName, instanceName, machineTypeReq);
    return executor.executeWithReattempt(setMachineType, buildProp);
  }
  
  public Operation setServiceAccount(String instanceName
      , String email
      , String zoneName
      , @Nullable BuildProperty buildProp) throws Exception {
    InstancesSetServiceAccountRequest servAccReq = new InstancesSetServiceAccountRequest();
    servAccReq.setEmail(email);
    servAccReq.setScopes(Collections.singletonList(ComputeScopes.CLOUD_PLATFORM));
    Instances.SetServiceAccount setServAcc =
        compute.instances().setServiceAccount(project, zoneName, instanceName, servAccReq);
    return executor.executeWithReattempt(setServAcc, buildProp);
  }
  
  /**
   * Usually consumed using {@link FingerprintBasedUpdater}
   * @param instanceName The name of instance to set labels to
   * @param labels that needs to be set
   * @param zoneName where the instance resides
   * @param currentFingerprint label-fingerprint currently set at GCP for this instance. It needs to
   * be up-to-date with GCP (use get() to fetch up-to-date instance from GCP)
   * @param buildProp BuildProperty instance
   * @return {@link Operation} representing set-labels operation
   * @throws Exception
   */
  public Operation setLabels(String instanceName
      , Map<String, String> labels
      , String zoneName
      , String currentFingerprint
      , @Nullable BuildProperty buildProp) throws Exception {
    InstancesSetLabelsRequest labelReq = new InstancesSetLabelsRequest();
    labelReq.setLabels(labels);
    labelReq.setLabelFingerprint(currentFingerprint);
    Instances.SetLabels setLabels =
        compute.instances().setLabels(project, zoneName, instanceName, labelReq);
    return executor.executeWithReattempt(setLabels, buildProp);
  }
  
  /**
   * Usually consumed using {@link FingerprintBasedUpdater}
   * @param instanceName The name of instance to set labels to
   * @param metadata that needs to be set
   * @param zoneName where the instance resides
   * @param currentFingerprint metadata-fingerprint currently set at GCP for this instance. It needs
   * to be up-to-date with GCP (use get() to fetch up-to-date instance from GCP)
   * @param buildProp BuildProperty instance
   * @return {@link Operation} representing set-metadata operation
   * @throws Exception
   */
  public Operation setMetadata(String instanceName
      , Map<String, String> metadata
      , String zoneName
      , String currentFingerprint
      , @Nullable BuildProperty buildProp) throws Exception {
    Instances.SetMetadata setMetadata =
        compute.instances().setMetadata(project, zoneName, instanceName
            , ResourceUtil.getGCPMetadata(metadata).setFingerprint(currentFingerprint));
    return executor.executeWithReattempt(setMetadata, buildProp);
  }
  
  public Image getImageFromFamily(String imageFamily
      , @Nullable BuildProperty buildProp) throws Exception {
    Images.GetFromFamily getFromFamily =
        compute.images().getFromFamily(project, imageFamily);
    return executor.executeWithReattempt(getFromFamily, buildProp);
  }
  
  public java.util.List<Image> listImages(String filter
      , long maxResults
      , @Nullable BuildProperty buildProp) throws Exception {
    Images.List listBuilder = compute.images().list(project);
    listBuilder.setMaxResults(maxResults);
    listBuilder.setFilter(filter);
    ImageList list = executor.executeWithReattempt(listBuilder, buildProp); 
    return list.getItems();
  }
  
  public java.util.List<Instance> listInstances(String filter
      , long maxResults
      , String zoneName
      , @Nullable BuildProperty buildProp) throws Exception {
    Instances.List listBuilder = compute.instances().list(project, zoneName);
    listBuilder.setMaxResults(maxResults);
    listBuilder.setFilter(filter);
    InstanceList list = executor.executeWithReattempt(listBuilder, buildProp);
    return list.getItems();
  }
  
  //TODO: pending unit test
  public Disk getDisk(String diskName
      , String zoneName
      , @Nullable BuildProperty buildProp) throws Exception {
    Compute.Disks.Get getDisk = compute.disks().get(project, zoneName, diskName);
    return executor.executeWithReattempt(getDisk, buildProp);
  }
}
