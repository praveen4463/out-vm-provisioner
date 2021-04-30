package com.zylitics.wzgp.resource.grid;

import java.util.Map;
import java.util.Set;

/**
 * All implementations must be strictly immutable for all accesses except spring container's.
 * @author Praveen Tiwari
 *
 */
public interface GridProperty {

  String getMachineType();
  
  String getServiceAccount();
  
  Boolean isPreemptible();
  
  Boolean isCreateExternalIP();
  
  Map<String, String> getCustomLabels();
  
  Map<String, String> getMetadata();
  
  Set<String> getNetworkTags();
}
