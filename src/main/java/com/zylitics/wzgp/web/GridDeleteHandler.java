package com.zylitics.wzgp.web;

import org.springframework.http.ResponseEntity;

import com.zylitics.wzgp.http.ResponseGridDelete;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.compute.ComputeService;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;

public interface GridDeleteHandler {

  ResponseEntity<ResponseGridDelete> handle() throws Exception;
  
  void setSessionId(String sessionId);
  
  void setNoRush(boolean noRush);
  
  interface Factory {
    
    GridDeleteHandler create(APICoreProperties apiCoreProps
        , ResourceExecutor executor
        , ComputeService computeSrv
        , FingerprintBasedUpdater fingerprintBasedUpdater
        , String zone
        , String gridName);
  }
}
