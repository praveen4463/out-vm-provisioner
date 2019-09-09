package com.zylitics.wzgp.resource.search;

import static com.zylitics.wzgp.resource.search.FilterBuilder.ConditionalExpr.AND;
import static com.zylitics.wzgp.resource.search.FilterBuilder.ConditionalExpr.OR;
import static com.zylitics.wzgp.resource.search.FilterBuilder.Operator.EQ;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.service.ComputeService;

public class ResourceSearchImpl implements ResourceSearch {
  
  private final APICoreProperties apiCoreProps;
  private final ComputeService computeServ;
  private final ResourceSearchParam searchParam;
  
  private @Nullable BuildProperty buildProp;

  private ResourceSearchImpl(APICoreProperties apiCoreProps, ComputeService computeServ
      , ResourceSearchParam searchParam) {
    searchParam.validate();  // validate that we got valid search params
    
    this.apiCoreProps = apiCoreProps;
    this.computeServ = computeServ;
    this.searchParam = searchParam;
  }
  
  @Override
  public void setBuildProperty(BuildProperty buildProp) {
    this.buildProp = buildProp;
  }
  
  @Override
  public Optional<Image> searchImage() throws Exception {
    List<Image> images = computeServ.listImages(buildImageFilters(), 1L, buildProp);
    return images != null && images.size() > 0
        ? Optional.of(images.get(0))
        : Optional.empty();
  }
  
  @Override
  public Optional<Instance> searchStoppedInstance(String zone) throws Exception {
    List<Instance> instances = computeServ.listInstances(buildInstanceFilters(), 1L, zone
        , buildProp);
    return instances != null && instances.size() > 0
        ? Optional.of(instances.get(0))
        : Optional.empty();
  }
  
  private String buildInstanceFilters() {
    Map<String, String> mergedSearchParams =
        new HashMap<>(apiCoreProps.getGridDefault().getInstanceSearchParams());
    
    if (searchParam.getCustomInstanceSearchParams() != null) {
      mergedSearchParams.putAll(searchParam.getCustomInstanceSearchParams());
    }
    
    FilterBuilder filterBuilder = new FilterBuilder();
    mergedSearchParams.entrySet().forEach(entry -> {
      filterBuilder.addCondition(entry.getKey(), entry.getValue(), EQ)
          .addConditionalExpr(AND);
    });
    return filterBuilder.build() + buildFromRequest();
  }
  
  private String buildImageFilters() {
    Map<String, String> mergedSearchParams =
        new HashMap<>(apiCoreProps.getGridDefault().getImageSearchParams());
    
    if (searchParam.getCustomImageSearchParams() != null) {
      mergedSearchParams.putAll(searchParam.getCustomImageSearchParams());
    }
    
    FilterBuilder filterBuilder = new FilterBuilder();
    mergedSearchParams.entrySet().forEach(entry -> {
      filterBuilder.addCondition(entry.getKey(), entry.getValue(), EQ)
          .addConditionalExpr(AND);
    });
    return filterBuilder.build() + buildFromRequest();
  }
  
  private String buildFromRequest() {
    String browser = searchParam.getBrowser();
    return new FilterBuilder()
        .addCondition("labels.os", searchParam.getOS(), EQ)
        .addConditionalExpr(AND)
        .addCondition("labels.browser1", browser, EQ)
        .addConditionalExpr(OR)
        .addCondition("labels.browser2", browser, EQ)
        .addConditionalExpr(OR)
        .addCondition("labels.browser3", browser, EQ)
        .addConditionalExpr(OR)
        .addCondition("labels.browser4", browser, EQ)
        .addConditionalExpr(OR)
        .addCondition("labels.browser5", browser, EQ)
        .addConditionalExpr(OR)
        .addCondition("labels.browser6", browser, EQ)
        .addConditionalExpr(AND)
        .addCondition("labels.shots", String.valueOf(searchParam.isShots()), EQ)
        .build();
  }
  
  public static class Factory implements ResourceSearch.Factory {
    
    @Override
    public ResourceSearch create(APICoreProperties apiCoreProps, ComputeService computeCalls
        , ResourceSearchParam searchParam) {
      return new ResourceSearchImpl(apiCoreProps, computeCalls, searchParam);
    }
  }
}
