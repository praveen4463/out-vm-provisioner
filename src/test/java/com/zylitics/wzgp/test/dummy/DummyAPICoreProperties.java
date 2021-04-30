package com.zylitics.wzgp.test.dummy;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.zylitics.wzgp.resource.APICoreProperties;

public class DummyAPICoreProperties implements APICoreProperties {

  private DummyGridDefaults gridDefaults = new DummyGridDefaults();
  
  @Override
  public String getResourceProjectId() {
    return "zl-dummy-proj";
  }
  
  @Override
  public String getSharedVpcProjectId() {
    return "zl-dummy-shared-proj";
  }
  
  @Override
  public String getGceApiUrl() {
    return "https://www.googleapis.com/compute/v1/projects";
  }
  
  @Override
  public long getGceTimeoutMillis() {
    return 100000;
  }
  
  @Override
  public Set<String> getGceZonalReattemptErrors() {
    return ImmutableSet.of("ZONE_RESOURCE_POOL_EXHAUSTED", "QUOTA_EXCEEDED");
  }
  
  @Override
  public Set<String> getGceReattemptZones() {
    return ImmutableSet.of("zone-a", "zone-b", "zone-c", "zone-d", "zone-e", "zone-f");
  }
  
  @Override
  public GridDefault getGridDefault() {
    return gridDefaults;
  }
  
  private static class DummyGridDefaults implements GridDefault {
    
    @Override
    public String getMachineType() {
      return "n1-standard";
    }
    
    @Override
    public String getServiceAccount() {
      return "dummy-service-account@gcp.com";
    }
    
    @Override
    public Set<String> getTags() {
      return ImmutableSet.of("tag-1", "tag-2");
    }
    
    @Override
    public Map<String, String> getLabels() {
      return ImmutableMap.of("zl-grid", "true", "is-deleting", "false"
          , "is-production-instance", "true");
    }
    
    @Override
    public Map<String, String> getMetadata() {
      return ImmutableMap.of("default-timezone", "utc");
    }
    
    @Override
    public Set<String> getImageSpecificLabelsKey() {
      return ImmutableSet.of("test-vms");
    }
    
    @Override
    public Map<String, String> getInstanceSearchParams() {
      return ImmutableMap.of(
            "labels.platform", "windows",
            "status", "TERMINATED"
          );
    }
    
    @Override
    public Map<String, String> getImageSearchParams() {
      return ImmutableMap.of("labels.platform", "windows");
    }
  
    @Override
    public int getMaxInstanceInSearch() {
      return 10;
    }
  }
}
