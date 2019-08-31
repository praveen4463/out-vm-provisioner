package com.zylitics.wzgp.web;

import org.springframework.http.ResponseEntity;

import com.google.api.services.compute.Compute;
import com.zylitics.wzgp.http.RequestGridCreate;
import com.zylitics.wzgp.http.ResponseGridCreate;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.service.ComputeService;

public interface GridGenerateHandler {

  ResponseEntity<ResponseGridCreate> handle() throws Exception;
  
  void setSourceImageFamily(String sourceImageFamily);
  
  interface Factory {
    
    GridGenerateHandler create(Compute compute
      , APICoreProperties apiCoreProps
      , ResourceExecutor executor
      , ComputeService computeSrv
      , String zone
      , RequestGridCreate request);
  }
}
