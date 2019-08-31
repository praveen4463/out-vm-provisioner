package com.zylitics.wzgp.resource.service;

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
      , String zone
      , @Nullable BuildProperty buildProp) throws Exception {
    Instances.Start startInstance = compute.instances().start(project, zone, instanceName);
    return executor.executeWithReattempt(startInstance, buildProp);
  }
  
  public Operation stopInstance(String instanceName
      , String zone
      , @Nullable BuildProperty buildProp) throws Exception {
    Instances.Stop stopInstance = compute.instances().stop(project, zone, instanceName);
    return executor.executeWithReattempt(stopInstance, buildProp);
  }
  
  public Operation deleteInstance(String instanceName
      , String zone
      , @Nullable BuildProperty buildProp) throws Exception {
    Instances.Delete deleteInstance =
        compute.instances().delete(project, zone, instanceName);
    return executor.executeWithReattempt(deleteInstance, buildProp);
  }
  
  public Instance getInstance(String instanceName
      , String zone
      , @Nullable BuildProperty buildProp) throws Exception {
    Instances.Get getInstance =
        compute.instances().get(project, zone, instanceName);
    return executor.executeWithReattempt(getInstance, buildProp);
  }
  
  public Operation setMachineType(String instanceName
      , String machineType
      , String zone
      , @Nullable BuildProperty buildProp) throws Exception {
    InstancesSetMachineTypeRequest machineTypeReq = new InstancesSetMachineTypeRequest();
    machineTypeReq.setMachineType(String.format("zones/%s/machineTypes/%s"
        , zone, machineType));
    Instances.SetMachineType setMachineType =
        compute.instances().setMachineType(project, zone, instanceName, machineTypeReq);
    return executor.executeWithReattempt(setMachineType, buildProp);
  }
  
  public Operation setServiceAccount(String instanceName
      , String email
      , String zone
      , @Nullable BuildProperty buildProp) throws Exception {
    InstancesSetServiceAccountRequest servAccReq = new InstancesSetServiceAccountRequest();
    servAccReq.setEmail(email);
    servAccReq.setScopes(Collections.singletonList(ComputeScopes.CLOUD_PLATFORM));
    Instances.SetServiceAccount setServAcc =
        compute.instances().setServiceAccount(project, zone, instanceName, servAccReq);
    return executor.executeWithReattempt(setServAcc, buildProp);
  }
  
  public Operation setLabels(String instanceName
      , Map<String, String> labels
      , String zone
      , @Nullable BuildProperty buildProp) throws Exception {
    InstancesSetLabelsRequest labelReq = new InstancesSetLabelsRequest();
    labelReq.setLabels(labels);
    Instances.SetLabels setLabels =
        compute.instances().setLabels(project, zone, instanceName, labelReq);
    return executor.executeWithReattempt(setLabels, buildProp);
  }
  
  public Operation setMetadata(String instanceName
      , Map<String, String> metadata
      , String zone
      , @Nullable BuildProperty buildProp) throws Exception {
    Instances.SetMetadata setMetadata =
        compute.instances().setMetadata(project, zone, instanceName
            , ResourceUtil.getGCPMetadata(metadata));
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
      , String zone
      , @Nullable BuildProperty buildProp) throws Exception {
    Instances.List listBuilder = compute.instances().list(project, zone);
    listBuilder.setMaxResults(maxResults);
    listBuilder.setFilter(filter);
    InstanceList list = executor.executeWithReattempt(listBuilder, buildProp);
    return list.getItems();
  }
}
