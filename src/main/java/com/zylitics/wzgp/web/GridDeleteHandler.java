package com.zylitics.wzgp.web;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.assertj.core.util.Strings;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.google.common.collect.ImmutableMap;
import com.zylitics.wzgp.http.ResponseGridDelete;
import com.zylitics.wzgp.http.ResponseStatus;
import com.zylitics.wzgp.resource.SharedDependencies;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.util.ComputeCalls;
import com.zylitics.wzgp.resource.util.ResourceUtil;
import com.zylitics.wzgp.web.exceptions.GridNotDeletedException;
import com.zylitics.wzgp.web.exceptions.GridNotStoppedException;

public class GridDeleteHandler extends AbstractGridHandler {

  private final String gridName;
  private final ResourceExecutor executor;
  private final ComputeCalls computeCalls;
  
  private @Nullable String sessionId;
  private boolean noRush;
  
  public GridDeleteHandler(SharedDependencies sharedDep, String gridName) {
    super(sharedDep);
    
    this.gridName = gridName;
    
    executor = getExecutor();
    computeCalls = getComputeCalls();
  }
  
  @Override
  public ResponseEntity<ResponseGridDelete> handle() throws Exception {
    // first set sessionId if available to ResourceUtil.METADATA_CURRENT_TEST_SESSIONID
    List<Operation> pendingOperations = new ArrayList<>(5);
    if (!Strings.isNullOrEmpty(sessionId)) {
      pendingOperations.add(computeCalls.setMetadata(gridName
          , ImmutableMap.of(ResourceUtil.METADATA_CURRENT_TEST_SESSIONID, sessionId)));
    }
    // get the grid instance to check a few things,
    Instance gridInstance = computeCalls.getInstance(gridName, sharedDep.zone());
    
    // could be true if an ongoing deployment is running that applied this label to indicate we
    // should delete the instance.
    boolean labelIsDeletingTrue =
        Boolean.parseBoolean(gridInstance.getLabels().get(ResourceUtil.LABEL_IS_DELETING));
    
    if (noRush || labelIsDeletingTrue) {
      return delete(pendingOperations, labelIsDeletingTrue);
    }
    
    return stop(pendingOperations);
  }
  
  public void setSessionId(String sessionId) {
    Assert.hasText(sessionId, "sessionId can't be empty.");
    
    this.sessionId = sessionId;
  }
  
  public void setNoRush(boolean noRush) {
    this.noRush = noRush;
  }
  
  private ResponseEntity<ResponseGridDelete> delete(
      List<Operation> pendingOperations
      , boolean labelIsDeletingTrue) throws Exception {
    
    if (!labelIsDeletingTrue) {
      // indicate we're going to delete it.
      pendingOperations.add(computeCalls.setLabels(gridName
          , ImmutableMap.of(ResourceUtil.LABEL_IS_DELETING, "true")));
    }
    waitForPendingOperations(pendingOperations);
    Operation operation = computeCalls.deleteInstance(gridName);
    operation = executor.blockUntilComplete(operation);
    if (!ResourceUtil.isOperationSuccess(operation)) {
      throw new GridNotDeletedException(
          String.format("Couldn't delete grid instance %s, operation: %s"
              , gridName
              , operation.toPrettyString()));
    }
    
    ResponseGridDelete response = prepareResponse();
    return ResponseEntity
        .status(response.getHttpErrorStatusCode())
        .body(response);
  }
  
  private ResponseEntity<ResponseGridDelete> stop(
      List<Operation> pendingOperations) throws Exception {
    waitForPendingOperations(pendingOperations);
    Operation operation = computeCalls.stopInstance(gridName);
    operation = executor.blockUntilComplete(operation);
    if (!ResourceUtil.isOperationSuccess(operation)) {
      throw new GridNotStoppedException(
          String.format("Couldn't stop grid instance %s, operation: %s"
              , gridName
              , operation.toPrettyString()));
    }
    
    ResponseGridDelete response = prepareResponse();
    return ResponseEntity
        .status(response.getHttpErrorStatusCode())
        .body(response);
  }
  
  private void waitForPendingOperations(List<Operation> pendingOperations) throws Exception {
    for (Operation operation : pendingOperations) {
      executor.blockUntilComplete(operation);
    }
  }
  
  private ResponseGridDelete prepareResponse() {
    ResponseGridDelete response = new ResponseGridDelete();
    response.setHttpErrorStatusCode(HttpStatus.OK.value());
    response.setStatus(ResponseStatus.SUCCESS.name());
    response.setZone(sharedDep.zone());
    return response;
  }
  
  private ResourceExecutor getExecutor() {
    return ResourceExecutor.Factory.getDefault().create(sharedDep);
  }
  
  private ComputeCalls getComputeCalls() {
    return new ComputeCalls(sharedDep, executor);
  }
}
