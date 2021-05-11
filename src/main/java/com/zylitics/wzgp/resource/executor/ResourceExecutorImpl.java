package com.zylitics.wzgp.resource.executor;

import static com.zylitics.wzgp.resource.util.ResourceUtil.nameFromUrl;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.google.api.client.http.HttpResponseException;
import com.google.api.client.json.GenericJson;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeRequest;
import com.google.api.services.compute.model.Operation;
import com.google.common.annotations.VisibleForTesting;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.CompletedOperation;
import com.zylitics.wzgp.resource.util.ResourceUtil;

/**
 * This is singleton for the life of application, take care with using any shared resource.
 * Re-attempt guidance from https://cloud.google.com/apis/design/errors
 * Note - This class is a work in progress.
 * @author Praveen Tiwari
 */
/*
 * Notes:
 * !!! The previous version of this file is saved as backup and this has now updated to have minimal
 * logic for now until we learn how and what type of exceptions return from gcp api. Once we've a
 * solid knowledge, refer to the backed-up file and work on this.
 * com.google.api.client.http.HttpRequest already has a re-attempt logic so we may not have to
 * build lot of re-attempt code around general http exceptions.
 */
@ThreadSafe
@Component
public class ResourceExecutorImpl implements ResourceExecutor, ResourceReattempter {
  
  private static final Logger LOG = LoggerFactory.getLogger(ResourceExecutorImpl.class);
  
  /**
   * <p>Zonal issues are issues that require us to re-attempt on to a different zone, when
   * re-attempts grows larger and in a row, its preferential to move the requester to a different
   * region/zone. Currently zonal issues are identified only by GCE error codes and mentioned in
   * config {@link APICoreProperties#getGceReattemptZones()}. Zonal issues are in fact resource
   * issues (http code = 429), but we want to separate zonal issues from resource issues so that we
   * could re-attempt on a different zone straight away without having to wait since we know that
   * these issues won't resolve with the problematic zone.</p>
   * As we use this api, we may see more GCE codes telling a zonal issues that will need to be
   * added into the list of codes. Unless added, those will be catched as 'resource issues'.
   */
  static final int ZONAL_ISSUES_MAX_REATTEMPTS = 5;
  
  private final Compute compute;
  private final APICoreProperties apiCoreProps;
  
  @Autowired
  ResourceExecutorImpl(Compute compute, APICoreProperties apiCoreProps) {
    this.compute = compute;
    this.apiCoreProps = apiCoreProps;
  }
  
  @Override
  public <T extends ComputeRequest<V>, V extends GenericJson> V executeWithReattempt(
      T objToExecute
      , @Nullable BuildProperty buildProp) throws Exception {
    Assert.notNull(objToExecute, "'objToExecute' can't be null.");
    
    try {
      V out = objToExecute.execute();
      if (out == null) {
        LOG.error("Got null while invoking execute on {} {}"
            , objToExecute
            , addToException(buildProp));
      }
      return out;
    } catch(IOException io) {
      // TODO: implement re-attempts after learning from logs.
      // Log messages for debugging the issue.
      HttpResponseException httpExp = (HttpResponseException) io;
      StringBuilder objectInfo = new StringBuilder();
      Object jsonContent = objToExecute.getJsonContent();
      if(jsonContent != null) {
        objectInfo.append(jsonContent);
      }
      objectInfo.append(objToExecute);
      LOG.error("An IOException occurred while invoking execute on {}. From inner"
          + " HttpResponseException: Status code= {}, Status message= {} {}"
          , objectInfo
          , httpExp.getStatusCode()
          , httpExp.getStatusMessage()
          , addToException(buildProp));
      // re-throw exception for handlers.
      throw io;
    }
  }
  
