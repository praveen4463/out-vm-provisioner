package com.zylitics.wzgp.web.exceptions;

public class GridNotStartedException extends RuntimeException {

  private static final long serialVersionUID = -6492982793168074130L;

  public GridNotStartedException() {
    super();
  }
  
  @SuppressWarnings("unused")
  public GridNotStartedException(String message) {
    super(message);
  }
  
  @SuppressWarnings("unused")
  public GridNotStartedException(String message, Throwable cause) {
    super(message, cause);
  }
}
