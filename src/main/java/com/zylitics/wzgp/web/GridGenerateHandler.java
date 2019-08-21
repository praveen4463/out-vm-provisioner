package com.zylitics.wzgp.web;

import java.util.Optional;

import org.assertj.core.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;

import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.zylitics.wzgp.config.GCPCompute;
import com.zylitics.wzgp.config.SharedDependencies;
import com.zylitics.wzgp.http.RequestGridCreate;
import com.zylitics.wzgp.http.ResponseGridCreate;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.AbstractResource;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.grid.GridGenerator;
import com.zylitics.wzgp.resource.search.ResourceSearch;

public class GridGenerateHandler extends AbstractGridCreateHandler {
  
  private static final Logger LOG = LoggerFactory.getLogger(GridGenerateHandler.class);
  
  private final BuildProperty builProp;
  private String sourceImageFamily;

  public GridGenerateHandler(SharedDependencies sharedDep
      , RequestGridCreate request) {
    super(sharedDep, request);
    
    builProp = request.getBuildProperties();
  }
  
  public ResponseEntity<ResponseGridCreate> handle() throws Exception {
    Image image = null;
    if (!Strings.isNullOrEmpty(sourceImageFamily)) {
      
    }
    if (image == null) {
      image = searchImage();
    }
    return generateGrid(image);
  }
  
  private Image searchImage() throws Exception {
    ResourceSearch search = getSearch();
    Optional<Image> image = search.searchImage();
    if (!image.isPresent()) {
      throw new RuntimeException("No image matches the given search terms, search terms: "
          + request.getResourceSearchParams().toString()
          + addToException(builProp));
    }
    return image.get();
  }
  
  private ResponseEntity<ResponseGridCreate> generateGrid(Image image)
      throws Exception {
    GridGenerator generator = getGridGenerator(sourceImage)
  }
  
  public void setSourceImageFamily(String sourceImageFamily) {
    Assert.hasText(sourceImageFamily, "source image family can't be empty");
    
    this.sourceImageFamily = sourceImageFamily;
  }
  
  private void getImageFromFamily() {
    
  }
  
  private void onCreateEventHandler(Instance instance) {
    
  }
}
