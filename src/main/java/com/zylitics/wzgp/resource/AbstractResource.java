package com.zylitics.wzgp.resource;

import com.google.api.client.util.Strings;
import com.google.api.services.compute.model.Operation;

public abstract class AbstractResource {
  
  /**
   * Add details to exception messages to make them easy for debugging.
   * @return Details such as build information.
   */
  protected String addToException(BuildProperty buildProp) {
    StringBuilder sb = new StringBuilder();
    sb.append(" Build details: ");
    sb.append("buildId");
    sb.append(buildProp);
    
    return sb.toString();
  }
  
  protected boolean isOperationSuccess(Operation operation) {
    if (operation.getStatus().equals("DONE")
        && 
        (operation.getError() == null 
        || operation.getError().getErrors() == null
        || operation.getError().getErrors().size() == 0)) {
      return true;
    }
    return false;
  }
  
  protected String getResourceNameFromUrl(String resourceAsUrl) {
    if (Strings.isNullOrEmpty(resourceAsUrl)) {
      throw new RuntimeException("The resource url can't be empty.");
    }
    String[] bits = resourceAsUrl.split("/");
    return bits[bits.length - 1];
  }
}
