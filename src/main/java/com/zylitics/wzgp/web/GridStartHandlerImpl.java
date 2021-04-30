package com.zylitics.wzgp.web;

import static com.zylitics.wzgp.resource.util.ResourceUtil.nameFromUrl;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.zylitics.wzgp.model.InstanceStatus;
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
import com.zylitics.wzgp.resource.compute.ComputeService;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.grid.GridStarter;
import com.zylitics.wzgp.resource.search.ResourceSearch;
import com.zylitics.wzgp.resource.util.ResourceUtil;
import com.zylitics.wzgp.web.exceptions.AcquireStoppedMaxReattemptException;
import com.zylitics.wzgp.web.exceptions.GridBeingDeletedFromOutsideException;
import com.zylitics.wzgp.web.exceptions.GridNotRunningException;
import com.zylitics.wzgp.web.exceptions.GridNotStartedException;
import com.zylitics.wzgp.web.exceptions.GridStartHandlerFailureException;

public class GridStartHandlerImpl extends AbstractGridCreateHandler implements GridStartHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GridStartHandlerImpl.class);

  // holds the Instance.Id and BuildId
  /*
   * The rationale behind using a static concurrent map: When several concurrent requests find a
   * same stopped instance, they fight to get a lock on it and may overwrite one other's lock at the
   * same time as its in form of GCP label. There may also be situations where one request
   * overwrites lock done by other request to acquire the instance and another request start making
   * updates to instance assuming that it owns it and we may end up with an instance that is locked
   * by one request but has updates of metadata/label of another request, messing everything badly.
   * 
   * I thought to put a Lock on instance-locking method but that would have delayed requests with
   * distinct searched instances plus lots of GCP api requests by parallel requests to know the
   * current lock-build etc.
   * 
   * This idea of using a ConcurrentHashMap seems the best so far, in this I save lot of GCP api
   * requests by keeping the searched instance locally in a static map. It resolves entire
   * concurrency issues in stopped instance search and start process. All requests may search a
   * stopped instances concurrently. Once they get one, they find if that instance already exists
   * in the map, if so it means another build has acquired the instance and currently probably
   * holding a lock on it, request give up and finds another instance until max re-attempts.
   * ** If instance doesn't exist, it tries to put the searched instance in map, several parallel
   * ** requests may come to this point as they hit the search together and may get the same
   * ** instance if the current availability is low. We solve this problem, by using putIfAbsent
   * ** of this map. Concurrent request try this method but only one request can get to put its
   * ** instance-id and build at a time, other requests fail to 'put' because putifAbsent may find
   * ** that the instance-id they're putting is already there, thus if concurrent request with same
   * ** instance-id try putting in map together, only one of them passes (that hits first) and other
   * ** just go on re-attempt searching.
   * 
   * So we synchronized the putIfAbsent call for all requests, they all will have to wait in a
   * queue to access this method but being its a local resource, this shouldn't be bad as it
   * guarantees only one request will get a hold of a stopped instance no matter how many concurrent
   * requests come.
   */
  // TODO: may need to update size of map later on when we've large no. of stopped instances.
  private static final Map<BigInteger, String> FOUND_INSTANCES = new ConcurrentHashMap<>(100);
  
  public static final int SEARCH_MAX_REATTEMPTS = 5;

  private GridStartHandlerImpl(APICoreProperties apiCoreProps
      , ResourceExecutor executor
      , ComputeService computeSrv
      , ResourceSearch search
      , FingerprintBasedUpdater fingerprintBasedUpdater
      , String zone
      , RequestGridCreate request) {
    super(apiCoreProps, executor, computeSrv, search, fingerprintBasedUpdater, zone, request);
  }
  
  @Override
  public ResponseEntity<ResponseGridCreate> handle() throws Exception {
    int attempts = 0;
    
    while (attempts < SEARCH_MAX_REATTEMPTS) {
      attempts++;
      
      Instance gridInstance = searchStoppedInstance();
      
      String existingBuild = FOUND_INSTANCES.get(gridInstance.getId());
      if (existingBuild != null) {
        LOG.info("The found stopped instance {} was reserved by another build {}, attempt #{} {}"
            , gridInstance.getName(), existingBuild, attempts, addToException());
        continue;
      }
      
      existingBuild = FOUND_INSTANCES.putIfAbsent(gridInstance.getId(), buildProp.getBuildId());
      if (existingBuild != null) {
        LOG.info("The found stopped instance {} was acquired by a concurrent request with build"
            + " {} as our put failed, attempt #{} {}"
            , gridInstance.getName(), existingBuild, attempts, addToException());
        continue;
      }
      
      // 'putIfAbsent' was successful, go ahead.
      try {
        onStoppedGridFoundEventHandler(gridInstance);  // lock instance among other things.
        // Note: if there is a problem in starting grid, let's not reset instance locking label
        // and investigate the issue and let instance not available for future requests.
        return startGrid(gridInstance);
      } finally {
        // we started the grid instance, lets remove our build from the map, so that once we're done
        // with it, another requests can use the instance. Use a finally block to guarantee removal.
        // Note that another request can't get this instance in search until we've shutdown the
        // instance (which will reset instance lock), thus its safe to remove from map here.
        FOUND_INSTANCES.remove(gridInstance.getId(), buildProp.getBuildId());
      }
    }
    
    LOG.error("maximum re-attempts reached while looking for a stopped instance, going to get a"
        + " fresh one {}", addToException()); 
    throw new GridStartHandlerFailureException(
        "maximum re-attempts reached while looking for a stopped instance"
        , new AcquireStoppedMaxReattemptException());  // give up
  }
  
  private Instance searchStoppedInstance() throws Exception {
    Optional<Instance> instance = search.searchInstance(request.getResourceSearchParams()
        , zone, InstanceStatus.TERMINATED, buildProp);
    
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
    LOG.info("Build {} acquired stopped instance {} and going to start it", addToException()
        , gridInstance.toPrettyString());
    
    lockGridInstance(gridInstance);
  }
  
  private ResponseEntity<ResponseGridCreate> startGrid(Instance gridInstance) throws Exception {
    GridStarter starter = new GridStarter(executor
        , computeSrv
        , fingerprintBasedUpdater
        , buildProp
        , request.getGridProperties()
        , gridInstance);
    
    CompletedOperation completedOperation = starter.start();
    
    Operation operation = completedOperation.get();
    if (!ResourceUtil.isOperationSuccess(operation)) {
      // TODO: if we see this in logs, look into the root cause and decide whether we should delete
      //  such instances.
      LOG.error("Couldn't start stopped grid instance {}, operation: {} {}"
          , gridInstance.toPrettyString()
          , operation.toPrettyString()
          , addToException());
      throw new GridStartHandlerFailureException(
          "Couldn't start stopped grid instance", new GridNotStartedException());  // give up
    }
    
    // fetch fresh to see updated values made by starter.
    gridInstance = computeSrv.getInstance(gridInstance.getName()
        , nameFromUrl(gridInstance.getZone())
        , buildProp);
    LOG.debug("started a grid instance {}:{} {}", gridInstance.getName(), gridInstance.getZone()
        , addToException());
    onGridStartEventHandler(gridInstance);
    
    ResponseGridCreate response = prepareResponse(gridInstance, HttpStatus.OK);
    return ResponseEntity
        .status(response.getHttpStatusCode())
        .body(response);
  }
  
  private void onGridStartEventHandler(Instance gridInstance) throws Exception {
    // ==========verify that we own this instance
    
    // TODO: we can remove this check later sometime if there is no such exception in logs as this
    //  shouldn't happen.
    //  get the current value of lock-by-build label
    String lockedByBuild = gridInstance.getLabels().get(ResourceUtil.LABEL_LOCKED_BY_BUILD);
    // see whether it holds our build.
    if (Strings.isNullOrEmpty(lockedByBuild) || !lockedByBuild.equals(buildProp.getBuildId())) {
      // signifies that some other request took our searched grid instance.
      // we'll not re-attempt finding another stopped instance for now and will just give up.
      LOG.error("Looks like some other request with buildId {} took out our instance, we waited"
          + " until it started and now giving it up. instance: {} {}"
          , lockedByBuild
          , gridInstance.toPrettyString()
          , addToException());
      throw new GridStartHandlerFailureException(
          "Looks like some other request took out our instance while it was starting up");
    }
    
    // ==========verify that an ongoing deployment is not going to delete it
    if (Boolean.parseBoolean(gridInstance.getLabels().get(ResourceUtil.LABEL_IS_DELETING))) {
      // signifies that an ongoing deployment is running. This instance may or may not delete
      // depending on when deployment script marked it for deletion, if it marked after it started,
      // it won't delete but if it marked it before that (while in terminated state) it may delete
      // soon, safer way is to get another fresh instance.
      // If this error is found in logs, such instance should be manually deleted for now. Not
      // reverting the LABEL_LOCKED_BY_BUILD label for now.
      // TODO: if we see this in logs, make sure we do something so that if instance is not deleted,
      //  it will be deleted.
      LOG.error("Instance started from stopped state, but is-deleting label is found true, rather"
          + ", than taking a chance with the ongoing deployment, leaving it out. instance: {} {}"
          , gridInstance.toPrettyString()
          , addToException());
      throw new GridStartHandlerFailureException(
          "Instance started from stopped state, but is-deleting label is found true"
          , new GridBeingDeletedFromOutsideException());  // give up
    }
    
    // verify the grid is running and there's nothing wrong
    if (isNotRunning(gridInstance)) {
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
    public GridStartHandler create(APICoreProperties apiCoreProps, ResourceExecutor executor
        , ComputeService computeSrv, ResourceSearch search
        , FingerprintBasedUpdater fingerprintBasedUpdater, String zone, RequestGridCreate request) {
      return new GridStartHandlerImpl(apiCoreProps, executor, computeSrv, search
          , fingerprintBasedUpdater, zone, request);
    }
  }
}
