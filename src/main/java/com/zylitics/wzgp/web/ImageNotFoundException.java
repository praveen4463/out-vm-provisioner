package com.zylitics.wzgp.web;

public class ImageNotFoundException extends RuntimeException {

  private static final long serialVersionUID = -6492982793168074130L;

  public ImageNotFoundException(String message) {
    super(message);
  }
  
  public ImageNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
