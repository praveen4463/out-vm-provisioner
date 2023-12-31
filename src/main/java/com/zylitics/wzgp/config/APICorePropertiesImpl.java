package com.zylitics.wzgp.config;

import java.util.Map;
import java.util.Set;

import javax.annotation.concurrent.ThreadSafe;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.zylitics.wzgp.resource.APICoreProperties;

/**
 * <p><b>Should be accessed only through the interface {@link APICoreProperties}.</b></p>
 * Note: Configuration file is written using kebab-case, relaxed binding allows us to convert them
 * to java specific style, for example short-version gets parsed shortVersion.
 * Validation reference:
 * https://docs.spring.io/spring-boot/docs/2.1.6.RELEASE/reference/html/boot-features-external-config.html#boot-features-external-config-validation
 * All setters in this class allow only first time access by container, after that no values can
 * be mutated, getters of collections are also Immutable.
 * @author Praveen Tiwari
 *
 */
@ThreadSafe
@Component
@ConfigurationProperties(prefix="api-core")
@Validated
@SuppressWarnings("unused")
public class APICorePropertiesImpl implements APICoreProperties {
  
  // How properties are matched and accessed by the container?
  // the properties file in the classpath is read, thru relaxed binding properties are converted
  // into instance fields and the field name's get and set accessor are invoked. Thus the accessor
  // names should match with the properties. 
  @NotBlank
  private String resourceProjectId;
  
  @NotBlank
  private String sharedVpcProjectId;
  
  @NotBlank
  private String gceApiUrl;
  
  @Min(60000)
  private Long gceTimeoutMillis;
  
  @NotEmpty
  private Set<String> gceZonalReattemptErrors;
  
  @NotEmpty
  private Set<String> gceReattemptZones;
  
  // Instantiating here so that a setter isn't required.
  @Valid
  private final GridDefaults gridDefaults = new GridDefaults();
  
  @Override
  public String getResourceProjectId() {
    return resourceProjectId;
  }
  
  public void setResourceProjectId(String projectId) {
    if (this.resourceProjectId == null) {
      this.resourceProjectId = projectId;
    }
  }
  
  @Override
  public String getSharedVpcProjectId() {
    return sharedVpcProjectId;
  }
  
  public void setSharedVpcProjectId(String sharedVpcProjectId) {
    if (this.sharedVpcProjectId == null) {
      this.sharedVpcProjectId = sharedVpcProjectId;
    }
  }
  
  @Override
  public String getGceApiUrl() {
    return gceApiUrl;
  }
  
  public void setGceApiUrl(String gceApiUrl) {
    if (this.gceApiUrl == null) {
      this.gceApiUrl = gceApiUrl;
    }
  }
  
  @Override
  public long getGceTimeoutMillis() {
    return gceTimeoutMillis;
  }
  
  public void setGceTimeoutMillis(long gceTimeoutMillis) {
    if (this.gceTimeoutMillis == null) {
      this.gceTimeoutMillis = gceTimeoutMillis;
    }
  }
  
  @Override
  public Set<String> getGceZonalReattemptErrors() {
    return gceZonalReattemptErrors;
  }
  
  public void setGceZonalReattemptErrors(Set<String> gceZonalReattemptErrors) {
    if (this.gceZonalReattemptErrors == null) {
      this.gceZonalReattemptErrors = ImmutableSet.copyOf(gceZonalReattemptErrors);
    }
  }
  
  @Override
  public Set<String> getGceReattemptZones() {
    return gceReattemptZones;
  }
  
  public void setGceReattemptZones(Set<String> gceReattemptZones) {
    if (this.gceReattemptZones == null) {
      this.gceReattemptZones = ImmutableSet.copyOf(gceReattemptZones);
    }
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
  
  /**
   * <p><b>Should be accessed only through the interface {@link GridDefault}.</b></p>
   * @author Praveen Tiwari
   *
   */
  // Has to be public because we're not having set accessors in the interface.
  public static class GridDefaults implements GridDefault {
    @NotBlank
    private String machineType;
    
    @NotBlank
    private String serviceAccount;
    
    @NotEmpty
    private Set<String> tags;
    
    @NotEmpty
    private Map<String, String> labels;
    
    @NotEmpty
    private Map<String, String> metadata;
    
    @NotEmpty
    private Set<String> imageSpecificLabelsKey;
    
    @NotEmpty
    private Map<String, String> instanceSearchParams;
    
    @NotEmpty
    private Map<String, String> imageSearchParams;
    
    @Min(1)
    @Max(500)
    private Integer maxInstanceInSearch;
    
    @Override
    public String getMachineType() {
      return machineType;
    }
    
    public void setMachineType(String machineType) {
      if (this.machineType == null) {
        this.machineType = machineType;
      }
    }
    
    @Override
    public String getServiceAccount() {
      return serviceAccount;
    }
    
    public void setServiceAccount(String serviceAccount) {
      if (this.serviceAccount == null) {
        this.serviceAccount = serviceAccount;
      }
    }
    
    @Override
    public Set<String> getTags() {
      return tags;
    }
    
    public void setTags(Set<String> tags) {
      if (this.tags == null) {
        this.tags = ImmutableSet.copyOf(tags);
      }
    }
    
    @Override
    public Map<String, String> getLabels() {
      return labels;
    }
    
    public void setLabels(Map<String, String> labels) {
      if (this.labels == null) {
        this.labels = ImmutableMap.copyOf(labels);
      }
    }
    
    @Override
    public Map<String, String> getMetadata() {
      return metadata;
    }
    
    public void setMetadata(Map<String, String> metadata) {
      if (this.metadata == null) {
        this.metadata = ImmutableMap.copyOf(metadata);
      }
    }
    
    @Override
    public Set<String> getImageSpecificLabelsKey() {
      return imageSpecificLabelsKey;
    }
    
    public void setImageSpecificLabelsKey(Set<String> imageSpecificLabelsKey) {
      if (this.imageSpecificLabelsKey == null) {
        this.imageSpecificLabelsKey = ImmutableSet.copyOf(imageSpecificLabelsKey);
      }
    }
    
    @Override
    public Map<String, String> getInstanceSearchParams() {
      return instanceSearchParams;
    }
    
    public void setInstanceSearchParams(Map<String, String> instanceSearchParams) {
      if (this.instanceSearchParams == null) {
        this.instanceSearchParams = ImmutableMap.copyOf(instanceSearchParams);
      }
    }
    
    @Override
    public Map<String, String> getImageSearchParams() {
      return imageSearchParams;
    }
    
    public void setImageSearchParams(Map<String, String> imageSearchParams) {
      if (this.imageSearchParams == null) {
        this.imageSearchParams = ImmutableMap.copyOf(imageSearchParams);
      }
    }
  
    @Override
    public int getMaxInstanceInSearch() {
      return maxInstanceInSearch;
    }
  
    public void setMaxInstanceInSearch(int maxInstanceInSearch) {
      if (this.maxInstanceInSearch == null) {
        this.maxInstanceInSearch = maxInstanceInSearch;
      }
    }
  }
}
