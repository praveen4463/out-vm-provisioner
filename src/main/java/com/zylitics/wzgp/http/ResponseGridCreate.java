package com.zylitics.wzgp.http;

import java.math.BigInteger;

public class ResponseGridCreate extends AbstractResponse {

  private String gridInternalIP;
  private String gridExternalIP;
  private BigInteger gridId;
  private String gridName;
  private String zone;
  
  public String getGridInternalIP() {
    return gridInternalIP;
  }
  
  public void setGridInternalIP(String gridInternalIP) {
    this.gridInternalIP = gridInternalIP;
  }
  
  public String getGridExternalIP() {
    return gridExternalIP;
  }
  
  public void setGridExternalIP(String gridExternalIP) {
    this.gridExternalIP = gridExternalIP;
  }
  
  public BigInteger getGridId() {
    return gridId;
  }
  
  public void setGridId(BigInteger gridId) {
    this.gridId = gridId;
  }
  
  public String getGridName() {
    return gridName;
  }
  
  public void setGridName(String gridName) {
    this.gridName = gridName;
  }
  
  public String getZone() {
    return zone;
  }
  
  public void setZone(String zone) {
    this.zone = zone;
  }
}
