package com.zylitics.wzgp.resource;

import org.springframework.util.Assert;

import com.google.api.services.compute.model.Operation;

/**
 * Wrapper to {@link Operation} that guarantees a completed Operation,
 * which means {@link Operation#getStatus()} is DONE. It doesn't tell anything about whether
 * Operation was succeeded.
 * @author Praveen Tiwari
 *
 */
public class CompletedOperation {
  
  private final Operation operation;

  public CompletedOperation(Operation operation) {
    Assert.notNull(operation, "Can't set a null Operation.");
    Assert.isTrue(operation.getStatus().equals("DONE"), "This Operation isn't yet completed.");
    
    this.operation = operation;
  }
  
  public Operation get() {
    return operation;
  }
}
