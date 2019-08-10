package com.zylitics.wzgp.controller;

import java.util.List;
import java.util.Map;

public interface GridProperties {

  String getMachineType();
  
  String getServiceAccount();
  
  boolean isPreemptible();
  
  List<String> getAlternateZonesForReattempt();
  
  List<String> getTags();
  
  Map<String, String> getLabels();
  
  Map<String, String> getMetadata();
}
