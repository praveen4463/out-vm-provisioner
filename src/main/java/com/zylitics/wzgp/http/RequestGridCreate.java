package com.zylitics.wzgp.http;

import java.util.HashMap;
import java.util.Map;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;

import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.util.Assert;
import org.springframework.validation.annotation.Validated;

import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.grid.GridProperty;
import com.zylitics.wzgp.resource.search.ResourceSearchParam;

/**
 * Parsed from a json request via {@link HttpMessageConverter} that use jackson.
 * Some components are not declared for validation cause they may not be given every time and should
 * be validated manually.
 * @author Praveen Tiwari
 *
 */
@Validated
public class RequestGridCreate {
  
  @Valid
  private BuildProperties buildProperties = new BuildProperties();
  
  private ResourceSearchParams resourceSearchParams = new ResourceSearchParams();
  
  @Valid
  private GridProperties gridProperties = new GridProperties();
  
  public BuildProperties getBuildProperties() {
    return buildProperties;
  }
  
  public ResourceSearchParams getResourceSearchParams() {
    return resourceSearchParams;
  }

  public GridProperties getGridProperties() {
    return gridProperties;
  }
  
  public static final class BuildProperties implements BuildProperty {
    
    @NotBlank
    private String buildId;

    public String getBuildId() {
      return buildId;
    }

    public void setBuildId(String buildId) {
      this.buildId = buildId;
    }
  }

  public static final class ResourceSearchParams implements ResourceSearchParam {
    
    private String os;
    private String browser;
    private boolean shots = false;
    
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
    
    /**
     * Validates the required search fields as per api documentation.
     * @throws IllegalArgumentException if validation fails. 
     */
    // Validate separately because this class may or may not be in the request, putting bean
    // validation annotations here will cause it to validate every time and request is bound.
    @Override
    public void validate() throws IllegalArgumentException {
      Assert.hasText(os, "'os' can't be empty.");
      Assert.hasText(browser, "'browser' can't be empty.");
    }

    @Override
    public String toString() {
      return "ResourceSearchParams [os=" + os + ", browser=" + browser + ", shots=" + shots + "]";
    }
  }
  
  public static final class GridProperties implements GridProperty {
    
    private String machineType;
    private String serviceAccount;
    private boolean preemptible = false;
    
    private final Map<String, String> customLabels = new HashMap<>();
    
    @NotEmpty
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
    public Map<String, String> getCustomLabels() {
      return customLabels;
    }
    
    @Override
    public Map<String, String> getMetadata() {
      return metadata;
    }
  }
}
