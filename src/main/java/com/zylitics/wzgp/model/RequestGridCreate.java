package com.zylitics.wzgp.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.zylitics.wzgp.controller.GridProperties;
import com.zylitics.wzgp.service.ResourceSearchParams;

/**
 * Parsed from a json request via Jackson. Make sure we keep the property names same as in json.
 * instance inner classes shouldn't be used in default configuration of Jackson, thus use static
 * classes.
 * Note: Currently there are no validations required. All fields are optional, whenever required
 * we can apply a mix of custom validation in setters including java ee's bean validation.
 * @author Praveen Tiwari
 *
 */
public class RequestGridCreate {
  
  private ResourceSearchParams resourceSearchParams;
  private GridProperties gridProperties;
  
  public ResourceSearchParams getResourceSearchParams() {
    return resourceSearchParams;
  }

  public GridProperties getGridProperties() {
    return gridProperties;
  }

  public static final class ResourceSearchParamsImpl implements ResourceSearchParams {
    
    private String os;
    private String browser;
    private boolean shots;
    
    @Override
    public String getOS() {
      return os;
    }
    
    public void setOS(String os) {
      this.os = os;
    }
    
    @Override
    public String getBrowser() {
      return browser;
    }
    
    public void setBrowser(String browser) {
      this.browser = browser;
    }
    
    @Override
    public boolean isShots() {
      return shots;
    }
    
    public void setShots(boolean shots) {
      this.shots = shots;
    }

    @Override
    public String toString() {
      return "ResourceSearchParams [os=" + os + ", browser=" + browser + ", shots=" + shots + "]";
    }
  }
  
  public static final class GridPropertiesImpl implements GridProperties {
    
    private String machineType;
    private String serviceAccount;
    private boolean preemptible;
    private final List<String> alternateZonesForReattempt = new ArrayList<>();
    private final List<String> tags = new ArrayList<>();
    private final Map<String, String> labels = new HashMap<>();
    private final Map<String, String> metadata = new HashMap<>();
    
    @Override
    public String getMachineType() {
      return machineType;
    }
    
    public void setMachineType(String machineType) {
      this.machineType = machineType;
    }
    
    @Override
    public String getServiceAccount() {
      return serviceAccount;
    }
    
    public void setServiceAccount(String serviceAccount) {
      this.serviceAccount = serviceAccount;
    }
    
    @Override
    public boolean isPreemptible() {
      return preemptible;
    }
    
    public void setPreemptible(boolean preemptible) {
      this.preemptible = preemptible;
    }
    
    @Override
    public List<String> getAlternateZonesForReattempt() {
      return alternateZonesForReattempt;
    }
    
    @Override
    public List<String> getTags() {
      return tags;
    }
    
    @Override
    public Map<String, String> getLabels() {
      return labels;
    }
    
    @Override
    public Map<String, String> getMetadata() {
      return metadata;
    }
  }
}
