package com.zylitics.wzgp.resource.grid;

import static com.zylitics.wzgp.resource.util.ResourceUtil.nameFromUrl;

import java.util.*;
import java.util.concurrent.TimeUnit;

import com.zylitics.wzgp.model.InstanceStatus;
import com.zylitics.wzgp.resource.util.ResourceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import com.google.api.client.util.Strings;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.ServiceAccount;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.CompletedOperation;
import com.zylitics.wzgp.resource.compute.ComputeService;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.web.FingerprintBasedUpdater;

public class GridStarter {
  
  private static final Logger LOG = LoggerFactory.getLogger(GridStarter.class);
  
  private final ResourceExecutor executor;
  private final ComputeService computeSrv;
  private final FingerprintBasedUpdater fingerprintBasedUpdater;
  private final BuildProperty buildProp;
  private final GridProperty gridProp;
  private final Instance gridInstance;
  private final String zone;
  
  public GridStarter(ResourceExecutor executor
      , ComputeService computeSrv
      , FingerprintBasedUpdater fingerprintBasedUpdater
      , BuildProperty buildProp
      , GridProperty gridProp
      , Instance gridInstance) {
    this.executor = executor;
    this.computeSrv = computeSrv;
    this.fingerprintBasedUpdater = fingerprintBasedUpdater;
    this.buildProp = buildProp;
    this.gridProp = gridProp;
    Assert.notNull(gridInstance, "'gridInstance' can't be null.");
    Assert.hasText(gridInstance.getName(), "'gridInstance' name is missing, object seems invalid.");
    this.gridInstance = gridInstance;
    zone = nameFromUrl(gridInstance.getZone());
  }
  
  public CompletedOperation start() throws Exception {
    if (!gridInstance.getStatus().equals(InstanceStatus.TERMINATED.toString())) {
      // shouldn't happen but still check.
      throw new RuntimeException(
          String.format("The given grid instance: %s, isn't in terminated state. Can't proceed. %s"
          , gridInstance.toPrettyString()
          , addToException()));
    }
    long start = System.currentTimeMillis();
    // Before starting the grid, we should update the requested properties of it.
    // start update of label and metadata and don't wait as they'll update by the time we're starting
    // instance
    Map<String, String> labelsToUpdate = new HashMap<>();
    labelsToUpdate.put(ResourceUtil.LABEL_LOCKED_BY_BUILD, buildProp.getBuildId());
    if (gridProp.getCustomLabels() != null) {
      labelsToUpdate.putAll(gridProp.getCustomLabels());
    }
    fingerprintBasedUpdater.updateLabelsGivenFreshlyFetchedInstance(
        gridInstance,
        labelsToUpdate,
        buildProp);
    if (gridProp.getMetadata() != null && gridProp.getMetadata().size() > 0) {
      fingerprintBasedUpdater.updateMetadataGivenFreshlyFetchedInstance(
          gridInstance,
          gridProp.getMetadata(),
          buildProp);
    }
    // Wait for the operations that needs to be completed before machine starts
    List<Optional<Operation>> updateOperations = new ArrayList<>(5);
    updateOperations.add(machineTypeUpdateHandler());
    updateOperations.add(serviceAccountUpdateHandler());
    for (Optional<Operation> optOperation : updateOperations) {
      if (optOperation.isPresent()) {
        executor.blockUntilComplete(optOperation.get(), 500, 10000, buildProp);
      }
    }
    LOG.debug("took {}secs waiting for update op before starting instance",
        TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start));
    // start grid.
    start = System.currentTimeMillis();
    Operation startOp = startInstanceHandler();
    startOp = executor.blockUntilComplete(startOp, 1000, 180 * 1000, buildProp);
    LOG.debug("took {}secs waiting for instance to start",
        TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start));
    return new CompletedOperation(startOp);
  }
  
  private Operation startInstanceHandler() throws Exception {
    return computeSrv.startInstance(gridInstance.getName(), zone, buildProp);
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
        nameFromUrl(gridInstance.getMachineType()))) {
      return Optional.ofNullable(computeSrv.setMachineType(
          gridInstance.getName()
          , gridProp.getMachineType()
          , zone
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
          , zone
          , buildProp));
    }
    return Optional.empty();
  }
  
  private String addToException() {
    StringBuilder sb = new StringBuilder();
    if (buildProp != null) {
      sb.append(buildProp);
    }
    return sb.toString();
  }
}
