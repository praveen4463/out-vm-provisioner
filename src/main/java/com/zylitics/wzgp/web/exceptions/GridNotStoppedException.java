package com.zylitics.wzgp.web.exceptions;

public class GridNotStoppedException extends RuntimeException {

  private static final long serialVersionUID = -7884128308342244118L;

  public GridNotStoppedException(String message) {
    super(message);
  }
  
  @SuppressWarnings("unused")
  public GridNotStoppedException(String message, Throwable cause) {
    super(message, cause);
  }
}
