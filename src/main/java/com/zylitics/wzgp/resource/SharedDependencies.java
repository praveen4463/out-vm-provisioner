package com.zylitics.wzgp.resource;

import com.google.api.services.compute.Compute;

public class SharedDependencies {

  private final Compute compute;
  private final APICoreProperties apiCoreProps;
  private final String zone;
  
  public SharedDependencies(Compute compute
      , APICoreProperties apiCoreProps
      , String zone) {
    this.compute = compute;
    this.apiCoreProps = apiCoreProps;
    this.zone = zone;
  }
  
  public Compute compute() {
    return compute;
  }
  
  public APICoreProperties apiCoreProps() {
    return apiCoreProps;
  }
  
  public String zone() {
    return zone;
  }
}
