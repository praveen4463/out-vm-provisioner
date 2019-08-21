package com.zylitics.wzgp.resource.grid;

import java.util.HashMap;
import java.util.Map;

import com.zylitics.wzgp.config.SharedDependencies;
import com.zylitics.wzgp.resource.APICoreProperties.GridDefault;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;

public abstract class AbstractGrid {
  
  protected final SharedDependencies sharedDep;
  protected final GridProperty gridProp;
  protected final ResourceExecutor executor;
  
  protected AbstractGrid(SharedDependencies sharedDep
      , GridProperty gridProp
      , ResourceExecutor executor) {
    this.sharedDep = sharedDep;
    this.gridProp = gridProp;
    this.executor = executor;
  }
  
  /**
   * Merge user defined and server defined collections in grid properties. User defined values
   * take precedence.
   */
  protected Map<String, String> mergedMetadata() {
    GridDefault gridDefault = sharedDep.apiCoreProps().getGridDefault();
    Map<String, String> metadata = new HashMap<>();
    
    // first put server defined grid defaults.
    metadata.putAll(gridDefault.getMetadata());
    // merge user defined grid properties, replacing if there's a match.
    metadata.putAll(gridProp.getMetadata());
    return metadata;
  }
}
