package com.zylitics.wzgp.http;

import java.util.Map;
import java.util.Set;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;

import com.google.common.collect.ImmutableSet;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.validation.annotation.Validated;

import com.google.common.collect.ImmutableMap;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.grid.GridProperty;
import com.zylitics.wzgp.resource.search.ResourceSearchParam;

/**
 * Parsed from a json request via {@link HttpMessageConverter} that use jackson.
 * Some components are not declared for validation cause they may not be given every time and should
 * be validated manually.
 * All setters in this class allow only first time access by container, after that no values can
 * be mutated, getters of collections are also Immutable.
 * @author Praveen Tiwari
 *
 */
@Validated
public class RequestGridCreate {
  
  @Valid
  private final BuildProperties buildProperties = new BuildProperties();
  
  private final ResourceSearchParams resourceSearchParams = new ResourceSearchParams();
  
  @Valid
  private final GridProperties gridProperties = new GridProperties();
  
  public BuildProperties getBuildProperties() {
    return buildProperties;
  }
  
  public ResourceSearchParams getResourceSearchParams() {
    return resourceSearchParams;
  }

  public GridProperties getGridProperties() {
    return gridProperties;
  }
  
  /**
   * <p><b>Should be accessed only through the interface {@link BuildProperty}.</b></p>
   * @author Praveen Tiwari
   *
   */
  public static class BuildProperties implements BuildProperty {
    
    @NotBlank
    private String buildId;

    public String getBuildId() {
      return buildId;
    }

    public void setBuildId(String buildId) {
      if (this.buildId == null) {
        this.buildId = buildId;
      }
    }

    @Override
    public String toString() {
      return "BuildProperties [buildId=" + buildId + "]";
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((buildId == null) ? 0 : buildId.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      BuildProperties other = (BuildProperties) obj;
      if (buildId == null) {
        return other.buildId == null;
      } else return buildId.equals(other.buildId);
    }
  }

  /**
   * <p><b>Should be accessed only through the interface {@link ResourceSearchParam}.</b></p>
   * @author Praveen Tiwari
   *
   */
  public static class ResourceSearchParams implements ResourceSearchParam {
    
    private String os;
    private String browser;
    private Boolean shots;
    private Map<String, String> customInstanceSearchParams;
    private Map<String, String> customImageSearchParams;
    
    @Override
    public String getOS() {
      return os;
    }
    
    public void setOS(String os) {
      if (this.os == null) {
        this.os = os;
      }
    }
    
    @Override
    public String getBrowser() {
      return browser;
    }
    
    public void setBrowser(String browser) {
      if (this.browser == null) {
        this.browser = browser;
      }
    }
    
    @Override
    public Boolean isShots() {
      return shots != null && shots;
    }
    
    public void setShots(Boolean shots) {
      if (this.shots == null) {
        this.shots = shots;
      }
    }
    
    @Override
    public Map<String, String> getCustomInstanceSearchParams() {
      return customInstanceSearchParams;
    }

    public void setCustomInstanceSearchParams(Map<String, String> customInstanceSearchParams) {
      if (this.customInstanceSearchParams == null && customInstanceSearchParams != null) {
        this.customInstanceSearchParams = ImmutableMap.copyOf(customInstanceSearchParams);
      }
    }

    @Override
    public Map<String, String> getCustomImageSearchParams() {
      return customImageSearchParams;
    }
    
    @SuppressWarnings("unused")
    public void setCustomImageSearchParams(Map<String, String> customImageSearchParams) {
      if (this.customImageSearchParams == null && customImageSearchParams != null) {
        this.customImageSearchParams = ImmutableMap.copyOf(customImageSearchParams);
      }
    }

    @Override
    public String toString() {
      return "ResourceSearchParams [os=" + os + ", browser=" + browser + ", shots=" + shots
          + ", customInstanceSearchParams=" + customInstanceSearchParams
          + ", customImageSearchParams=" + customImageSearchParams + "]";
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((browser == null) ? 0 : browser.hashCode());
      result = prime * result
          + ((customImageSearchParams == null) ? 0 : customImageSearchParams.hashCode());
      result = prime * result
          + ((customInstanceSearchParams == null) ? 0 : customInstanceSearchParams.hashCode());
      result = prime * result + ((os == null) ? 0 : os.hashCode());
      result = prime * result + (shots ? 1231 : 1237);
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      ResourceSearchParams other = (ResourceSearchParams) obj;
      if (browser == null) {
        if (other.browser != null) {
          return false;
        }
      } else if (!browser.equals(other.browser)) {
        return false;
      }
      if (customImageSearchParams == null) {
        if (other.customImageSearchParams != null) {
          return false;
        }
      } else if (!customImageSearchParams.equals(other.customImageSearchParams)) {
        return false;
      }
      if (customInstanceSearchParams == null) {
        if (other.customInstanceSearchParams != null) {
          return false;
        }
      } else if (!customInstanceSearchParams.equals(other.customInstanceSearchParams)) {
        return false;
      }
      if (os == null) {
        if (other.os != null) {
          return false;
        }
      } else if (!os.equals(other.os)) {
        return false;
      }
      return shots == other.shots;
    }
  }
  
