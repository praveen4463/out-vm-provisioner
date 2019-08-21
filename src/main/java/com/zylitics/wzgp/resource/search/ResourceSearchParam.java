package com.zylitics.wzgp.resource.search;

public interface ResourceSearchParam {

  String getOS();
  
  String getBrowser();
  
  boolean isShots();
  
  void validate();
}
