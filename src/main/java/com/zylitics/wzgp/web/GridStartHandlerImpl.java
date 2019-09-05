package com.zylitics.wzgp.web;

import static com.zylitics.wzgp.resource.util.ResourceUtil.nameFromUrl;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.google.common.base.Strings;
import com.zylitics.wzgp.http.RequestGridCreate;
import com.zylitics.wzgp.http.ResponseGridCreate;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.CompletedOperation;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.grid.GridStarter;
import com.zylitics.wzgp.resource.search.ResourceSearch;
import com.zylitics.wzgp.resource.service.ComputeService;
import com.zylitics.wzgp.resource.util.ResourceUtil;
import com.zylitics.wzgp.web.exceptions.GridBeingDeletedFromOutsideException;
import com.zylitics.wzgp.web.exceptions.GridNotRunningException;
import com.zylitics.wzgp.web.exceptions.GridNotStartedException;
import com.zylitics.wzgp.web.exceptions.GridOccupiedByOtherException;
import com.zylitics.wzgp.web.exceptions.GridStartHandlerFailureException;

public class GridStartHandlerImpl extends AbstractGridCreateHandler implements GridStartHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GridStartHandlerImpl.class);

  // APICoreProperties is currently redundant for this handler but keeping it for later use.
  private GridStartHandlerImpl(APICoreProperties apiCoreProps
      , ResourceExecutor executor
      , ComputeService computeSrv
      , String zone
      , RequestGridCreate request) {
    super(apiCoreProps, executor, computeSrv, zone, request);
  }
  
  @Override
  public ResponseEntity<ResponseGridCreate> handle() throws Exception {
    Instance gridInstance = searchStoppedInstance();
    onStoppedGridFoundEventHandler(gridInstance);  // lock instance among other things.
    
    return startGrid(gridInstance);
  }
  
  private Instance searchStoppedInstance() throws Exception {
    ResourceSearch search = getSearch();
    Optional<Instance> instance = search.searchStoppedInstance(zone);
    if (!instance.isPresent()) {
      LOG.warn("No stopped instance found that matches the given search terms, search terms: {} {}"
          , request.getResourceSearchParams().toString()
          , addToException());
      throw new GridStartHandlerFailureException();  // give up
    }
    
    return instance.get();
  }
  
  private void onStoppedGridFoundEventHandler(Instance gridInstance) throws Exception {
    // LOG the searched instance against build info so that if any error occurs during startup
    // even if we don't know what instance was found against the build seeing the logs, there is
    // this information available.
    LOG.info("Build {} found stopped instance {} and going to start it", addToException()
        , gridInstance.toPrettyString());
    // label buildId to the created grid instance to lock it.
    lockGridInstance(gridInstance);
  }
  
  private ResponseEntity<ResponseGridCreate> startGrid(Instance gridInstance) throws Exception {
    GridStarter starter = new GridStarter(executor
        , computeSrv
        , buildProp
        , request.getGridProperties()
        , gridInstance);
    CompletedOperation completedOperation = starter.start();
    
    Operation operation = completedOperation.get();
    if (!ResourceUtil.isOperationSuccess(operation)) {
      LOG.error("Couldn't start stopped grid instance {}, operation: {} {}"
          , gridInstance.toPrettyString()
          , operation.toPrettyString()
          , addToException());
      throw new GridStartHandlerFailureException(
          "Couldn't start stopped grid instance", new GridNotStartedException());  // give up
    }
    
    onGridStartEventHandler(gridInstance);
    
    ResponseGridCreate response = prepareResponse(gridInstance, HttpStatus.OK);
    return ResponseEntity
        .status(response.getHttpStatusCode())
        .body(response);
  }
  
  private void onGridStartEventHandler(Instance gridInstance) throws Exception {
    // ==========verify that we own this instance, get the current state of instance
    
    gridInstance = computeSrv.getInstance(gridInstance.getName()
        , nameFromUrl(gridInstance.getZone())
        , buildProp);
    // get the current value of lock-by-build label
    String labelLockedByBuild = gridInstance.getLabels().get(ResourceUtil.LABEL_LOCKED_BY_BUILD);
    // see whether it holds our build.
    if (Strings.isNullOrEmpty(labelLockedByBuild)
        || !labelLockedByBuild.equals(buildProp.getBuildId())) {
      // signifies that some other request took our searched grid instance.
      // we'll not re-attempt finding another stopped instance for now and will just give up.
      LOG.error("Looks like some other request took out our instance while it was starting up"
          + ", forcing us to leave it out and give up. instance: {} {}"
          , gridInstance.toPrettyString()
          , addToException());
      throw new GridStartHandlerFailureException(
          "Looks like some other request took out our instance while it was starting up"
          , new GridOccupiedByOtherException());  // give up
    }
    
    // ==========verify that an ongoing deployment is not going to delete it
    if (Boolean.parseBoolean(gridInstance.getLabels().get(ResourceUtil.LABEL_IS_DELETING))) {
      // signifies that an ongoing deployment is running. This instance may or may not delete
      // depending on when deployment script marked it for deletion, if it marked after it started,
      // it won't delete but if it marked it before that (while in terminated state) it may delete
      // soon, safer way is to get another fresh instance.
      // If this error is found in logs, such instance should be manually deleted for now. Not
      // reverting the LABEL_LOCKED_BY_BUILD label for now.
      LOG.error("Instance started from stopped state, but is-deleting label is found true, rather"
          + ", than taking a chance with the ongoing deployment, leaving it out. instance: {} {}"
          , gridInstance.toPrettyString()
          , addToException());
      throw new GridStartHandlerFailureException(
          "Instance started from stopped state, but is-deleting label is found true"
          , new GridBeingDeletedFromOutsideException());  // give up
    }
    
    // verify the grid is running and there's nothing wrong
    if (!isRunning(gridInstance)) {
      // shouldn't happen
      LOG.error(
          String.format("Grid instance found not running after start completed. grid instance %s %s"
          , gridInstance.toPrettyString()
          , addToException()));
      throw new GridStartHandlerFailureException(
          "Grid instance found not running after start completed.", new GridNotRunningException());
    }
  }
  
  public static class Factory implements GridStartHandler.Factory {
    
    @Override
    public GridStartHandler create(APICoreProperties apiCoreProps, ResourceExecutor executor,
        ComputeService computeSrv, String zone, RequestGridCreate request) {
      return new GridStartHandlerImpl(apiCoreProps, executor, computeSrv, zone, request);
    }
  }
}