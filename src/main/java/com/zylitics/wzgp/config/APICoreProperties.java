package com.zylitics.wzgp.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Note: Configuration file is written using kebab-case, relaxed binding allows us to convert them
 * to java specific style, for example short-version gets parsed shortVersion.
 * @author Praveen Tiwari
 *
 */
@Component
@ConfigurationProperties(prefix="api-core")
@Validated
public class APICoreProperties {
  
  @NotEmpty
  private String projectId;
  @NotEmpty
  private String shortVersion;
  @NotEmpty
  private String gcpAPI;
  @Valid
  private GridDefault gridDefault = new GridDefault();
  
  public String getProjectId() {
    return projectId;
  }
  
  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }
  
  public String getShortVersion() {
    return shortVersion;
  }
  
  public void setShortVersion(String shortVersion) {
    this.shortVersion = shortVersion;
  }
  
  public String getGcpAPI() {
    return gcpAPI;
  }
  
  public void setGcpAPI(String gcpAPI) {
    this.gcpAPI = gcpAPI;
  }
  
  public GridDefault getGridDefault() {
    return gridDefault;
  }
  
  public static final class GridDefault {
    @NotEmpty
    private String machineType;
    @NotEmpty
    private String network;
    @NotEmpty
    private String serviceAccount;
    private final List<String> tags = new ArrayList<>();
    private final Map<String, String> labels = new HashMap<>();
    private final Map<String, String> metadata = new HashMap<>();
    
    public String getMachineType() {
      return machineType;
    }
    
    public void setMachineType(String machineType) {
      this.machineType = machineType;
    }
    
    public String getNetwork() {
      return network;
    }
    
    public void setNetwork(String network) {
      this.network = network;
    }
    
    public String getServiceAccount() {
      return serviceAccount;
    }
    
    public void setServiceAccount(String serviceAccount) {
      this.serviceAccount = serviceAccount;
    }
    
    public List<String> getTags() {
      return tags;
    }
    
    public Map<String, String> getLabels() {
      return labels;
    }
    
    public Map<String, String> getMetadata() {
      return metadata;
    }
  }
}
