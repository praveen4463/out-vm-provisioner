package com.zylitics.wzgp.resource.grid;

import java.util.Map;

public interface GridProperty {

  String getMachineType();
  
  String getServiceAccount();
  
  boolean isPreemptible();
  
  Map<String, String> getCustomLabels();
  
  Map<String, String> getMetadata();
}
