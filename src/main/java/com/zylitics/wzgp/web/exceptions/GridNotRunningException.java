package com.zylitics.wzgp.web.exceptions;

import com.google.api.services.compute.model.Operation;

/**
 * Thrown when the grid instance is found not in RUNNING state even after the {@link Operation} is
 * completed.
 * @author Praveen Tiwari
 *
 */
public class GridNotRunningException extends RuntimeException {

  private static final long serialVersionUID = 8172380696228134127L;
  
  public GridNotRunningException() {}
  
  public GridNotRunningException(String message) {
    super(message);
  }
  
  public GridNotRunningException(String message, Throwable cause) {
    super(message, cause);
  }
}
