package com.zylitics.wzgp.resource.util;

import java.util.Collections;
import java.util.Map;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.ImageList;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceList;
import com.google.api.services.compute.model.InstancesSetLabelsRequest;
import com.google.api.services.compute.model.InstancesSetMachineTypeRequest;
import com.google.api.services.compute.model.InstancesSetServiceAccountRequest;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.Operation;
import com.zylitics.wzgp.resource.SharedDependencies;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;

public class ComputeCalls {

  private final ResourceExecutor executor;
  private final Compute compute;
  private final String project;
  private final String zone;
  public ComputeCalls(SharedDependencies sharedDep, ResourceExecutor executor) {
    this.executor = executor;
    this.compute = sharedDep.compute();
    this.project = sharedDep.apiCoreProps().getProjectId();
    this.zone = sharedDep.zone();
  }
  
  public Operation startInstance(String instanceName) throws Exception {
    Compute.Instances.Start startInstance = compute.instances().start(project, zone, instanceName);
    return executor.executeWithReattempt(startInstance);
  }
  
  public Operation stopInstance(String instanceName) throws Exception {
    Compute.Instances.Stop stopInstance = compute.instances().stop(project, zone, instanceName);
    return executor.executeWithReattempt(stopInstance);
  }
  
  public Operation deleteInstance(String instanceName) throws Exception {
    Compute.Instances.Delete deleteInstance =
        compute.instances().delete(project, zone, instanceName);
    return executor.executeWithReattempt(deleteInstance);
  }
  
  public Operation setMachineType(String instanceName, String machineType) throws Exception {
    InstancesSetMachineTypeRequest machineTypeReq = new InstancesSetMachineTypeRequest();
    machineTypeReq.setMachineType(String.format("zones/%s/machineTypes/%s"
        , zone, machineType));
    Compute.Instances.SetMachineType setMachineType =
        compute.instances().setMachineType(project, zone, instanceName, machineTypeReq);
    return executor.executeWithReattempt(setMachineType);
  }
  
  public Operation setServiceAccount(String instanceName, String email) throws Exception {
    InstancesSetServiceAccountRequest servAccReq = new InstancesSetServiceAccountRequest();
    servAccReq.setEmail(email);
    servAccReq.setScopes(Collections.singletonList(ComputeScopes.CLOUD_PLATFORM));
    Compute.Instances.SetServiceAccount setServAcc =
        compute.instances().setServiceAccount(project, zone, instanceName, servAccReq);
    return executor.executeWithReattempt(setServAcc);
  }
  
  public Operation setLabels(String instanceName, Map<String, String> labels) throws Exception {
    InstancesSetLabelsRequest labelReq = new InstancesSetLabelsRequest();
    labelReq.setLabels(labels);
    Compute.Instances.SetLabels setLabels =
        compute.instances().setLabels(project, zone, instanceName, labelReq);
    return executor.executeWithReattempt(setLabels);
  }
  
  public Operation setMetadata(String instanceName, Map<String, String> metadata) throws Exception {
    Metadata md = new Metadata();
    metadata.entrySet()
        .forEach(entry -> md.set(entry.getKey(), entry.getValue()));
    Compute.Instances.SetMetadata setMetadata =
        compute.instances().setMetadata(project, zone, instanceName, md);
    return executor.executeWithReattempt(setMetadata);
  }
  
  public Operation getZoneOperation(String operationName) throws Exception {
    Compute.ZoneOperations.Get getZOp = compute.zoneOperations().get(project, zone, operationName);
    return executor.executeWithReattempt(getZOp);
  }
  
  public Image getImageFromFamily(String imageFamily) throws Exception {
    Compute.Images.GetFromFamily getFromFamily =
        compute.images().getFromFamily(project, imageFamily);
    return executor.executeWithReattempt(getFromFamily);
  }
  
  public java.util.List<Image> listImages(String filter, long maxResults) throws Exception {
    Compute.Images.List listBuilder = compute.images().list(project);
    listBuilder.setMaxResults(maxResults);
    listBuilder.setFilter(filter);
    ImageList list = executor.executeWithReattempt(listBuilder); 
    return list.getItems();
  }
  
  public java.util.List<Instance> listInstances(String filter, long maxResults) throws Exception {
    Compute.Instances.List listBuilder = compute.instances().list(project, zone);
    listBuilder.setMaxResults(maxResults);
    listBuilder.setFilter(filter);
    InstanceList list = executor.executeWithReattempt(listBuilder);
    return list.getItems();
  }
  
  /**
   * @param instanceZone: could be the zone coming in request or actual zone where instance got
   * created that may defer from the one in request. 
   */
  public Instance getInstance(String instanceName, String instanceZone) throws Exception {
    Compute.Instances.Get getInstance =
        compute.instances().get(project, instanceZone, instanceName);
    return executor.executeWithReattempt(getInstance);
  }
}
