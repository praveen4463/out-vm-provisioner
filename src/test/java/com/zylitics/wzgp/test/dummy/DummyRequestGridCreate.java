package com.zylitics.wzgp.test.dummy;

import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.zylitics.wzgp.http.RequestGridCreate;

public class DummyRequestGridCreate extends RequestGridCreate {

  private DummyBuildProperties buildProperties = new DummyBuildProperties();
  
  private DummyResourceSearchParams resourceSearchParams = new DummyResourceSearchParams();
  
  private DummyGridProperties gridProperties = new DummyGridProperties();
  
  @Override
  public DummyBuildProperties getBuildProperties() {
    return buildProperties;
  }
  
  @Override
  public DummyResourceSearchParams getResourceSearchParams() {
    return resourceSearchParams;
  }
  
  @Override
  public DummyGridProperties getGridProperties() {
    return gridProperties;
  }
  
  private static class DummyBuildProperties extends BuildProperties {
    
    private String buildId = "build-007";
    
    @Override
    public String getBuildId() {
      return buildId;
    }
    
    @Override
    public String toString() {
      return "BuildProperties [buildId=" + buildId + "]";
    }
  }
  
  private static class DummyResourceSearchParams extends ResourceSearchParams {
    
    private String os = "win7";
    private String browser = "chrome";
    private boolean shots = false;
    
    @Override
    public String getOS() {
      return os;
    }
    
    @Override
    public String getBrowser() {
      return browser;
    }
    
    @Override
    public boolean isShots() {
      return shots;
    }
    
    @Override
    public String toString() {
      return "ResourceSearchParams [os=" + os + ", browser=" + browser + ", shots=" + shots + "]";
    }
  }
  
  private static class DummyGridProperties extends GridProperties {
    
    @Override
    public String getMachineType() {
      return "n2-standard";
    }
    
    @Override
    public String getServiceAccount() {
      return "dummy-service-account@gcp.com";
    }
    
    @Override
    public boolean isPreemptible() {
      return false;
    }
    
    @Override
    public Map<String, String> getCustomLabels() {
      return ImmutableMap.of("is-production-instance", "false");
    }
    
    @Override
    public Map<String, String> getMetadata() {
      return ImmutableMap.of("screen", "1x1");
    }
  }
}
