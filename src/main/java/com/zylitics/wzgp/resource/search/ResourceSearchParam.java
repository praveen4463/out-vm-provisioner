package com.zylitics.wzgp.resource.search;

import org.springframework.util.Assert;

public interface ResourceSearchParam {

  String getOS();
  
  String getBrowser();
  
  boolean isShots();
  
  /**
   * Validates the required search fields as per api documentation.
   * @throws IllegalArgumentException if validation fails. 
   */
  default void validate() throws IllegalArgumentException {
    // Validate separately because search params may or may not be in the request, putting bean
    // validation annotations on fields will cause it to validate every time the request is bound.
    Assert.hasText(getOS(), "'os' can't be empty.");
    Assert.hasText(getBrowser(), "'browser' can't be empty.");
  }
}
