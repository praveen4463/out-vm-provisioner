package com.zylitics.wzgp.resource.grid;

import java.util.HashMap;
import java.util.Map;

import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.APICoreProperties.GridDefault;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;

public abstract class AbstractGrid {
  
  protected final APICoreProperties apiCoreProps; 
  protected final ResourceExecutor executor;
  protected final BuildProperty buildProp;
  protected final GridProperty gridProp;
  
  protected AbstractGrid(APICoreProperties apiCoreProps
      , ResourceExecutor executor
      , BuildProperty buildProp
      , GridProperty gridProp) {
    this.apiCoreProps = apiCoreProps;
    this.executor = executor;
    this.buildProp = buildProp;
    this.gridProp = gridProp;
  }
  
  /**
   * Merge user defined and server defined collections in grid properties. User defined values
   * take precedence.
   */
  protected Map<String, String> mergedMetadata() {
    GridDefault gridDefault = apiCoreProps.getGridDefault();
    Map<String, String> metadata = new HashMap<>();
    
    // first put server defined grid defaults.
    metadata.putAll(gridDefault.getMetadata());
    // merge user defined grid properties, replacing if there's a match.
    metadata.putAll(gridProp.getMetadata());
    return metadata;
  }
}
