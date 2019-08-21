package com.zylitics.wzgp.resource.search;

import java.util.Optional;

import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.zylitics.wzgp.resource.util.ComputeCalls;

public interface ResourceSearch {

  Optional<Instance> searchStoppedInstance() throws Exception;
  
  Optional<Image> searchImage() throws Exception;
  
  interface Factory {
    
    static Factory getDefault() {
      return new ResourceSearchImpl.Factory();
    }
    
    ResourceSearch create(ResourceSearchParam searchParam, ComputeCalls computeCalls);
  }
}
