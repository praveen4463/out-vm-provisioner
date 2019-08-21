package com.zylitics.wzgp.web;

import org.springframework.util.Assert;

import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.zylitics.wzgp.config.SharedDependencies;
import com.zylitics.wzgp.http.RequestGridCreate;
import com.zylitics.wzgp.resource.AbstractResource;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.grid.GridGenerator;
import com.zylitics.wzgp.resource.grid.GridStarter;
import com.zylitics.wzgp.resource.search.ResourceSearch;

public abstract class AbstractGridCreateHandler extends AbstractResource {

  private final SharedDependencies sharedDep;
  protected final RequestGridCreate request;
  private final ResourceExecutor executor;
  
  public AbstractGridCreateHandler(SharedDependencies sharedDep
      , RequestGridCreate request) {
    Assert.notNull(request, "SharedDependencies can't be null");
    this.sharedDep = sharedDep;
    Assert.notNull(request, "RequestGridCreate can't be null");
    this.request = request;
    
    this.executor = getExecutor();
  }
  
  private ResourceExecutor getExecutor() {
    return ResourceExecutor.Factory.getDefault().create(sharedDep
        , request.getBuildProperties());
  }
  
  protected ResourceSearch getSearch() {
    return ResourceSearch.Factory.getDefault().create(sharedDep
        , request.getResourceSearchParams()
        , executor);
  }
  
  protected GridGenerator getGridGenerator(Image sourceImage) {
    return new GridGenerator(sharedDep
        , request.getGridProperties()
        , sourceImage
        , executor);
  }
  
  protected GridStarter getGridStarter(Instance gridInstance) {
    return new GridStarter(sharedDep
        , request.getBuildProperties()
        , request.getGridProperties()
        , gridInstance
        , executor);
  }
}
