package com.zylitics.wzgp.resource;

import java.util.Map;
import java.util.Set;

/**
 * All implementations must be strictly immutable for all accesses except spring container's.
 * @author Praveen Tiwari
 *
 */
public interface APICoreProperties {

  String getResourceProjectId();
  
  String getSharedVpcProjectId();
  
  String getGceApiUrl();
  
  long getGceTimeoutMillis();
  
  Set<String> getGceZonalReattemptErrors();
  
  Set<String> getGceReattemptZones();
  
  GridDefault getGridDefault();
  
  interface GridDefault {
    
    String getMachineType();
    
    String getServiceAccount();
    
    Set<String> getTags();
    
    Map<String, String> getLabels();
    
    Map<String, String> getMetadata();
    
    Set<String> getImageSpecificLabelsKey();
    
    Map<String, String> getInstanceSearchParams();
    
    Map<String, String> getImageSearchParams();
    
    int getMaxInstanceInSearch();
  }
}
