package com.zylitics.wzgp.web;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.zylitics.wzgp.http.ResponseGridDelete;
import com.zylitics.wzgp.http.ResponseStatus;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.service.ComputeService;
import com.zylitics.wzgp.resource.util.ResourceUtil;
import com.zylitics.wzgp.web.exceptions.GridNotDeletedException;
import com.zylitics.wzgp.web.exceptions.GridNotFoundException;
import com.zylitics.wzgp.web.exceptions.GridNotStoppedException;

public class GridDeleteHandlerImpl extends AbstractGridHandler implements GridDeleteHandler {

  private final String gridName;
  
  private @Nullable String sessionId;
  private boolean noRush;
  
  private GridDeleteHandlerImpl(APICoreProperties apiCoreProps
      , ResourceExecutor executor
      , ComputeService computeSrv
      , String zone
      , String gridName) {
    super(apiCoreProps, executor, computeSrv, zone);
    
    this.gridName = gridName;
  }
  
  @Override
  public ResponseEntity<ResponseGridDelete> handle() throws Exception {
    // first set sessionId if available to ResourceUtil.METADATA_CURRENT_TEST_SESSIONID
    List<Operation> pendingOperations = new ArrayList<>(5);
    if (!Strings.isNullOrEmpty(sessionId)) {
      pendingOperations.add(
          computeSrv.setMetadata(gridName
              , ImmutableMap.of(ResourceUtil.METADATA_CURRENT_TEST_SESSIONID, sessionId)
              , zone
              , null));
    }
    // get the grid instance to check a few things,
    Instance gridInstance = computeSrv.getInstance(gridName, zone, null);
    
    if (gridInstance == null) {
      throw new GridNotFoundException("Grid instance wasn't found by name " + gridName + " deletion"
          + " is failed");
    }
    
    // could be true if an ongoing deployment is running that applied this label to indicate we
    // should delete the instance.
    boolean labelIsDeletingTrue =
        Boolean.parseBoolean(gridInstance.getLabels().get(ResourceUtil.LABEL_IS_DELETING));
    
    if (noRush || labelIsDeletingTrue) {
      return delete(pendingOperations, labelIsDeletingTrue);
    }
    
    return stop(pendingOperations);
  }
  
  @Override
  public void setSessionId(String sessionId) {
    Assert.hasText(sessionId, "sessionId can't be empty.");
    
    this.sessionId = sessionId;
  }
  
  @Override
  public void setNoRush(boolean noRush) {
    this.noRush = noRush;
  }
  
  private ResponseEntity<ResponseGridDelete> delete(
      List<Operation> pendingOperations
      , boolean labelIsDeletingTrue) throws Exception {
    
    if (!labelIsDeletingTrue) {
      // adding this label indicates we're going to delete it.
      pendingOperations.add(
          computeSrv.setLabels(gridName
          , ImmutableMap.of(ResourceUtil.LABEL_IS_DELETING, "true")
          , zone
          , null));
    }
    waitForPendingOperations(pendingOperations);  // let finish before starting delete
    Operation operation = computeSrv.deleteInstance(gridName, zone, null);
    operation = executor.blockUntilComplete(operation, null);
    if (!ResourceUtil.isOperationSuccess(operation)) {
      throw new GridNotDeletedException(
          String.format("Couldn't delete grid instance %s, operation: %s"
              , gridName
              , operation.toPrettyString()));
    }
    
    ResponseGridDelete response = prepareResponse();
    return ResponseEntity
        .status(response.getHttpStatusCode())
        .body(response);
  }
  
  private ResponseEntity<ResponseGridDelete> stop(
      List<Operation> pendingOperations) throws Exception {
    waitForPendingOperations(pendingOperations);  // let finish before starting stop
    Operation operation = computeSrv.stopInstance(gridName, zone, null);
    operation = executor.blockUntilComplete(operation, null);
    if (!ResourceUtil.isOperationSuccess(operation)) {
      throw new GridNotStoppedException(
          String.format("Couldn't stop grid instance %s, operation: %s"
              , gridName
              , operation.toPrettyString()));
    }
    
    ResponseGridDelete response = prepareResponse();
    return ResponseEntity
        .status(response.getHttpStatusCode())
        .body(response);
  }
  
  private void waitForPendingOperations(List<Operation> pendingOperations) throws Exception {
    for (Operation operation : pendingOperations) {
      executor.blockUntilComplete(operation, null);
    }
  }
  
  private ResponseGridDelete prepareResponse() {
    ResponseGridDelete response = new ResponseGridDelete();
    response.setHttpStatusCode(HttpStatus.OK.value());
    response.setStatus(ResponseStatus.SUCCESS.name());
    response.setZone(zone);
    return response;
  }
  
  public static class Factory implements GridDeleteHandler.Factory {
    
    @Override
    public GridDeleteHandler create(APICoreProperties apiCoreProps, ResourceExecutor executor,
        ComputeService computeSrv, String zone, String gridName) {
      return new GridDeleteHandlerImpl(apiCoreProps, executor, computeSrv, zone, gridName);
    }
  }
}
