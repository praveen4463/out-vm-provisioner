package com.zylitics.wzgp.web;

import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;

import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.google.common.collect.ImmutableMap;
import com.zylitics.wzgp.http.RequestGridCreate;
import com.zylitics.wzgp.http.ResponseGridCreate;
import com.zylitics.wzgp.http.ResponseStatus;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.SharedDependencies;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.grid.GridGenerator;
import com.zylitics.wzgp.resource.grid.GridStarter;
import com.zylitics.wzgp.resource.search.ResourceSearch;
import com.zylitics.wzgp.resource.util.ComputeCalls;
import com.zylitics.wzgp.resource.util.ResourceUtil;

public abstract class AbstractGridCreateHandler extends AbstractGridHandler {

  protected final RequestGridCreate request;
  protected final BuildProperty buildProp;
  private final ResourceExecutor executor;
  protected final ComputeCalls computeCalls;
  
  protected final String addToException;
  
  public AbstractGridCreateHandler(SharedDependencies sharedDep, RequestGridCreate request) {
    super(sharedDep);

    Assert.notNull(request, "RequestGridCreate can't be null");
    this.request = request;
    
    buildProp = request.getBuildProperties();
    executor = getExecutor();
    computeCalls = getComputeCalls();
    
    addToException = buildAddToException();
  }
  
  private ResourceExecutor getExecutor() {
    return ResourceExecutor.Factory.getDefault().create(sharedDep, buildProp);
  }
  
  private ComputeCalls getComputeCalls() {
    return new ComputeCalls(sharedDep, executor);
  }
  
  protected ResourceSearch getSearch() {
    return ResourceSearch.Factory.getDefault().create(request.getResourceSearchParams()
        , computeCalls);
  }
  
  protected GridGenerator getGridGenerator(Image sourceImage) {
    return new GridGenerator(sharedDep
        , request.getGridProperties()
        , sourceImage
        , executor);
  }
  
  protected GridStarter getGridStarter(Instance gridInstance) {
    return new GridStarter(sharedDep
        , buildProp
        , request.getGridProperties()
        , gridInstance
        , executor
        , computeCalls);
  }
  
  protected ResponseGridCreate prepareResponse(Instance gridInstance) {
    ResponseGridCreate response = new ResponseGridCreate();
    response.setGridInternalIP(gridInstance.getNetworkInterfaces().get(0).getNetworkIP());
    response.setGridId(gridInstance.getId());
    response.setGridName(gridInstance.getName());
    response.setHttpErrorStatusCode(HttpStatus.CREATED.value());
    response.setStatus(ResponseStatus.SUCCESS.name());
    response.setZone(gridInstance.getZone());
    return response;
  }
  
  protected void lockGridInstance(String gridInstanceName) throws Exception {
    computeCalls.setLabels(gridInstanceName
        , ImmutableMap.of(ResourceUtil.LABEL_LOCKED_BY_BUILD, buildProp.getBuildId()));
  }
  
  protected boolean isRunning(Instance gridInstance) {
    return gridInstance.getStatus().equals("RUNNING");
  }
  
  protected String buildAddToException() {
    StringBuilder sb = new StringBuilder();
    if (buildProp != null) {
      sb.append(buildProp.toString());
    }
    return sb.toString();
  }
}