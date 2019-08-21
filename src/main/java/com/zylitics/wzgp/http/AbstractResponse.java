package com.zylitics.wzgp.http;

public abstract class AbstractResponse {

  private String status;
  private String error;
  private int httpErrorStatusCode;
  
  public String getStatus() {
    return status;
  }
  
  public void setStatus(String status) {
    this.status = status;
  }
  
  public String getError() {
    return error;
  }
  
  public void setError(String error) {
    this.error = error;
  }

  public int getHttpErrorStatusCode() {
    return httpErrorStatusCode;
  }

  public void setHttpErrorStatusCode(int httpErrorStatusCode) {
    this.httpErrorStatusCode = httpErrorStatusCode;
  }
}
