package com.zylitics.wzgp.web;

import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;

import com.zylitics.wzgp.http.AbstractResponse;
import com.zylitics.wzgp.resource.SharedDependencies;

public abstract class AbstractGridHandler {

  protected final SharedDependencies sharedDep;
  
  public AbstractGridHandler(SharedDependencies sharedDep) {
    Assert.notNull(sharedDep, "SharedDependencies can't be null");
    this.sharedDep = sharedDep;
  }
  
  public abstract ResponseEntity<? extends AbstractResponse> handle() throws Exception;
}
