package com.zylitics.wzgp.http;

public abstract class AbstractResponse {

  private String status;
  private String error;
  private int httpStatusCode;
  
  public String getStatus() {
    return status;
  }
  
  public void setStatus(String status) {
    this.status = status;
  }
  
  @SuppressWarnings("unused")
  public String getError() {
    return error;
  }
  
  public void setError(String error) {
    this.error = error;
  }

  public int getHttpStatusCode() {
    return httpStatusCode;
  }

  public void setHttpStatusCode(int httpErrorStatusCode) {
    this.httpStatusCode = httpErrorStatusCode;
  }
}
