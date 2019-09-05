package com.zylitics.wzgp.http;

import java.util.HashMap;
import java.util.Map;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;

import org.springframework.http.converter.HttpMessageConverter;
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
  
  public static class BuildProperties implements BuildProperty {
    
    @NotBlank
    private String buildId;

    public String getBuildId() {
      return buildId;
    }

    public void setBuildId(String buildId) {
      this.buildId = buildId;
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
        if (other.buildId != null) {
          return false;
        }
      } else if (!buildId.equals(other.buildId)) {
        return false;
      }
      return true;
    }
  }

  public static class ResourceSearchParams implements ResourceSearchParam {
    
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

    @Override
    public String toString() {
      return "ResourceSearchParams [os=" + os + ", browser=" + browser + ", shots=" + shots + "]";
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((browser == null) ? 0 : browser.hashCode());
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
      if (os == null) {
        if (other.os != null) {
          return false;
        }
      } else if (!os.equals(other.os)) {
        return false;
      }
      if (shots != other.shots) {
        return false;
      }
      return true;
    }
  }
  
  public static class GridProperties implements GridProperty {
    
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

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((customLabels == null) ? 0 : customLabels.hashCode());
      result = prime * result + ((machineType == null) ? 0 : machineType.hashCode());
      result = prime * result + ((metadata == null) ? 0 : metadata.hashCode());
      result = prime * result + (preemptible ? 1231 : 1237);
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
      if (preemptible != other.preemptible) {
        return false;
      }
      if (serviceAccount == null) {
        if (other.serviceAccount != null) {
          return false;
        }
      } else if (!serviceAccount.equals(other.serviceAccount)) {
        return false;
      }
      return true;
    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((buildProperties == null) ? 0 : buildProperties.hashCode());
    result = prime * result + ((gridProperties == null) ? 0 : gridProperties.hashCode());
    result = prime * result
        + ((resourceSearchParams == null) ? 0 : resourceSearchParams.hashCode());
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
    if (buildProperties == null) {
      if (other.buildProperties != null) {
        return false;
      }
    } else if (!buildProperties.equals(other.buildProperties)) {
      return false;
    }
    if (gridProperties == null) {
      if (other.gridProperties != null) {
        return false;
      }
    } else if (!gridProperties.equals(other.gridProperties)) {
      return false;
    }
    if (resourceSearchParams == null) {
      if (other.resourceSearchParams != null) {
        return false;
      }
    } else if (!resourceSearchParams.equals(other.resourceSearchParams)) {
      return false;
    }
    return true;
  }
}