  /*
   * Notes:
   * Zonal re-attempts rely on Operation.Error.Errors.code. To get the code, we need to wait for
   * Operation completion and check if any error has occurred, if so, we can match the returned
   * codes with the ones we already know from apiCoreProps.getGceZonalReattemptErrors()
   * If the returned code matches, we can get a zone randomly from
   * apiCoreProps.getGceReattemptZones() to build a new object for re-attempt.
   * This method returns either an Exception or Operation. Operation is guaranteed to be 'DONE'
   * but may have failed. It's the responsibility of caller to evaluate the Operation to find
   * out whether it's succeeded based on error existence.
   */
  @Override
  public <T extends ComputeRequest<Operation>> CompletedOperation executeWithZonalReattempt(
      T objToExecute
      , Function<String, T> generateObjToExecutePerZone
      , @Nullable BuildProperty buildProp) throws Exception {
    Assert.notNull(objToExecute, "'objToExecute' can't be null.");
  
    long start = System.currentTimeMillis();
    // first execute the input object to get an Operation. Use method that will re-attempt in case
    // getting just the 'Operation' raises exceptions.
    Operation operation = executeWithReattempt(objToExecute, buildProp);
    // Now we've the Operation, let's wait for it's completion and see how it goes.
    operation = blockUntilComplete(operation, 2000, 300 * 1000, buildProp);
    LOG.debug("took {}secs waiting for new grid creation before reattempt",
        TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start));
    if (ResourceUtil.isOperationSuccess(operation)) {
      return new CompletedOperation(operation);
    }

    if (operation.getError() == null || operation.getError().getErrors() == null) {
      // shouldn't happen but still log.
      LOG.error("Operation {} returned no error on failure. Reattempt couldn't happen. {}"
          , operation.toPrettyString()
          , addToException(buildProp));
      return new CompletedOperation(operation);
    }
    
    // ! Do any other reattempt than zonal here

    if (apiCoreProps.getGceReattemptZones().size() == 1) {
      LOG.warn("There is only one zone, we can't do zonal reattempt");
      return new CompletedOperation(operation);
    }
  
    LOG.debug("Going to perform zonal reattempts");
    // Try if we should reattempt in another zone.
    // Reaching here means our request failed, check for error codes to reattempt in another zone.
    for (Operation.Error.Errors err : operation.getError().getErrors()) {
      if (err.getCode() != null
          && apiCoreProps.getGceZonalReattemptErrors().contains(err.getCode())) {
        return perZoneReattemptHandler(generateObjToExecutePerZone,
            buildProp,
            objToExecute instanceof Compute.Instances.Insert
                ? ((Compute.Instances.Insert) objToExecute).getZone()
                : null);
      }
    }
    
