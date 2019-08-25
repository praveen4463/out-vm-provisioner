package com.zylitics.wzgp.resource.search;

import static com.zylitics.wzgp.resource.search.FilterBuilder.ConditionalExpr.AND;
import static com.zylitics.wzgp.resource.search.FilterBuilder.ConditionalExpr.OR;
import static com.zylitics.wzgp.resource.search.FilterBuilder.Operator.EQ;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.service.ComputeService;

public class ResourceSearchImpl implements ResourceSearch {
  
  private final ComputeService computeServ;
  private final ResourceSearchParam searchParam;
  
  private @Nullable BuildProperty buildProp;

  private ResourceSearchImpl(ComputeService computeServ, ResourceSearchParam searchParam) {
    this.computeServ = computeServ;
    this.searchParam = searchParam;
  }
  
  @Override
  public void setBuildProperty(BuildProperty buildProp) {
    this.buildProp = buildProp;
  }
  
  @Override
  public Optional<Image> searchImage() throws Exception {
    String filter = buildCommonFilter(searchParam);
    List<Image> images = computeServ.listImages(filter, 1L, buildProp);
    return images != null && images.size() > 0
        ? Optional.of(images.get(0))
        : Optional.empty();
  }
  
  @Override
  public Optional<Instance> searchStoppedInstance(String zone) throws Exception {
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
    
    List<Instance> instances = computeServ.listInstances(filter, 1L, zone, buildProp);
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
    public ResourceSearch create(ComputeService computeCalls, ResourceSearchParam searchParam) {
      return new ResourceSearchImpl(computeCalls, searchParam);
    }
  }
}
