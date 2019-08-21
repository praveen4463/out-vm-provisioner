package com.zylitics.wzgp.resource;

import java.util.Map;
import java.util.Set;

public interface APICoreProperties {

  String getProjectId();
  
  String getShortVersion();
  
  String getGcpApiUrl();
  
  long getGceTimeoutMillis();
  
  Set<String> getGceZonalReattemptErrors();
  
  Set<String> getGceReattemptZones();
  
  GridDefault getGridDefault();
  
  interface GridDefault {
    
    String getMachineType();
    
    String getNetwork();
    
    String getServiceAccount();
    
    Set<String> getTags();
    
    Map<String, String> getLabels();
    
    Map<String, String> getMetadata();
    
    Set<String> getImageSpecificLabelsKey();
  }
}
