package com.zylitics.wzgp.web;

import static com.zylitics.wzgp.resource.util.ResourceUtil.nameFromUrl;

import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.google.common.collect.ImmutableMap;
import com.zylitics.wzgp.http.RequestGridCreate;
import com.zylitics.wzgp.http.ResponseGridCreate;
import com.zylitics.wzgp.http.ResponseStatus;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.compute.ComputeService;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.search.ResourceSearch;
import com.zylitics.wzgp.resource.util.ResourceUtil;

public abstract class AbstractGridCreateHandler extends AbstractGridHandler {

  protected final ResourceSearch search;
  protected final RequestGridCreate request;
  protected final BuildProperty buildProp;
  
  public AbstractGridCreateHandler(APICoreProperties apiCoreProps
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
  
  protected ResponseGridCreate prepareResponse(Instance gridInstance, HttpStatus status) {
    ResponseGridCreate response = new ResponseGridCreate();
    response.setGridInternalIP(gridInstance.getNetworkInterfaces().get(0).getNetworkIP());
    response.setGridId(gridInstance.getId());
    response.setGridName(gridInstance.getName());
    response.setHttpStatusCode(status.value());
    response.setStatus(ResponseStatus.SUCCESS.name());
    response.setZone(nameFromUrl(gridInstance.getZone()));
    return response;
  }
  
  protected void lockGridInstance(Instance gridInstance) throws Exception {
    Operation operation = fingerprintBasedUpdater.updateLabels(gridInstance
        , ImmutableMap.of(ResourceUtil.LABEL_LOCKED_BY_BUILD, buildProp.getBuildId()), buildProp);
    executor.blockUntilComplete(operation, buildProp);
  }
  
  protected boolean isRunning(Instance gridInstance) {
    return gridInstance.getStatus().equals("RUNNING");
  }
  
  protected String addToException() {
    StringBuilder sb = new StringBuilder();
    if (buildProp != null) {
      sb.append(buildProp.toString());
    }
    return sb.toString();
  }
}
