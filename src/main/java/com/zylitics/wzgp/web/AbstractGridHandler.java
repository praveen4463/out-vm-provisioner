package com.zylitics.wzgp.web;

import org.springframework.http.ResponseEntity;

import com.zylitics.wzgp.http.AbstractResponse;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.service.ComputeService;

public abstract class AbstractGridHandler {

  protected final APICoreProperties apiCoreProps;
  protected final ResourceExecutor executor;
  protected final ComputeService computeSrv;
  protected final String zone;
  
  public AbstractGridHandler(APICoreProperties apiCoreProps
      , ResourceExecutor executor
      , ComputeService computeSrv
      , String zone) {
    this.apiCoreProps = apiCoreProps;
    this.executor = executor;
    this.computeSrv = computeSrv;
    this.zone = zone;
  }
  
  public abstract ResponseEntity<? extends AbstractResponse> handle() throws Exception;
}
