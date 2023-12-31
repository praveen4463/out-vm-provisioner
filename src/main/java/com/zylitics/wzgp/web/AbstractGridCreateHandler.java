package com.zylitics.wzgp.web;

import static com.zylitics.wzgp.resource.util.ResourceUtil.nameFromUrl;

import com.google.api.services.compute.model.NetworkInterface;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;

import com.google.api.services.compute.model.Instance;
import com.zylitics.wzgp.http.RequestGridCreate;
import com.zylitics.wzgp.http.ResponseGridCreate;
import com.zylitics.wzgp.http.ResponseStatus;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.compute.ComputeService;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.search.ResourceSearch;

abstract class AbstractGridCreateHandler extends AbstractGridHandler {
  
  final ResourceSearch search;
  final RequestGridCreate request;
  final BuildProperty buildProp;
  
  AbstractGridCreateHandler(APICoreProperties apiCoreProps
      , ResourceExecutor executor
      , ComputeService computeSrv
      , ResourceSearch search
      , FingerprintBasedUpdater fingerprintBasedUpdater
      , String zone
      , RequestGridCreate request) {
    super(apiCoreProps, executor, computeSrv, fingerprintBasedUpdater, zone);

    this.search = search;
    Assert.notNull(request, "RequestGridCreate can't be null");
    this.request = request;
    buildProp = request.getBuildProperties();
  }
  
  ResponseGridCreate prepareResponse(Instance gridInstance, HttpStatus status) {
    ResponseGridCreate response = new ResponseGridCreate();
    NetworkInterface netInterface = gridInstance.getNetworkInterfaces().get(0);
    response.setGridInternalIP(netInterface.getNetworkIP());
    if (netInterface.getAccessConfigs() != null && netInterface.getAccessConfigs().get(0) != null) {
      response.setGridExternalIP(netInterface.getAccessConfigs().get(0).getNatIP());
    }
    response.setGridId(gridInstance.getId());
    response.setGridName(gridInstance.getName());
    response.setHttpStatusCode(status.value());
    response.setStatus(ResponseStatus.SUCCESS.name());
    response.setZone(nameFromUrl(gridInstance.getZone()));
    return response;
  }
  
  boolean isNotRunning(Instance gridInstance) {
    return !gridInstance.getStatus().equals("RUNNING");
  }
  
  String addToException() {
    StringBuilder sb = new StringBuilder();
    if (buildProp != null) {
      sb.append(buildProp);
    }
    return sb.toString();
  }
}
