package com.zylitics.wzgp.service;

import static com.zylitics.wzgp.service.GCPFilterBuilder.ConditionalExpr.AND;
import static com.zylitics.wzgp.service.GCPFilterBuilder.ConditionalExpr.OR;
import static com.zylitics.wzgp.service.GCPFilterBuilder.Operator.EQ;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.zylitics.wzgp.config.APICoreProperties;
import com.zylitics.wzgp.config.GCPCompute;
import com.zylitics.wzgp.model.RequestGridCreate;
import com.zylitics.wzgp.service.GCPFilterBuilder.Operator;

@Service
public class GCPResourceSearchImpl implements GCPResourceSearch {
  
  private static final Logger LOG = LoggerFactory.getLogger(GCPResourceSearchImpl.class);
  private static final short MAX_RESOURCES_TO_FETCH = 1;
  
  private final GCPCompute compute;
  private final APICoreProperties apiCoreProps;

  @Autowired
  public GCPResourceSearchImpl(GCPCompute compute, APICoreProperties apiCoreProps) {
    this.compute = compute;
    this.apiCoreProps = apiCoreProps;
  }
  
  @Override
  public Optional<Image> searchImage(ResourceSearchParams searchParams) {
    String filter = buildCommonFilter(searchParams);
    return null;
  }
  
  @Override
  public Optional<Instance> searchStoppedInstance(ResourceSearchParams searchParams) {
    String filter = buildCommonFilter(searchParams);
    filter += new GCPFilterBuilder()
        .addConditionalExpr(AND)
        .addCondition("labels.locked-by-requestId", "NONE", String.class, EQ)
        .addConditionalExpr(AND)
        .addCondition("status", "STOPPED", String.class, EQ)
        .build();
    
  }
  
  private String buildCommonFilter(ResourceSearchParams searchParams) {
    return new GCPFilterBuilder()
        .addCondition("labels.os", searchParams.getOS(), String.class, EQ)
        .addConditionalExpr(AND)
        .addCondition("labels.browser1", searchParams.getBrowser(), String.class, EQ)
        .addConditionalExpr(OR)
        .addCondition("labels.browser2", searchParams.getBrowser(), String.class, EQ)
        .addConditionalExpr(OR)
        .addCondition("labels.browser3", searchParams.getBrowser(), String.class, EQ)
        .addConditionalExpr(OR)
        .addCondition("labels.browser4", searchParams.getBrowser(), String.class, EQ)
        .addConditionalExpr(OR)
        .addCondition("labels.browser5", searchParams.getBrowser(), String.class, EQ)
        .addConditionalExpr(OR)
        .addCondition("labels.browser6", searchParams.getBrowser(), String.class, EQ)
        .addConditionalExpr(AND)
        .addCondition("labels.shots", searchParams.isShots(), boolean.class, EQ)
        .build();
  }
}
