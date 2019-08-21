package com.zylitics.wzgp.resource.grid;

import java.util.Map;
import java.util.Optional;

import org.springframework.util.Assert;

import com.google.api.client.util.Strings;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.ServiceAccount;
import com.zylitics.wzgp.config.SharedDependencies;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.CompletedOperation;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.util.ComputeCalls;
import com.zylitics.wzgp.resource.util.ResourceUtil;

public class GridStarter extends AbstractGrid {
  
  private final Instance gridInstance;
  private final BuildProperty buildProp;
  private final ComputeCalls computeCalls;

  public GridStarter(SharedDependencies sharedDep
      , BuildProperty buildProp
      , GridProperty gridProp
      , Instance gridInstance
      , ResourceExecutor executor
      , ComputeCalls computeCalls) {
    super(sharedDep, gridProp, executor);
    
    this.buildProp = buildProp;
    Assert.notNull(gridInstance, "grid instance can't be null.");
    Assert.hasText(gridInstance.getName(), "grid instance name is missing, object seems invalid.");
    this.gridInstance = gridInstance;
    this.computeCalls = computeCalls;
  }
  
  public CompletedOperation start() throws Exception {
    if (!gridInstance.getStatus().equals("TERMINATED")) {
      throw new RuntimeException(
          String.format("The given grid instance: %s, isn't in terminated state. Can't proceed. %s"
          , gridInstance.toPrettyString()
          , buildProp.toString()));
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
    return computeCalls.startInstance(gridInstance.getName());
  }
  
  private Optional<Operation> machineTypeUpdateHandler() throws Exception {
    if (Strings.isNullOrEmpty(gridProp.getMachineType())) {
      return Optional.empty();
    }
    
    if (Strings.isNullOrEmpty(gridInstance.getMachineType())) {
      throw new RuntimeException(
          String.format("Grid instance doesn't have a machine type, grid instance: %s %s"
          , gridInstance.toPrettyString()
          , buildProp.toString()));
    }
    
    if (!gridProp.getMachineType().equals(
        ResourceUtil.getResourceNameFromUrl(gridInstance.getMachineType()))) {
      return Optional.ofNullable(computeCalls.setMachineType(
          gridInstance.getName()
          , gridProp.getMachineType()));
    }
    return Optional.empty();
  }
  
  private Optional<Operation> serviceAccountUpdateHandler() throws Exception {
    if (Strings.isNullOrEmpty(gridProp.getServiceAccount())) {
      return Optional.empty();
    }
    
    if (gridInstance.getServiceAccounts() == null
        || gridInstance.getServiceAccounts().size() == 0) {
      throw new RuntimeException(
          String.format("Grid instance doesn't have a service account, grid instance: %s %s"
          , gridInstance.toPrettyString()
          , buildProp.toString()));
    }
    
    ServiceAccount existingServAcc = gridInstance.getServiceAccounts().get(0);
    if (!gridProp.getServiceAccount().equals(existingServAcc.getEmail())) {
      return Optional.ofNullable(computeCalls.setServiceAccount(
          gridInstance.getName()
          , gridProp.getServiceAccount()));
    }
    return Optional.empty();
  }
  
  private Optional<Operation> customLabelsUpdateHandler() throws Exception {
    if (gridProp.getCustomLabels() == null || gridProp.getCustomLabels().size() == 0) {
      return Optional.empty();
    }
    
    return Optional.ofNullable(computeCalls.setLabels(
        gridInstance.getName()
        , gridProp.getCustomLabels()));
  }
  
  private Optional<Operation> metadataUpdateHandler() throws Exception {
    Map<String, String> mergedMetadata = mergedMetadata();
    if (mergedMetadata == null || mergedMetadata.size() == 0) {
      return Optional.empty();
    }
    
    return Optional.ofNullable(computeCalls.setMetadata(
        gridInstance.getName()
        , mergedMetadata));
  }
}
