package com.zylitics.wzgp.resource.grid;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.util.Assert;

import com.google.api.client.util.Strings;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.ServiceAccount;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.CompletedOperation;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.service.ComputeService;
import com.zylitics.wzgp.resource.util.ResourceUtil;

public class GridStarter {
  
  private final ResourceExecutor executor;
  private final ComputeService computeSrv;
  private final BuildProperty buildProp;
  private final GridProperty gridProp;
  private final Instance gridInstance;

  public GridStarter(ResourceExecutor executor
      , ComputeService computeSrv
      , BuildProperty buildProp
      , GridProperty gridProp
      , Instance gridInstance) {
    this.executor = executor;
    this.buildProp = buildProp;
    this.gridProp = gridProp;
    this.computeSrv = computeSrv;
    Assert.notNull(gridInstance, "'gridInstance' can't be null.");
    Assert.hasText(gridInstance.getName(), "'gridInstance' name is missing, object seems invalid.");
    this.gridInstance = gridInstance;
  }
  
  public CompletedOperation start() throws Exception {
    if (!gridInstance.getStatus().equals("TERMINATED")) {
      // shouldn't happen but still check.
      throw new RuntimeException(
          String.format("The given grid instance: %s, isn't in terminated state. Can't proceed. %s"
          , gridInstance.toPrettyString()
          , addToException()));
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
        executor.blockUntilComplete(optOperation.get(), buildProp);
      }
    }
    
    // All updated, now start grid.
    Operation start = startInstanceHandler();
    return new CompletedOperation(executor.blockUntilComplete(start, buildProp));
  }
  
  private Operation startInstanceHandler() throws Exception {
    return computeSrv.startInstance(gridInstance.getName(), gridInstance.getZone(), buildProp);
  }
  
  private Optional<Operation> machineTypeUpdateHandler() throws Exception {
    if (Strings.isNullOrEmpty(gridProp.getMachineType())) {
      return Optional.empty();
    }
    
    if (Strings.isNullOrEmpty(gridInstance.getMachineType())) {
      // shouldn't happen but still check.
      throw new RuntimeException(
          String.format("Grid instance doesn't have a machine type, grid instance: %s %s"
          , gridInstance.toPrettyString()
          , addToException()));
    }
    
    if (!gridProp.getMachineType().equals(
        ResourceUtil.getResourceNameFromUrl(gridInstance.getMachineType()))) {
      return Optional.ofNullable(computeSrv.setMachineType(
          gridInstance.getName()
          , gridProp.getMachineType()
          , gridInstance.getZone()
          , buildProp));
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
          , addToException()));
    }
    
    ServiceAccount existingServAcc = gridInstance.getServiceAccounts().get(0);
    if (!gridProp.getServiceAccount().equals(existingServAcc.getEmail())) {
      return Optional.ofNullable(computeSrv.setServiceAccount(
          gridInstance.getName()
          , gridProp.getServiceAccount()
          , gridInstance.getZone()
          , buildProp));
    }
    return Optional.empty();
  }
  
  private Optional<Operation> customLabelsUpdateHandler() throws Exception {
    if (gridProp.getCustomLabels() == null || gridProp.getCustomLabels().size() == 0) {
      return Optional.empty();
    }
    
    return Optional.ofNullable(computeSrv.setLabels(
        gridInstance.getName()
        , gridProp.getCustomLabels()
        , gridInstance.getZone()
        , buildProp));
  }
  
  private Optional<Operation> metadataUpdateHandler() throws Exception {
    if (gridProp.getMetadata() == null || gridProp.getMetadata().size() == 0) {
      return Optional.empty();
    }
    
    return Optional.ofNullable(computeSrv.setMetadata(
        gridInstance.getName()
        , gridProp.getMetadata()
        , gridInstance.getZone()
        , buildProp));
  }
  
  private String addToException() {
    StringBuilder sb = new StringBuilder();
    if (buildProp != null) {
      sb.append(buildProp.toString());
    }
    return sb.toString();
  }
}
