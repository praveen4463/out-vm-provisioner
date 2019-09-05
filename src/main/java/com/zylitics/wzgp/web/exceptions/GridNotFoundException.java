package com.zylitics.wzgp.web.exceptions;

/**
 * Thrown when a grid instance couldn't be found by its 'name'.
 * @author Praveen Tiwari
 *
 */
public class GridNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 3001543947557767420L;

  public GridNotFoundException(String message) {
    super(message);
  }
  
  public GridNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
