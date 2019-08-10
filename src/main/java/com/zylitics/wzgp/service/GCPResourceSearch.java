package com.zylitics.wzgp.service;

import java.util.Optional;

import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;

public interface GCPResourceSearch {

  Optional<Instance> searchStoppedInstance(ResourceSearchParams searchParams);
  
  Optional<Image> searchImage(ResourceSearchParams searchParams);
}
