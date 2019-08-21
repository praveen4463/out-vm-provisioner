package com.zylitics.wzgp.resource.grid;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.springframework.util.Assert;

import com.google.api.client.util.Strings;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstancesSetLabelsRequest;
import com.google.api.services.compute.model.InstancesSetMachineTypeRequest;
import com.google.api.services.compute.model.InstancesSetServiceAccountRequest;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.ServiceAccount;
import com.zylitics.wzgp.config.SharedDependencies;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.CompletedOperation;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;

public class GridStarter extends AbstractGrid {
  
  private final Instance gridInstance;
  private final BuildProperty buildProp;

  public GridStarter(SharedDependencies sharedDep
      , BuildProperty buildProp
      , GridProperty gridProp
      , Instance gridInstance
      , ResourceExecutor executor) {
    super(sharedDep, gridProp, executor);
    
    this.buildProp = buildProp;
    Assert.notNull(gridInstance, "grid instance can't be null.");
    Assert.hasText(gridInstance.getName(), "grid instance name is missing, object seems invalid.");
    this.gridInstance = gridInstance;
  }
  
  public CompletedOperation start() throws Exception {
    if (!gridInstance.getStatus().equals("TERMINATED")) {
      throw new RuntimeException("The given grid instance " + gridInstance.toPrettyString()
          + " isn't in terminated state. Can't proceed. " + addToException(buildProp));
    }
    // Before starting the grid, we should update the requested properties of it.
    Optional<Operation> machineType = machineTypeUpdateHandler();
    Optional<Operation> serviceAccount = serviceAccountUpdateHandler();
    Optional<Operation> customLabels = customLabelsUpdateHandler();
    Optional<Operation> metadata = metadataUpdateHandler();
    // We've started all the updates at ones sequentially, they will most likely complete near
    // together, but we'll verify completion of all of them individually before beginning start.
    
    if (machineType.isPresent()) executor.blockUntilComplete(machineType.get());
    if (serviceAccount.isPresent()) executor.blockUntilComplete(serviceAccount.get());
    if (customLabels.isPresent()) executor.blockUntilComplete(customLabels.get());
    if (metadata.isPresent()) executor.blockUntilComplete(metadata.get());
    
    // All updated, now start grid.
    Operation start = startInstanceHandler();
    return new CompletedOperation(executor.blockUntilComplete(start));
  }
  
  private Operation startInstanceHandler() throws Exception {
    Compute.Instances.Start startInstance =
        sharedDep.compute().instances().start(sharedDep.apiCoreProps().getProjectId()
        , sharedDep.zone()
        , gridInstance.getName());
    return executor.executeWithReattempt(startInstance);
  }
  
  private Optional<Operation> machineTypeUpdateHandler() throws Exception {
    if (Strings.isNullOrEmpty(gridProp.getMachineType())) {
      return Optional.empty();
    }
    
    if (Strings.isNullOrEmpty(gridInstance.getMachineType())) {
      throw new RuntimeException("Grid instance doesn't have a machine type, grid instance: "
          + gridInstance.toPrettyString() + addToException(buildProp));
    }
    
    if (!gridProp.getMachineType().equals(getResourceNameFromUrl(gridInstance.getMachineType()))) {
      InstancesSetMachineTypeRequest machineTypeReq = new InstancesSetMachineTypeRequest();
      machineTypeReq.setMachineType(String.format("zones/%s/machineTypes/%s"
          , sharedDep.zone(), gridProp.getMachineType()));
      Compute.Instances.SetMachineType setMachineType =
          sharedDep.compute().instances().setMachineType(sharedDep.apiCoreProps().getProjectId()
              , sharedDep.zone()
              , gridInstance.getName()
              , machineTypeReq);
      return Optional.ofNullable(executor.executeWithReattempt(setMachineType));
    }
    return Optional.empty();
  }
  
  private Optional<Operation> serviceAccountUpdateHandler() throws Exception {
    if (Strings.isNullOrEmpty(gridProp.getServiceAccount())) {
      return Optional.empty();
    }
    
    if (gridInstance.getServiceAccounts() == null
        || gridInstance.getServiceAccounts().size() == 0) {
      throw new RuntimeException("Grid instance doesn't have a service account, grid instance: "
          + gridInstance.toPrettyString() + addToException(buildProp));
    }
    
    ServiceAccount existingServAcc = gridInstance.getServiceAccounts().get(0);
    if (!gridProp.getServiceAccount().equals(existingServAcc.getEmail())) {
      InstancesSetServiceAccountRequest servAccReq = new InstancesSetServiceAccountRequest();
      servAccReq.setEmail(gridProp.getServiceAccount());
      servAccReq.setScopes(Collections.singletonList(ComputeScopes.CLOUD_PLATFORM));
      Compute.Instances.SetServiceAccount setServAcc =
          sharedDep.compute().instances().setServiceAccount(sharedDep.apiCoreProps().getProjectId()
              , sharedDep.zone()
              , gridInstance.getName()
              , servAccReq);
      return Optional.ofNullable(executor.executeWithReattempt(setServAcc));
    }
    return Optional.empty();
  }
  
  private Optional<Operation> customLabelsUpdateHandler() throws Exception {
    if (gridProp.getCustomLabels() == null || gridProp.getCustomLabels().size() == 0) {
      return Optional.empty();
    }
    
    InstancesSetLabelsRequest labelReq = new InstancesSetLabelsRequest();
    labelReq.setLabels(gridProp.getCustomLabels());
    Compute.Instances.SetLabels setLabels =
        sharedDep.compute().instances().setLabels(sharedDep.apiCoreProps().getProjectId()
            , sharedDep.zone()
            , gridInstance.getName()
            , labelReq);
    return Optional.ofNullable(executor.executeWithReattempt(setLabels));
  }
  
  private Optional<Operation> metadataUpdateHandler() throws Exception {
    Map<String, String> mergedMetadata = mergedMetadata();
    if (mergedMetadata == null || mergedMetadata.size() == 0) {
      return Optional.empty();
    }
    
    Metadata metadata = new Metadata();
    mergedMetadata.entrySet()
        .forEach(entry -> metadata.set(entry.getKey(), entry.getValue()));
    Compute.Instances.SetMetadata setMetadata =
        sharedDep.compute().instances().setMetadata(sharedDep.apiCoreProps().getProjectId()
            , sharedDep.zone()
            , gridInstance.getName()
            , metadata);
    return Optional.ofNullable(executor.executeWithReattempt(setMetadata));
  }
}
