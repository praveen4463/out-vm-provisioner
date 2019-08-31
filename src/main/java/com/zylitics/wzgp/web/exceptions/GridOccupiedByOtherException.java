package com.zylitics.wzgp.web.exceptions;

public class GridOccupiedByOtherException extends RuntimeException {

  private static final long serialVersionUID = -6492982793168074130L;

  public GridOccupiedByOtherException() {
    super();
  }
  
  public GridOccupiedByOtherException(String message) {
    super(message);
  }
  
  public GridOccupiedByOtherException(String message, Throwable cause) {
    super(message, cause);
  }
}
