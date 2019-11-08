package com.zylitics.wzgp.web;

import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.compute.ComputeService;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;

public abstract class AbstractGridHandler {

  protected final APICoreProperties apiCoreProps;
  protected final ResourceExecutor executor;
  protected final ComputeService computeSrv;
  protected final FingerprintBasedUpdater fingerprintBasedUpdater;
  protected final String zone;
  
  AbstractGridHandler(APICoreProperties apiCoreProps
      , ResourceExecutor executor
      , ComputeService computeSrv
      , FingerprintBasedUpdater fingerprintBasedUpdater
      , String zone) {
    this.apiCoreProps = apiCoreProps;
    this.executor = executor;
    this.computeSrv = computeSrv;
    this.fingerprintBasedUpdater = fingerprintBasedUpdater;
    this.zone = zone;
  }
}