  /**
   * <p><b>Should be accessed only through the interface {@link GridProperty}.</b></p>
   * @author Praveen Tiwari
   *
   */
  public static class GridProperties implements GridProperty {
    
    private String machineType;
    private String serviceAccount;
    private Boolean preemptible;
    private Boolean createExternalIP;
    
    private Set<String> networkTags;
    
    private Map<String, String> customLabels;
    
    @NotEmpty
    private Map<String, String> metadata;
    
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
    public Boolean isPreemptible() {
      return preemptible != null && preemptible;
    }
    
    public void setPreemptible(Boolean preemptible) {
      if (this.preemptible == null) {
        this.preemptible = preemptible;
      }
    }
  
    @Override
    public Boolean isCreateExternalIP() {
      return createExternalIP != null && createExternalIP;
    }
  
    public void setCreateExternalIP(Boolean createExternalIP) {
      if (this.createExternalIP == null) {
        this.createExternalIP = createExternalIP;
      }
    }
  
    @Override
    public Set<String> getNetworkTags() {
      return networkTags;
    }
  
    public void setNetworkTags(Set<String> networkTags) {
      if (this.networkTags == null && networkTags != null) {
        this.networkTags = ImmutableSet.copyOf(networkTags);
      }
    }
  
    @Override
    public Map<String, String> getCustomLabels() {
      return customLabels;
    }
    
    public void setCustomLabels(Map<String, String> customLabels) {
      if (this.customLabels == null && customLabels != null) {
        this.customLabels = ImmutableMap.copyOf(customLabels);
      }
    }

    @Override
    public Map<String, String> getMetadata() {
      return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
      if (this.metadata == null && metadata != null) {
        this.metadata = ImmutableMap.copyOf(metadata);
      }
    }
  
    @Override
    public String toString() {
      return "GridProperties{" +
          "machineType='" + machineType + '\'' +
          ", serviceAccount='" + serviceAccount + '\'' +
          ", preemptible=" + preemptible +
          ", createExternalIP=" + createExternalIP +
          ", customLabels=" + customLabels +
          ", metadata=" + metadata +
          ", networkTags=" + networkTags +
          '}';
    }
  
    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((customLabels == null) ? 0 : customLabels.hashCode());
      result = prime * result + ((machineType == null) ? 0 : machineType.hashCode());
      result = prime * result + ((metadata == null) ? 0 : metadata.hashCode());
      result = prime * result + ((networkTags == null) ? 0 : networkTags.hashCode());
      result = prime * result + (preemptible ? 1231 : 1237);
      result = prime * result + (createExternalIP ? 1231 : 1237);
      result = prime * result + ((serviceAccount == null) ? 0 : serviceAccount.hashCode());
      return result;
    }
  
    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      GridProperties other = (GridProperties) obj;
      if (customLabels == null) {
        if (other.customLabels != null) {
          return false;
        }
      } else if (!customLabels.equals(other.customLabels)) {
        return false;
      }
      if (machineType == null) {
        if (other.machineType != null) {
          return false;
        }
      } else if (!machineType.equals(other.machineType)) {
        return false;
      }
      if (metadata == null) {
        if (other.metadata != null) {
          return false;
        }
      } else if (!metadata.equals(other.metadata)) {
        return false;
      }
      if (networkTags == null) {
        if (other.networkTags != null) {
          return false;
        }
      } else if (!networkTags.equals(other.networkTags)) {
        return false;
      }
      if (preemptible != other.preemptible) {
        return false;
      }
      if (createExternalIP != other.createExternalIP) {
        return false;
      }
      if (serviceAccount == null) {
        return other.serviceAccount == null;
      } else return serviceAccount.equals(other.serviceAccount);
    }
  }

  @Override
  public String toString() {
    return "RequestGridCreate [buildProperties=" + buildProperties + ", resourceSearchParams="
        + resourceSearchParams + ", gridProperties=" + gridProperties + "]";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + buildProperties.hashCode();
    result = prime * result + gridProperties.hashCode();
    result = prime * result + resourceSearchParams.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    RequestGridCreate other = (RequestGridCreate) obj;
    if (!buildProperties.equals(other.buildProperties)) {
      return false;
    }
    if (!gridProperties.equals(other.gridProperties)) {
      return false;
    }
    return resourceSearchParams.equals(other.resourceSearchParams);
  }
}
