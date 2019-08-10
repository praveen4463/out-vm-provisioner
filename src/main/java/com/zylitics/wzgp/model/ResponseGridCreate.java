package com.zylitics.wzgp.model;

public class ResponseGridCreate extends AbstractResponse {

  private String gridInternalIP;
  private String gridId;
  private String gridName;
  
  public String getGridInternalIP() {
    return gridInternalIP;
  }
  
  public void setGridInternalIP(String gridInternalIP) {
    this.gridInternalIP = gridInternalIP;
  }
  
  public String getGridId() {
    return gridId;
  }
  
  public void setGridId(String gridId) {
    this.gridId = gridId;
  }
  
  public String getGridName() {
    return gridName;
  }
  
  public void setGridName(String gridName) {
    this.gridName = gridName;
  }
}
