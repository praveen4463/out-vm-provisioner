package com.zylitics.wzgp.resource.executor;

import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.json.GenericJson;
import com.google.api.services.compute.ComputeRequest;
import com.google.api.services.compute.model.Operation;
import com.zylitics.wzgp.config.SharedDependencies;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.CompletedOperation;

/**
 * Interface that defines methods to execute pre-built objects with re-attempts. Re-attempts should
 * use rules and algorithms to maximize the possibility of getting a valid result. 
 * @author Praveen Tiwari
 */
public interface ResourceExecutor {

  /**
   * Executes the given object using {@link AbstractGoogleClientRequest#execute()}. It can
   * re-attempt based on the returned http error code.
   * @param objToExecute The object to execute, multiple times if re-attempts required.
   * @return The result of executing the given object post all re-attempts (if required). 
   */
  <T extends ComputeRequest<V>, V extends GenericJson> V executeWithReattempt(
      T objToExecute) throws Exception;
  
  /**
   * Executes the given object using {@link AbstractGoogleClientRequest#execute()}. It can
   * re-attempt based on the returned http error code and also able to get a new object using the
   * given {@link Function} {@code generateObjToExecutePerZone}, so that re-attempts can be made on
   * a different zone if required.
   * @param objToExecute The object to execute, multiple times if re-attempts required.
   * @param generateObjToExecutePerZone {@link Function} to generate a new object for new zone. It
   * accepts zone and returns the new object.
   * @return {@link Operation}, that is the result of executing the given (or new) object post all
   * re-attempts (if required).
   */
  <T extends ComputeRequest<Operation>> CompletedOperation executeWithZonalReattempt(
      T objToExecute, Function<String, T> generateObjToExecutePerZone) throws Exception;
  
  /**
   * Wait until {@code Operation} is completed.
   * @param operation the Operation returned by the original request
   * @return Operation
   * @throws TimeoutException if we timed out waiting for the operation to complete
   * @throws Exception if we had trouble connecting
   */
  Operation blockUntilComplete(Operation operation) throws Exception;
  
  interface Factory {
    
    static Factory getDefault() {
      return new ResourceExecutorImpl.Factory();
    }
    
    ResourceExecutor create(SharedDependencies sharedDep
      , BuildProperty buildProp);
  }
}
