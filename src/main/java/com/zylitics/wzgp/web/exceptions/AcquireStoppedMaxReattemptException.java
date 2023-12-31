package com.zylitics.wzgp.web.exceptions;

public class AcquireStoppedMaxReattemptException extends RuntimeException {

  private static final long serialVersionUID = -6492982793168074130L;

  public AcquireStoppedMaxReattemptException() {
    super();
  }
  
  @SuppressWarnings("unused")
  public AcquireStoppedMaxReattemptException(String message) {
    super(message);
  }
  
  @SuppressWarnings("unused")
  public AcquireStoppedMaxReattemptException(String message, Throwable cause) {
    super(message, cause);
  }
}
