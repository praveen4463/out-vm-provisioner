package com.zylitics.wzgp.web;

import java.util.Optional;

import org.assertj.core.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.zylitics.wzgp.config.SharedDependencies;
import com.zylitics.wzgp.http.RequestGridCreate;
import com.zylitics.wzgp.http.ResponseGridCreate;
import com.zylitics.wzgp.resource.CompletedOperation;
import com.zylitics.wzgp.resource.grid.GridStarter;
import com.zylitics.wzgp.resource.search.ResourceSearch;
import com.zylitics.wzgp.resource.util.ResourceUtil;

public class GridStartHandler extends AbstractGridCreateHandler {

private static final Logger LOG = LoggerFactory.getLogger(GridStartHandler.class);

  public GridStartHandler(SharedDependencies sharedDep
      , RequestGridCreate request) {
    super(sharedDep, request);
  }
  
  @Override
  public ResponseEntity<ResponseGridCreate> handle() throws Exception {
    Instance gridInstance = searchStoppedInstance();
    onStoppedGridFoundEventHandler(gridInstance);  // lock instance among other things.
    
    return startGrid(gridInstance);
  }
  
  private Instance searchStoppedInstance() throws Exception {
    ResourceSearch search = getSearch();
    Optional<Instance> instance = search.searchStoppedInstance();
    if (!instance.isPresent()) {
      LOG.warn("No stopped instance found that matches the given search terms, search terms: {} {}"
          , request.getResourceSearchParams().toString()
          , buildProp.toString());
      throw new GridStartHandlerFailureException();  // give up
    }
    return instance.get();
  }
  
  private void onStoppedGridFoundEventHandler(Instance gridInstance) throws Exception {
    // label buildId to the created grid instance to lock it.
    lockGridInstance(gridInstance.getName());
  }
  
  private ResponseEntity<ResponseGridCreate> startGrid(Instance gridInstance) throws Exception {
    GridStarter starter = getGridStarter(gridInstance);
    CompletedOperation completedOperation = starter.start();
    Operation operation = completedOperation.get();
    if (!ResourceUtil.isOperationSuccess(operation))  {
      LOG.error("Couldn't start stopped grid instance {}, operation: {} {}"
          , gridInstance.toPrettyString()
          , operation.toPrettyString()
          , buildProp.toString());
      throw new GridStartHandlerFailureException();  // give up
    }
    
    onGridStartEventHandler(gridInstance);
    
    ResponseGridCreate response = prepareResponse(gridInstance);
    return ResponseEntity
        .status(response.getHttpErrorStatusCode())
        .body(response);
  }
  
  private void onGridStartEventHandler(Instance gridInstance) throws Exception {
    // verify that we own this instance, get the current state of instance
    gridInstance = computeCalls.getInstance(gridInstance.getName(), gridInstance.getZone());
    // get the current value of lock-by-build label
    String labelLockedByBuild = gridInstance.getLabels().get(ResourceUtil.LABEL_LOCKED_BY_BUILD);
    // see whether it holds our build.
    if (Strings.isNullOrEmpty(labelLockedByBuild)
        || !labelLockedByBuild.equals(buildProp.getBuildId())) {
      // signifies that some other request took our searched grid instance.
      // we'll not re-attempt finding another stopped instance for now and will just give up.
      LOG.error("Looks like some other request took out our instance while it was starting up"
          + ", forcing us to leave it out and give up. instance: {} {}"
          , gridInstance.toPrettyString(), buildProp.toString());
      throw new GridStartHandlerFailureException();  // give up
    }
    
    // verify the grid is running and there's nothing wrong
    if (!isRunning(gridInstance)) {
      // shouldn't happen
      Exception ex = new GridNotRunningException();
      LOG.error(
          String.format("Grid instance found not running after start completed. grid instance %s %s"
          , gridInstance.toPrettyString()
          , buildProp.toString())
          , ex);
      throw ex;  // give up
    }
  }
}
