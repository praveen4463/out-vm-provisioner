package com.zylitics.wzgp.web.exceptions;

public class GridNotDeletedException extends RuntimeException {

  private static final long serialVersionUID = 3001543947557767420L;

  public GridNotDeletedException(String message) {
    super(message);
  }
  
  @SuppressWarnings("unused")
  public GridNotDeletedException(String message, Throwable cause) {
    super(message, cause);
  }
}
