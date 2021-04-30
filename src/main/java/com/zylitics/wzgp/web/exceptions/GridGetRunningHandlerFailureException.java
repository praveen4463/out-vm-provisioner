package com.zylitics.wzgp.web.exceptions;

public class GridGetRunningHandlerFailureException extends Exception {
  
  private static final long serialVersionUID = 7600775904477809300L;
  
  public GridGetRunningHandlerFailureException() {
    super();
  }
  
  public GridGetRunningHandlerFailureException(String message) {
    super(message);
  }
  
  public GridGetRunningHandlerFailureException(String message, Throwable cause) {
    super(message, cause);
  }
}
