package com.zylitics.wzgp.resource.executor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import com.google.api.client.http.HttpResponseException;
import com.google.api.client.json.GenericJson;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeRequest;
import com.google.api.services.compute.model.Operation;
import com.google.cloud.GcpLaunchStage.Alpha;
import com.zylitics.wzgp.config.SharedDependencies;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.CompletedOperation;
import com.zylitics.wzgp.resource.util.ResourceUtil;

/**
 * This class is designed in a way that one object in a program can be used multiple times.
 * Re-attempt guidance from https://cloud.google.com/apis/design/errors
 * Implementation is not thread-safe.
 * This class is a work in progress.
 * @author Praveen Tiwari
 */
/*
 * Notes:
 * !!! The previous version of this file is saved as backup and this has now updated to have minimal
 * logic for now until we learn how and what type of exceptions returns from gcp api. Once we've a
 * solid knowledge, refer to the backed-up file and work on this.
 * com.google.api.client.http.HttpRequest already has a re-attempt logic so we may not have to
 * build lot of re-attempt code around general http exceptions.
 */
@Alpha
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
  private static final int ZONAL_ISSUES_MAX_REATTEMPTS = 5;
  
  private final SharedDependencies sharedDep;
  private final BuildProperty buildProp;
  private final Random random;
  
  private ResourceExecutorImpl(SharedDependencies sharedDep, BuildProperty buildProp) {
    this.sharedDep = sharedDep;
    this.buildProp = buildProp;
    random = new Random();
  }
  
  @Override
  public <T extends ComputeRequest<V>, V extends GenericJson> V executeWithReattempt(
      T objToExecute) throws Exception {
    Assert.notNull(objToExecute, "object to execute can't be null.");
    
    try {
      V out = objToExecute.setOauthToken(sharedDep.token()).execute();
      if (out == null) {
        // Shouldn't happen but still log.
        LOG.error("Got null while inoking execute() on {} {}" 
            , objToExecute.toString()
            , buildProp.toString());
      }
      return out;
    } catch(IOException io) {
      // TODO: implement re-attempts after learning from logs.
      // Log messages for debugging the issue.
      try {
        HttpResponseException httpExp = (HttpResponseException) io;
        LOG.error("An IOException occurred while inoking execute() on {}. From inner"
            + " HttpResponseException: Status code= {}, Status message= {} {}"
            , objToExecute.toString()
            , httpExp.getStatusCode()
            , httpExp.getStatusMessage()
            , buildProp.toString());
      } catch (ClassCastException cce) {
        LOG.error("An IOException occurred while inoking execute() on {}, HttpResponseException"
            + " isn't found wrapped. {}"
            , objToExecute.toString()
            , buildProp.toString());
      }
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
      , Function<String, T> generateObjToExecutePerZone) throws Exception {
    Assert.notNull(objToExecute, "object to execute can't be null.");
    Assert.notNull(generateObjToExecutePerZone, "generateObjToExecutePerZone can't be null.");
    
    // first execute the input object to get an Operation. Use method that will re-attempt in case
    // getting just the 'Operation' raises exceptions.
    Operation operation = executeWithReattempt(objToExecute);
    // Now we've the Operation, let's wait for it's completion and see how it goes.
    operation = blockUntilComplete(operation);
    
    if (ResourceUtil.isOperationSuccess(operation)) {
      return new CompletedOperation(operation);
    }

    // Reaching here means our request failed, check for error codes.
    for (Operation.Error.Errors err : operation.getError().getErrors()) {
      if (err.getCode() != null 
          && sharedDep.apiCoreProps().getGceZonalReattemptErrors().contains(err.getCode())) {
        return perZoneReattemptHandler(generateObjToExecutePerZone);
      }
    }
    // Reaching here means before invoking handler, we've to quit because the error codes don't
    // match the ones we already know. Log and return the first Operation.
    LOG.error("After waiting for Operation completion, the returned error codes aren't matched the"
        + " ones we have. Reattempt couldn't happen. Returned codes: {} {}" 
        , String.join(",", operationErrorsToCodes(operation))
        , buildProp.toString());
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
   * @throws IOException
   */
  private <T extends ComputeRequest<Operation>> CompletedOperation perZoneReattemptHandler(
      Function<String, T> generateObjToExecutePerZone) throws Exception {
    int attempts = 0;
    List<String> alternateZones =
        new ArrayList<>(sharedDep.apiCoreProps().getGceReattemptZones());
    int totalAlternateZones = alternateZones.size();
    Operation operation = null;
    
    while (attempts < ZONAL_ISSUES_MAX_REATTEMPTS) {
      String randomZone = alternateZones.get(random.nextInt(totalAlternateZones));
      T objToExecute = generateObjToExecutePerZone.apply(randomZone);
      operation = executeWithReattempt(objToExecute);
      operation = blockUntilComplete(operation);
      
      if (ResourceUtil.isOperationSuccess(operation)) {
        LOG.debug("Operation {} succeeded on attempt # {}"
            , operation.toPrettyString()
            , (attempts + 1));
        return new CompletedOperation(operation);
      }
      
      for (Operation.Error.Errors err : operation.getError().getErrors()) {
        if (err.getCode() == null 
            || !sharedDep.apiCoreProps().getGceZonalReattemptErrors().contains(err.getCode())) {
          LOG.error("During reattempt # {}, the returned error codes aren't matched the ones we"
              + " have. Returned codes: {} {}"
              , (attempts + 1)
              , String.join(",", operationErrorsToCodes(operation))
              , buildProp.toString());
          return new CompletedOperation(operation);
        }
      }
      attempts++;
    }
    LOG.error("maximum re-attempts reached for operation {} {}" 
        , operation.toPrettyString()
        , buildProp.toString());
    return new CompletedOperation(operation);
  }
  
  @Override
  public Operation blockUntilComplete(Operation operation) throws Exception {
    Assert.notNull(operation, "Operation can't be null");
    
    long start = System.currentTimeMillis();
    long pollInterval = 10 * 1000;
    
    String zone = operation.getZone();
    String[] bits = zone.split("/");
    zone = bits[bits.length - 1];
    
    String status = operation.getStatus();
    String opId = operation.getName();
    
    while (!status.equals("DONE")) {
      Thread.sleep(pollInterval);
      long elapsed = System.currentTimeMillis() - start;
      if (elapsed >= sharedDep.apiCoreProps().getGceTimeoutMillis()) {
        throw new TimeoutException(String.format("Timed out waiting for Operation to complete."
            + " Operation: %s %s"
            , operation.toPrettyString()
            , buildProp.toString()));
      }
      
      // Won't use ComputeCalls here.
      Compute.ZoneOperations.Get get = sharedDep.compute().zoneOperations().get(
          sharedDep.apiCoreProps().getProjectId()
          , zone
          , opId);
      operation = executeWithReattempt(get);
      status = operation.getStatus();
    }
    return operation;
  }
  
  private List<String> operationErrorsToCodes(Operation operation) {
    return operation.getError().getErrors()
        .stream()
        .map(errors -> errors.getCode())
        .collect(Collectors.toList());
  }
  
  public static class Factory implements ResourceExecutor.Factory {
    
    @Override
    public ResourceExecutor create(SharedDependencies sharedDep
        , BuildProperty buildProp) {
      return new ResourceExecutorImpl(sharedDep
          , buildProp);
    }
  }
}
