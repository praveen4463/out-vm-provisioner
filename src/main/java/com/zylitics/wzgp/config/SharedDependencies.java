package com.zylitics.wzgp.config;

import com.google.api.services.compute.Compute;
import com.zylitics.wzgp.resource.APICoreProperties;

public class SharedDependencies {

  private final Compute compute;
  private final String token;
  private final APICoreProperties apiCoreProps;
  private final String zone;
  
  public SharedDependencies(Compute compute
      , String token
      , APICoreProperties apiCoreProps
      , String zone) {
    this.compute = compute;
    this.token = token;
    this.apiCoreProps = apiCoreProps;
    this.zone = zone;
  }
  
  public Compute compute() {
    return compute;
  }
  
  public String token() {
    return token;
  }
  
  public APICoreProperties apiCoreProps() {
    return apiCoreProps;
  }
  
  public String zone() {
    return zone;
  }
}
