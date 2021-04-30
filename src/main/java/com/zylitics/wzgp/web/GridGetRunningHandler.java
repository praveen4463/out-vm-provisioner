package com.zylitics.wzgp.web;

import com.zylitics.wzgp.http.RequestGridCreate;
import com.zylitics.wzgp.http.ResponseGridCreate;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.compute.ComputeService;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.search.ResourceSearch;
import org.springframework.http.ResponseEntity;

public interface GridGetRunningHandler {
  
  ResponseEntity<ResponseGridCreate> handle() throws Exception;
  
  interface Factory {
    
    GridGetRunningHandler create(APICoreProperties apiCoreProps
        , ResourceExecutor executor
        , ComputeService computeSrv
        , ResourceSearch search
        , FingerprintBasedUpdater fingerprintBasedUpdater
        , String zone
        , RequestGridCreate request);
  }
}
