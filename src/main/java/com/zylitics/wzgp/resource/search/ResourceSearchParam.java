package com.zylitics.wzgp.resource.search;

import java.util.Map;

import org.springframework.util.Assert;

/**
 * All implementations must be strictly immutable for all accesses except spring container's.
 * @author Praveen Tiwari
 *
 */
public interface ResourceSearchParam {

  String getOS();
  
  String getBrowser();
  
  Boolean getShots();
  
  Map<String, String> getCustomInstanceSearchParams();
  
  Map<String, String> getCustomImageSearchParams();
  
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
