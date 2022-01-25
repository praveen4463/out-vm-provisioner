package com.zylitics.wzgp.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.google.common.collect.ImmutableMap;
import com.zylitics.wzgp.http.ResponseGridDelete;
import com.zylitics.wzgp.http.ResponseStatus;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.compute.ComputeService;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.util.ResourceUtil;
import com.zylitics.wzgp.web.exceptions.GridNotDeletedException;
import com.zylitics.wzgp.web.exceptions.GridNotFoundException;
import com.zylitics.wzgp.web.exceptions.GridNotStoppedException;

public class GridDeleteHandlerImpl extends AbstractGridHandler implements GridDeleteHandler {

  private final String gridName;
  
  private boolean noRush;
  
  private boolean requireRunningVM;
  
  private Instance gridInstance;
  
  private GridDeleteHandlerImpl(APICoreProperties apiCoreProps
      , ResourceExecutor executor
      , ComputeService computeSrv
      , FingerprintBasedUpdater fingerprintBasedUpdater
      , String zone
      , String gridName) {
    super(apiCoreProps, executor, computeSrv, fingerprintBasedUpdater, zone);
    
    this.gridName = gridName;
  }
  
  @Override
  public ResponseEntity<ResponseGridDelete> handle() throws Exception {
    gridInstance = computeSrv.getInstance(gridName, zone, null);
    if (gridInstance == null) {
      throw new GridNotFoundException("Grid instance wasn't found by name " + gridName + " deletion"
          + " is failed");
    }
    // could be true if an ongoing deployment is running that applied this label to indicate we
    // should delete the instance.
    boolean labelIsDeletingTrue =
        Boolean.parseBoolean(gridInstance.getLabels().get(ResourceUtil.LABEL_IS_DELETING));
    
    if (noRush || labelIsDeletingTrue || !requireRunningVM) {
      return delete(labelIsDeletingTrue);
    }
  
    // if we're not deleting, first unlock this instance, don't wait for completion.
    fingerprintBasedUpdater.updateLabelsGivenFreshlyFetchedInstance(gridInstance,
        ImmutableMap.of(ResourceUtil.LABEL_LOCKED_BY_BUILD, "none"),
        null);
  
    return sendResponse();
  }
  
  @Override
  public void setSessionId(String sessionId) {
    // not being used
  }
  
  @Override
  public void setNoRush(boolean noRush) {
    this.noRush = noRush;
  }
  
  @Override
  public void setRequireRunningVM(boolean requireRunningVM) {
    this.requireRunningVM = requireRunningVM;
  }
  
  private ResponseEntity<ResponseGridDelete> delete(boolean labelIsDeletingTrue) throws Exception {
    
    if (!labelIsDeletingTrue) {
      // adding this label indicates we're going to delete it.
      Operation op = fingerprintBasedUpdater.updateLabelsGivenFreshlyFetchedInstance(gridInstance,
          ImmutableMap.of(ResourceUtil.LABEL_IS_DELETING, "true"),
          null);
      // wait because we don't want any other request to find this instance while it's being deleted
      executor.blockUntilComplete(op, 500, 10000, null);
    }
    Operation operation = computeSrv.deleteInstance(gridName, zone, null);
    operation = executor.blockUntilComplete(operation, 1000, 300 * 1000, null);
    if (!ResourceUtil.isOperationSuccess(operation)) {
      throw new GridNotDeletedException(
          String.format("Couldn't delete grid instance %s, operation: %s"
              , gridName
              , operation.toPrettyString()));
    }
  
    return sendResponse();
  }
  
  @SuppressWarnings("unused")
  private ResponseEntity<ResponseGridDelete> stop() throws Exception {
    Operation operation = computeSrv.stopInstance(gridName, zone, null);
    operation = executor.blockUntilComplete(operation, 1000, 300 * 1000, null);
    if (!ResourceUtil.isOperationSuccess(operation)) {
      throw new GridNotStoppedException(
          String.format("Couldn't stop grid instance %s, operation: %s"
              , gridName
              , operation.toPrettyString()));
    }
    
    return sendResponse();
  }
  
  private ResponseEntity<ResponseGridDelete> sendResponse() {
    ResponseGridDelete response = prepareResponse();
    return ResponseEntity
        .status(response.getHttpStatusCode())
        .body(response);
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
    public GridDeleteHandler create(APICoreProperties apiCoreProps, ResourceExecutor executor
        , ComputeService computeSrv, FingerprintBasedUpdater fingerprintBasedUpdater, String zone
        , String gridName) {
      return new GridDeleteHandlerImpl(apiCoreProps, executor, computeSrv, fingerprintBasedUpdater
          , zone, gridName);
    }
  }
}
