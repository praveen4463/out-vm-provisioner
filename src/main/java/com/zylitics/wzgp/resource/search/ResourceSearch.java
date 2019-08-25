package com.zylitics.wzgp.resource.search;

import java.util.Optional;

import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.service.ComputeService;

public interface ResourceSearch {

  Optional<Instance> searchStoppedInstance(String zone) throws Exception;
  
  Optional<Image> searchImage() throws Exception;
  
  void setBuildProperty(BuildProperty buildProp);
  
  interface Factory {
    
    static Factory getDefault() {
      return new ResourceSearchImpl.Factory();
    }
    
    ResourceSearch create(ComputeService computeCalls, ResourceSearchParam searchParam);
  }
}
