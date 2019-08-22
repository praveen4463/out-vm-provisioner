package com.zylitics.wzgp.config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import com.zylitics.wzgp.resource.APICoreProperties;

/**
 * Note: Configuration file is written using kebab-case, relaxed binding allows us to convert them
 * to java specific style, for example short-version gets parsed shortVersion.
 * Note on injection: Thru type prediction of spring framework any place autowiring on the type of
 * this class (APICoreProperties) should get the object of this class without registering a bean.
 * Validation reference:
 * https://docs.spring.io/spring-boot/docs/2.1.6.RELEASE/reference/html/boot-features-external-config.html#boot-features-external-config-validation
 * @author Praveen Tiwari
 *
 */
@Component
@ConfigurationProperties(prefix="api-core")
@Validated
public class APICorePropertiesImpl implements APICoreProperties {
  
  @NotBlank
  private String projectId;
  
  @NotBlank
  private String shortVersion;
  
  @NotBlank
  private String gceApiUrl;
  
  @Min(60000)
  private long gceTimeoutMillis;
  
  @NotEmpty
  private final Set<String> gceZonalReattemptErrors = new HashSet<>();
  
  @NotEmpty
  private final Set<String> gceReattemptZones = new HashSet<>();
  
  // Instantiating here so that a setter isn't required.
  @Valid
  private GridDefaults gridDefaults = new GridDefaults();
  
  @Override
  public String getProjectId() {
    return projectId;
  }
  
  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }
  
  @Override
  public String getShortVersion() {
    return shortVersion;
  }
  
  public void setShortVersion(String shortVersion) {
    this.shortVersion = shortVersion;
  }
  
  @Override
  public String getGceApiUrl() {
    return gceApiUrl;
  }
  
  public void setGceApiUrl(String gceApiUrl) {
    this.gceApiUrl = gceApiUrl;
  }
  
  @Override
  public long getGceTimeoutMillis() {
    return gceTimeoutMillis;
  }

  public void setGceTimeoutMillis(long gceTimeoutMillis) {
    this.gceTimeoutMillis = gceTimeoutMillis;
  }
  
  @Override
  public Set<String> getGceZonalReattemptErrors() {
    return gceZonalReattemptErrors;
  }
  
  @Override
  public Set<String> getGceReattemptZones() {
    return gceReattemptZones;
  }

  /**
   * Accessed by container to set GridDefaults's members.
   */
  public GridDefaults getGridDefaults() {
    return gridDefaults;
  }
  
  /**
   * Accessed through interface.
   */
  @Override
  public GridDefault getGridDefault() {
    return gridDefaults;
  }
  
  public static final class GridDefaults implements GridDefault {
    @NotBlank
    private String machineType;
    
    @NotBlank
    private String network;
    
    @NotBlank
    private String serviceAccount;
    
    @NotEmpty
    private final Set<String> tags = new HashSet<>();
    
    @NotEmpty
    private final Map<String, String> labels = new HashMap<>();
    
    @NotEmpty
    private final Map<String, String> metadata = new HashMap<>();
    
    @NotEmpty
    private final Set<String> imageSpecificLabelsKey = new HashSet<>();
    
    @Override
    public String getMachineType() {
      return machineType;
    }
    
    public void setMachineType(String machineType) {
      this.machineType = machineType;
    }
    
    @Override
    public String getNetwork() {
      return network;
    }
    
    public void setNetwork(String network) {
      this.network = network;
    }
    
    @Override
    public String getServiceAccount() {
      return serviceAccount;
    }
    
    public void setServiceAccount(String serviceAccount) {
      this.serviceAccount = serviceAccount;
    }
    
    @Override
    public Set<String> getTags() {
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
    
    @Override
    public Set<String> getImageSpecificLabelsKey() {
      return imageSpecificLabelsKey;
    }
  }
}
