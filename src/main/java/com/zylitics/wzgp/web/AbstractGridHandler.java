package com.zylitics.wzgp.web;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.google.common.collect.ImmutableMap;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.compute.ComputeService;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.util.ResourceUtil;

import javax.annotation.Nullable;

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
  
  void unlockGridInstance(Instance gridInstance,
                          boolean waitForCompletion,
                          @Nullable BuildProperty buildProp)
      throws Exception {
    Operation operation = fingerprintBasedUpdater.updateLabels(gridInstance
        , ImmutableMap.of(ResourceUtil.LABEL_LOCKED_BY_BUILD, "none"), buildProp);
    if (waitForCompletion) {
      executor.blockUntilComplete(operation, buildProp);
    }
  }
}
