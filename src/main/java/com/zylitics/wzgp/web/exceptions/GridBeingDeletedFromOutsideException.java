package com.zylitics.wzgp.web.exceptions;

public class GridBeingDeletedFromOutsideException extends RuntimeException {

  private static final long serialVersionUID = -6492982793168074130L;

  public GridBeingDeletedFromOutsideException() {
    super();
  }
  
  @SuppressWarnings("unused")
  public GridBeingDeletedFromOutsideException(String message) {
    super(message);
  }
  
  @SuppressWarnings("unused")
  public GridBeingDeletedFromOutsideException(String message, Throwable cause) {
    super(message, cause);
  }
}
