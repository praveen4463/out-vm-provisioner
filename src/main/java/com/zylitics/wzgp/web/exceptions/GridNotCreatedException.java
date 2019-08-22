package com.zylitics.wzgp.web.exceptions;

public class GridNotCreatedException extends RuntimeException {

  private static final long serialVersionUID = 3396132167351874488L;

  public GridNotCreatedException(String message) {
    super(message);
  }
  
  public GridNotCreatedException(String message, Throwable cause) {
    super(message, cause);
  }
}
