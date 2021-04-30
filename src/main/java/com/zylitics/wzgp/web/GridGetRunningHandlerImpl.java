package com.zylitics.wzgp.web;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.zylitics.wzgp.http.RequestGridCreate;
import com.zylitics.wzgp.http.ResponseGridCreate;
import com.zylitics.wzgp.model.InstanceStatus;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.compute.ComputeService;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.search.ResourceSearch;
import com.zylitics.wzgp.web.exceptions.AcquireStoppedMaxReattemptException;
import com.zylitics.wzgp.web.exceptions.GridGetRunningHandlerFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
  
    while (attempts < SEARCH_MAX_REATTEMPTS) {
      attempts++;
    
      Instance gridInstance = searchRunningInstance();
    
      String existingBuild = FOUND_INSTANCES.get(gridInstance.getId());
      if (existingBuild != null) {
        LOG.info("The found running instance {} was reserved by another build {}, attempt #{} {}"
            , gridInstance.getName(), existingBuild, attempts, addToException());
        continue;
      }
    
      existingBuild = FOUND_INSTANCES.putIfAbsent(gridInstance.getId(), buildProp.getBuildId());
      if (existingBuild != null) {
        LOG.info("The found running instance {} was acquired by a concurrent request with build"
                + " {} as our put failed, attempt #{} {}"
            , gridInstance.getName(), existingBuild, attempts, addToException());
        continue;
      }
    
      // 'putIfAbsent' was successful, go ahead.
      lockGridInstance(gridInstance); // blocks until done
      FOUND_INSTANCES.remove(gridInstance.getId(), buildProp.getBuildId());
      // update the requested properties in instance.
      List<Optional<Operation>> updateOperations = new ArrayList<>(5);
      updateOperations.add(customLabelsUpdateHandler(gridInstance,
          request.getGridProperties().getCustomLabels()));
      updateOperations.add(metadataUpdateHandler(gridInstance,
          request.getGridProperties().getMetadata()));
      // We've started all the updates at ones sequentially, they will most likely complete near
      // together and THERE MAY NOT BE ANY COMPLETION ORDER, MEANS A METADATA UPDATE CAN HAPPEN BEFORE
      // LABEL UPDATE, but we'll verify completion of all of them before returning instance.
      for (Optional<Operation> optOperation : updateOperations) {
        if (optOperation.isPresent()) {
          executor.blockUntilComplete(optOperation.get(), buildProp);
        }
      }
      ResponseGridCreate response = prepareResponse(gridInstance, HttpStatus.OK);
      return ResponseEntity
          .status(response.getHttpStatusCode())
          .body(response);
    }
  
    LOG.error("maximum re-attempts reached while looking for a running instance, going to get a"
        + " fresh one {}", addToException());
    throw new GridGetRunningHandlerFailureException(
        "maximum re-attempts reached while looking for a running instance"
        , new AcquireStoppedMaxReattemptException());  // give upreturn null;
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
  
  private Optional<Operation> customLabelsUpdateHandler(Instance gridInstance,
                                                        Map<String, String> customLabels)
      throws Exception {
    if (customLabels == null || customLabels.size() == 0) {
      return Optional.empty();
    }
    
    return Optional.ofNullable(fingerprintBasedUpdater.updateLabels(gridInstance
        , customLabels
        , buildProp));
  }
  
  private Optional<Operation> metadataUpdateHandler(Instance gridInstance,
                                                    Map<String, String> metadata)
      throws Exception {
    if (metadata == null || metadata.size() == 0) {
      return Optional.empty();
    }
    
    return Optional.ofNullable(fingerprintBasedUpdater.updateMetadata(gridInstance
        , metadata
        , buildProp));
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
