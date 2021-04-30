package com.zylitics.wzgp.resource.search;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import com.zylitics.wzgp.model.InstanceStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.compute.ComputeService;

@Service
public class ResourceSearchImpl implements ResourceSearch {
  
  private static final FilterBuilder.ConditionalExpr AND = FilterBuilder.ConditionalExpr.AND;
  private static final FilterBuilder.ConditionalExpr OR = FilterBuilder.ConditionalExpr.OR;
  
  private final APICoreProperties apiCoreProps;
  private final ComputeService computeServ;
  private final Random random;

  @Autowired
  ResourceSearchImpl(APICoreProperties apiCoreProps, ComputeService computeServ) {
    this.apiCoreProps = apiCoreProps;
    this.computeServ = computeServ;
    random = new Random();
  }
  
  @Override
  public Optional<Image> searchImage(ResourceSearchParam searchParam, BuildProperty buildProp)
      throws Exception {
    searchParam.validate();
    
    List<Image> images = computeServ.listImages(buildImageFilters(searchParam), 1L, buildProp);
    return images != null && images.size() > 0
        ? Optional.of(images.get(0))
        : Optional.empty();
  }
  
  @Override
  public Optional<Instance> searchInstance(ResourceSearchParam searchParam,
                                           String zone,
                                           InstanceStatus instanceStatus,
                                           BuildProperty buildProp) throws Exception {
    searchParam.validate();
    
    List<Instance> instances = computeServ.listInstances(
        buildInstanceFilters(searchParam, instanceStatus),
        apiCoreProps.getGridDefault().getMaxInstanceInSearch(),
        zone,
        buildProp);
    return instances != null && instances.size() > 0
        ? Optional.of(instances.get(random.nextInt(instances.size())))
        : Optional.empty();
  }
  
  private String buildInstanceFilters(ResourceSearchParam searchParam,
                                      InstanceStatus instanceStatus) {
    Map<String, String> mergedSearchParams =
        new HashMap<>(apiCoreProps.getGridDefault().getInstanceSearchParams());
    mergedSearchParams.put("status", instanceStatus.toString());
    
    if (searchParam.getCustomInstanceSearchParams() != null) {
      mergedSearchParams.putAll(searchParam.getCustomInstanceSearchParams());
    }
    
    FilterBuilder filterBuilder = new FilterBuilder();
    mergedSearchParams.forEach((k, v) ->
        filterBuilder.addCondition(k, v).addConditionalExpr(AND));
    return filterBuilder.build() + buildFromRequest(searchParam);
  }
  
  private String buildImageFilters(ResourceSearchParam searchParam) {
    Map<String, String> mergedSearchParams =
        new HashMap<>(apiCoreProps.getGridDefault().getImageSearchParams());
    
    if (searchParam.getCustomImageSearchParams() != null) {
      mergedSearchParams.putAll(searchParam.getCustomImageSearchParams());
    }
  
    FilterBuilder filterBuilder = new FilterBuilder();
    mergedSearchParams.forEach((k, v) ->
        filterBuilder.addCondition(k, v).addConditionalExpr(AND));
    return filterBuilder.build() + buildFromRequest(searchParam);
  }
  
  private String buildFromRequest(ResourceSearchParam searchParam) {
    String browser = searchParam.getBrowser();
    return new FilterBuilder()
        .addCondition("labels.os", searchParam.getOS())
        .addConditionalExpr(AND)
        .addCondition("labels.browser1", browser)
        .addConditionalExpr(OR)
        .addCondition("labels.browser2", browser)
        .addConditionalExpr(OR)
        .addCondition("labels.browser3", browser)
        .addConditionalExpr(OR)
        .addCondition("labels.browser4", browser)
        .addConditionalExpr(OR)
        .addCondition("labels.browser5", browser)
        .addConditionalExpr(OR)
        .addCondition("labels.browser6", browser)
        .addConditionalExpr(AND)
        .addCondition("labels.shots", String.valueOf(searchParam.isShots()))
        .build();
  }
  
  @SuppressWarnings("unused")
  private static final class FilterBuilder {
    
    private static final String SEARCH_TMPL = "(L = R)";
    
    enum ConditionalExpr {
      AND,
      OR
    }
    
    private final StringBuilder builder = new StringBuilder();
    
    private FilterBuilder addCondition(String key, String value) {
      Assert.hasText(key, "key can't be empty");
      Assert.notNull(value, "value can't be null");
      
      builder.append(SEARCH_TMPL
          .replace("L", key)
          .replace("R", "\"" + value + "\""));
      return this;
    }
    
    private FilterBuilder addConditionalExpr(ConditionalExpr conditionalExpr) {
      builder.append(" ")
          .append(conditionalExpr.name())
          .append(" ");
      return this;
    }
    
    private String build() {
      return builder.toString();
    }
  }
}
