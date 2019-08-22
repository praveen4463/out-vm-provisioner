package com.zylitics.wzgp.web.exceptions;

import com.zylitics.wzgp.web.GridStartHandler;

/**
 * Indicates that {@link GridStartHandler} failed to provide an instance, this could happen for any
 * reason such as no stopped instance found, stopped instance couldn't start, another request got
 * hold of the instance during start and so on. Controller should catch this and try alternatives
 * to get a grid instance.
 * @author Praveen Tiwari
 *
 */
public class GridStartHandlerFailureException extends Exception {

  private static final long serialVersionUID = -580473977299412266L;
  
  public GridStartHandlerFailureException() {}

  public GridStartHandlerFailureException(String message) {
    super(message);
  }
  
  public GridStartHandlerFailureException(String message, Throwable cause) {
    super(message, cause);
  }
}
