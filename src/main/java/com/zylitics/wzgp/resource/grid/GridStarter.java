package com.zylitics.wzgp.resource.grid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.util.Assert;

import com.google.api.client.util.Strings;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.ServiceAccount;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.CompletedOperation;
import com.zylitics.wzgp.resource.SharedDependencies;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.util.ComputeCalls;
import com.zylitics.wzgp.resource.util.ResourceUtil;

public class GridStarter extends AbstractGrid {
  
  private final Instance gridInstance;
  private final ComputeCalls computeCalls;
  
  private final String addToException;

  public GridStarter(SharedDependencies sharedDep
      , BuildProperty buildProp
      , GridProperty gridProp
      , Instance gridInstance
      , ResourceExecutor executor
      , ComputeCalls computeCalls) {
    super(sharedDep, gridProp, executor);
    
    Assert.notNull(gridInstance, "grid instance can't be null.");
    Assert.hasText(gridInstance.getName(), "grid instance name is missing, object seems invalid.");
    this.gridInstance = gridInstance;
    this.computeCalls = computeCalls;
    
    addToException = buildAddToException(buildProp);
  }
  
  public CompletedOperation start() throws Exception {
    if (!gridInstance.getStatus().equals("TERMINATED")) {
      throw new RuntimeException(
          String.format("The given grid instance: %s, isn't in terminated state. Can't proceed. %s"
          , gridInstance.toPrettyString()
          , addToException));
    }
    // Before starting the grid, we should update the requested properties of it.
    List<Optional<Operation>> updateOperations = new ArrayList<>(10);
    updateOperations.add(machineTypeUpdateHandler());
    updateOperations.add(serviceAccountUpdateHandler());
    updateOperations.add(customLabelsUpdateHandler());
    updateOperations.add(metadataUpdateHandler());
    // We've started all the updates at ones sequentially, they will most likely complete near
    // together, but we'll verify completion of all of them before beginning start.
    for (Optional<Operation> optOperation : updateOperations) {
      if (optOperation.isPresent()) {
        executor.blockUntilComplete(optOperation.get());
      }
    }
    
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
          , addToException));
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
          , addToException));
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
  
  private String buildAddToException(BuildProperty buildProp) {
    StringBuilder sb = new StringBuilder();
    if (buildProp != null) {
      sb.append(buildProp.toString());
    }
    return sb.toString();
  }
}
