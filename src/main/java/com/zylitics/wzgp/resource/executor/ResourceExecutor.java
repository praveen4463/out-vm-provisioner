package com.zylitics.wzgp.resource.executor;

import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.json.GenericJson;
import com.google.api.services.compute.ComputeRequest;
import com.google.api.services.compute.model.Operation;
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
   * @param buildProp mainly used to append the build information with any logged exception.
   * @return The result of executing the given object post all re-attempts (if required). 
   */
  <T extends ComputeRequest<V>, V extends GenericJson> V executeWithReattempt(
      T objToExecute
      , @Nullable BuildProperty buildProp) throws Exception;
  
  /**
   * Executes the given object using {@link AbstractGoogleClientRequest#execute()}. It can
   * re-attempt based on the returned http error code and also able to get a new object using the
   * given {@link Function} {@code generateObjToExecutePerZone}, so that re-attempts can be made on
   * a different zone if required.
   * @param objToExecute The object to execute, multiple times if re-attempts required.
   * @param generateObjToExecutePerZone {@link Function} to generate a new object for new zone. It
   * accepts zone and returns the new object.
   * @param buildProp mainly used to append the build information with any logged exception.
   * @return {@link Operation}, that is the result of executing the given (or new) object post all
   * re-attempts (if required).
   */
  <T extends ComputeRequest<Operation>> CompletedOperation executeWithZonalReattempt(
      T objToExecute
      , Function<String, T> generateObjToExecutePerZone
      , @Nullable BuildProperty buildProp) throws Exception;
  
  /**
   * Block execution until an {@code Operation} is completed by repeatedly polling the status of
   * operation with compute api.
   * @param operation the Operation returned by the original request
   * @param pollIntervalMillis Polling interval in milliseconds. This has to be given very carefully
   *                           depending on the estimated time the operation may take to complete.
   *                           If this is too large, this method may block unnecessarily to longer
   *                           time even though the operation may have completed earlier.
   *                           It's important to choose a pooling interval that doesn't block
   *                           for longer periods in between pools. For instance, given 2 seconds time
   *                           for an op and would complete in 2 sec 100 millisecond, the second
   *                           poll may block the thread for 1sec 900 millis even though the op is
   *                           done.
   *                           On the other hand, if it's too low for an operation that may take long
   *                           time, more computing power and network bandwidth could be wasted in
   *                           repeated polling.
   * @param timeoutMillis Timeout duration in milliseconds for this operation's status polling
   * @param buildProp mainly used to append the build information with any logged exception.
   * @return Operation
   * @throws TimeoutException if we timed out waiting for the operation to complete
   * @throws Exception if we had trouble connecting
   */
  Operation blockUntilComplete(Operation operation,
                               long pollIntervalMillis,
                               long timeoutMillis,
                               @Nullable BuildProperty buildProp) throws Exception;
}