    // Reaching here means before invoking handler, we've to quit because the error codes don't
    // match the ones we already know. Log and return the first Operation.
    LOG.error("After waiting for Operation completion, the returned error codes aren't matched the"
        + " ones we have. Reattempt couldn't happen. Returned codes: {} {}" 
        , String.join(",", operationErrorsToCodes(operation))
        , addToException(buildProp));
    return new CompletedOperation(operation);
  }
  
  /**
   * Performs zonal re-attempts by choosing one of available zone randomly.
   * Re-attempts until one of the following is met:
   * 1. Successful Operation is returned post completion.
   * 2. Operation error contains an error not in 
   *    {@link APICoreProperties#getGceZonalReattemptErrors()}
   * 3. Maximum attempts count is equals to {@link #ZONAL_ISSUES_MAX_REATTEMPTS}
   * @param generateObjToExecutePerZone a {@link Function} that is used to get new object for
   * selected zone.
   * @return Operation
   * @throws Exception If there are problems in reattempting.
   */
  private <T extends ComputeRequest<Operation>> CompletedOperation perZoneReattemptHandler(
      Function<String, T> generateObjToExecutePerZone,
      @Nullable BuildProperty buildProp,
      @Nullable String zoneToExclude) throws Exception {
    Assert.notNull(generateObjToExecutePerZone, "'generateObjToExecutePerZone' can't be null.");
    LOG.debug("excluded zone {}", zoneToExclude);
    Random random = new Random();
    int attempts = 0;
    List<String> alternateZones = new ArrayList<>(apiCoreProps.getGceReattemptZones());
    if (zoneToExclude != null) {
      alternateZones.remove(zoneToExclude);
    }
    int totalAlternateZones = alternateZones.size();
    Preconditions.checkArgument(totalAlternateZones > 0, "No zone found to reattempt");
    Operation operation = null;
    int allowedReattempts = Math.min(ZONAL_ISSUES_MAX_REATTEMPTS, totalAlternateZones);
    
    while (attempts < allowedReattempts) {
      // increment in beginning so we don't set it at multiple places that are continuing in loop.
      attempts++;
      String randomZone = alternateZones.get(random.nextInt(totalAlternateZones));
      LOG.debug("reattempting in zone {}, attempt: {}", randomZone, attempts);
      long start = System.currentTimeMillis();
      T objToExecute = generateObjToExecutePerZone.apply(randomZone);
      operation = executeWithReattempt(objToExecute, buildProp);
      operation = blockUntilComplete(operation, 2000, 300 * 1000, buildProp);
      LOG.debug("took {}secs reattempting in zone {}",
          TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start), randomZone);
      if (ResourceUtil.isOperationSuccess(operation)) {
        LOG.debug("Operation {} succeeded on attempt #{}"
            , operation.toPrettyString()
            , attempts);
        return new CompletedOperation(operation);
      }
      
      if (operation.getError() == null || operation.getError().getErrors() == null) {
        // shouldn't happen but still log.
        LOG.warn("Operation {} returned no error on failure, attempt #{}"
            , operation.toPrettyString()
            , attempts);
        continue;
      }
      
      for (Operation.Error.Errors err : operation.getError().getErrors()) {
        if (err.getCode() == null
            || !apiCoreProps.getGceZonalReattemptErrors().contains(err.getCode())) {
          LOG.error("During reattempt #{}, the returned error codes aren't matched the ones we"
              + " have. Returned codes: {} {}"
              , (attempts)
              , String.join(",", operationErrorsToCodes(operation))
              , addToException(buildProp));
          return new CompletedOperation(operation);
        }
      }
    }
    LOG.error("maximum re-attempts reached for operation {} {}" 
        , operation.toPrettyString()
        , addToException(buildProp));
    return new CompletedOperation(operation);
  }
  
  @Override
  public Operation blockUntilComplete(Operation operation,
                                      long pollIntervalMillis,
                                      long timeoutMillis,
                                      @Nullable BuildProperty buildProp) throws Exception {
    return blockUntilComplete(operation, pollIntervalMillis, timeoutMillis, Clock.systemUTC(),
        buildProp);
  }
  
  @VisibleForTesting
  public Operation blockUntilComplete(Operation operation,
                                      long pollInterval,
                                      long timeoutMillis,
                                      Clock clock,
                                      @Nullable BuildProperty buildProp) throws Exception {
    Assert.notNull(operation, "Operation can't be null");
    
    Instant start = clock.instant();
    String zone = nameFromUrl(operation.getZone());
    String status = operation.getStatus();
    String operationName = operation.getName();
    
    while (!status.equals("DONE")) {
      Thread.sleep(pollInterval);
      Instant elapsed = clock.instant().minusMillis(timeoutMillis);
      if (elapsed.isAfter(start)) {
        throw new TimeoutException(String.format("Timed out waiting for Operation to complete."
            + " Operation: %s %s"
            , operation.toPrettyString()
            , addToException(buildProp)));
      }
      
      // Won't use ComputeService here to prevent a cyclic dependency.
      Compute.ZoneOperations.Get get = compute.zoneOperations().get(
          apiCoreProps.getResourceProjectId()
          , zone
          , operationName);
      operation = executeWithReattempt(get, buildProp);
      status = operation.getStatus();
    }
    return operation;
  }
  
  private String addToException(BuildProperty buildProp) {
    StringBuilder sb = new StringBuilder();
    if (buildProp != null) {
      sb.append(buildProp);
    }
    return sb.toString();
  }
  
  private List<String> operationErrorsToCodes(Operation operation) {
    return operation.getError().getErrors()
        .stream()
        .map(Operation.Error.Errors::getCode)
        .collect(Collectors.toList());
  }
}
