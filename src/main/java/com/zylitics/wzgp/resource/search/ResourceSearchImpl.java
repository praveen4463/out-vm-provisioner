package com.zylitics.wzgp.resource.search;

import static com.zylitics.wzgp.resource.search.FilterBuilder.ConditionalExpr.AND;
import static com.zylitics.wzgp.resource.search.FilterBuilder.ConditionalExpr.OR;
import static com.zylitics.wzgp.resource.search.FilterBuilder.Operator.EQ;

import java.util.List;
import java.util.Optional;

import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.zylitics.wzgp.resource.util.ComputeCalls;

public class ResourceSearchImpl implements ResourceSearch {
  
  private final ResourceSearchParam searchParam;
  protected final ComputeCalls computeCalls;

  private ResourceSearchImpl(ResourceSearchParam searchParam, ComputeCalls computeCalls) {
    this.searchParam = searchParam;
    this.computeCalls = computeCalls;
  }
  
  @Override
  public Optional<Image> searchImage() throws Exception {
    String filter = buildCommonFilter(searchParam);
    List<Image> images = computeCalls.listImages(filter, 1L);
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
    
    List<Instance> instances = computeCalls.listInstances(filter, 1L);
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
    public ResourceSearch create(ResourceSearchParam searchParam, ComputeCalls computeCalls) {
      return new ResourceSearchImpl(searchParam, computeCalls);
    }
  }
}
