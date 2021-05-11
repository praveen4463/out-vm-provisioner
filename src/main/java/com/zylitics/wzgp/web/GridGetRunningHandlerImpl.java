package com.zylitics.wzgp.web;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.zylitics.wzgp.http.RequestGridCreate;
import com.zylitics.wzgp.http.ResponseGridCreate;
import com.zylitics.wzgp.model.InstanceStatus;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.compute.ComputeService;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.grid.GridProperty;
import com.zylitics.wzgp.resource.search.ResourceSearch;
import com.zylitics.wzgp.resource.util.ResourceUtil;
import com.zylitics.wzgp.web.exceptions.AcquireStoppedMaxReattemptException;
import com.zylitics.wzgp.web.exceptions.GridGetRunningHandlerFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class GridGetRunningHandlerImpl extends AbstractGridCreateHandler
    implements GridGetRunningHandler {
  
  private static final Logger LOG = LoggerFactory.getLogger(GridGetRunningHandlerImpl.class);
  
  // works same as in start handler
  // TODO: may need to update size of map later on when we've large no. of running instances.
  private static final Map<BigInteger, String> FOUND_INSTANCES = new ConcurrentHashMap<>(100);
  
  public static final int SEARCH_MAX_REATTEMPTS = 5;
  
  private GridGetRunningHandlerImpl(APICoreProperties apiCoreProps
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
    GridProperty gridProperty = request.getGridProperties();
  
    while (attempts < SEARCH_MAX_REATTEMPTS) {
      attempts++;
    
      LOG.debug("get running handler, going to find running instances in zone{}, attempt #{}",
          zone, attempts);
      
      long start = System.currentTimeMillis();
      Instance gridInstance = searchRunningInstance();
      LOG.debug("took {}secs finding running instances",
          TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start));
  
      String existingBuild = FOUND_INSTANCES.putIfAbsent(gridInstance.getId(),
          buildProp.getBuildId());
      if (existingBuild != null) {
        LOG.info("The found running instance {} was acquired by a concurrent request with build"
                + " {} as our put failed, attempt #{} {}"
            , gridInstance.getName(), existingBuild, attempts, addToException());
        continue;
      }
  
      start = System.currentTimeMillis();
      // 'putIfAbsent' was successful, go ahead.
      List<Operation> updateOperations = new ArrayList<>(5);
      Map<String, String> labelsToUpdate = new HashMap<>();
      labelsToUpdate.put(ResourceUtil.LABEL_LOCKED_BY_BUILD, buildProp.getBuildId());
      if (gridProperty.getCustomLabels() != null) {
        labelsToUpdate.putAll(gridProperty.getCustomLabels());
      }
      updateOperations.add(fingerprintBasedUpdater.updateLabelsGivenFreshlyFetchedInstance(
          gridInstance,
          labelsToUpdate,
          buildProp));
      if (gridProperty.getMetadata() != null && gridProperty.getMetadata().size() > 0) {
        updateOperations.add(fingerprintBasedUpdater.updateMetadataGivenFreshlyFetchedInstance(
            gridInstance,
            gridProperty.getMetadata(),
            buildProp));
      }
      // all update ops started together
      for (Operation op : updateOperations) {
        executor.blockUntilComplete(op, 500, 10000, buildProp);
      }
      LOG.debug("took {}secs finishing update to requested properties in instance",
          TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start));
      // we've locked instance, remove it from found list
      FOUND_INSTANCES.remove(gridInstance.getId(), buildProp.getBuildId());
      ResponseGridCreate response = prepareResponse(gridInstance, HttpStatus.OK);
      return ResponseEntity
          .status(response.getHttpStatusCode())
          .body(response);
    }
  
    LOG.error("maximum re-attempts reached while looking for a running instance, going to get a"
        + " fresh one {}", addToException());
    throw new GridGetRunningHandlerFailureException(
        "maximum re-attempts reached while looking for a running instance"
        , new AcquireStoppedMaxReattemptException());  // give up return null;
  }
  
  private Instance searchRunningInstance() throws Exception {
    Optional<Instance> instance = search.searchInstance(request.getResourceSearchParams()
        , zone, InstanceStatus.RUNNING, buildProp);
    
    if (!instance.isPresent()) {
      LOG.warn("No running instance found that matches the given search terms, search terms: {} {}"
          , request.getResourceSearchParams().toString()
          , addToException());
      throw new GridGetRunningHandlerFailureException();  // give up
    }
    
    return instance.get();
  }
  
  public static class Factory implements GridGetRunningHandler.Factory {
    
    @Override
    public GridGetRunningHandler create(APICoreProperties apiCoreProps, ResourceExecutor executor
        , ComputeService computeSrv, ResourceSearch search
        , FingerprintBasedUpdater fingerprintBasedUpdater, String zone, RequestGridCreate request) {
      return new GridGetRunningHandlerImpl(apiCoreProps, executor, computeSrv, search
          , fingerprintBasedUpdater, zone, request);
    }
  }
}
