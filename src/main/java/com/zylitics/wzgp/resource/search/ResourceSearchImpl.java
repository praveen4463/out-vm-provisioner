package com.zylitics.wzgp.resource.search;

import static com.zylitics.wzgp.resource.search.FilterBuilder.ConditionalExpr.AND;
import static com.zylitics.wzgp.resource.search.FilterBuilder.ConditionalExpr.OR;
import static com.zylitics.wzgp.resource.search.FilterBuilder.Operator.EQ;

import java.util.List;
import java.util.Optional;

import com.google.api.services.compute.Compute.Images;
import com.google.api.services.compute.Compute.Instances;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.ImageList;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceList;
import com.zylitics.wzgp.config.SharedDependencies;
import com.zylitics.wzgp.resource.AbstractResource;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;

public class ResourceSearchImpl extends AbstractResource implements ResourceSearch {
  
  private final SharedDependencies sharedDep;
  private final ResourceSearchParam searchParam;
  private final String project;
  private final ResourceExecutor executor;

  private ResourceSearchImpl(SharedDependencies sharedDep
      , ResourceSearchParam searchParam
      , ResourceExecutor executor) {
    this.sharedDep = sharedDep;
    this.searchParam = searchParam;
    this.executor = executor;
    this.project = sharedDep.apiCoreProps().getProjectId();
  }
  
  @Override
  public Optional<Image> searchImage() throws Exception {
    String filter = buildCommonFilter(searchParam);
    Images.List listBuilder = sharedDep.compute().images().list(project);
    listBuilder.setMaxResults(1L);
    listBuilder.setFilter(filter);
    ImageList list = executor.executeWithReattempt(listBuilder); 
    List<Image> images = list.getItems();
    return images != null && images.size() > 0
        ? Optional.of(images.get(0))
        : Optional.empty();
  }
  
  @Override
  public Optional<Instance> searchStoppedInstance() throws Exception {
    FilterBuilder filterBuilder = new FilterBuilder();
    String filter = filterBuilder
        .addCondition("status", "TERMINATED", String.class, EQ)
        .addConditionalExpr(AND)
        .addCondition("labels.is-production-instance", "true", String.class, EQ)
        .addConditionalExpr(AND)
        .addCondition("labels.zl-selenium-grid", "true", String.class, EQ)
        .addConditionalExpr(AND)
        .addCondition("labels.locked-by-build", "NONE", String.class, EQ)
        .addConditionalExpr(AND)
        .addCondition("labels.is-deleting", "false", String.class, EQ)
        .build();
    filter += filterBuilder.addConditionalExpr(AND).build() + buildCommonFilter(searchParam);
    
    Instances.List listBuilder = sharedDep.compute().instances().list(project, sharedDep.zone());
    listBuilder.setMaxResults(1L);
    listBuilder.setFilter(filter);
    InstanceList list = executor.executeWithReattempt(listBuilder);
    List<Instance> instances = list.getItems();
    return instances != null && instances.size() > 0
        ? Optional.of(instances.get(0))
        : Optional.empty();
  }
  
  private String buildCommonFilter(ResourceSearchParam searchParam) {
    String browser = searchParam.getBrowser();
    return new FilterBuilder()
        .addCondition("labels.platform", "windows", String.class, EQ)
        .addConditionalExpr(AND)
        .addCondition("labels.os", searchParam.getOS(), String.class, EQ)
        .addConditionalExpr(AND)
        .addCondition("labels.browser1", browser, String.class, EQ)
        .addConditionalExpr(OR)
        .addCondition("labels.browser2", browser, String.class, EQ)
        .addConditionalExpr(OR)
        .addCondition("labels.browser3", browser, String.class, EQ)
        .addConditionalExpr(OR)
        .addCondition("labels.browser4", browser, String.class, EQ)
        .addConditionalExpr(OR)
        .addCondition("labels.browser5", browser, String.class, EQ)
        .addConditionalExpr(OR)
        .addCondition("labels.browser6", browser, String.class, EQ)
        .addConditionalExpr(AND)
        .addCondition("labels.shots", searchParam.isShots(), boolean.class, EQ)
        .build();
  }
  
  public static class Factory implements ResourceSearch.Factory {
    
    @Override
    public ResourceSearch create(SharedDependencies sharedDep
        , ResourceSearchParam searchParam,
        ResourceExecutor executor) {
      return new ResourceSearchImpl(sharedDep, searchParam, executor);
    }
  }
}
