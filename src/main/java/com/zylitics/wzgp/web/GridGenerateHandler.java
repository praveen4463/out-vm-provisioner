package com.zylitics.wzgp.web;

import java.util.Optional;

import org.assertj.core.util.Strings;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;

import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.zylitics.wzgp.config.SharedDependencies;
import com.zylitics.wzgp.http.RequestGridCreate;
import com.zylitics.wzgp.http.ResponseGridCreate;
import com.zylitics.wzgp.resource.CompletedOperation;
import com.zylitics.wzgp.resource.grid.GridGenerator;
import com.zylitics.wzgp.resource.search.ResourceSearch;
import com.zylitics.wzgp.resource.util.ResourceUtil;

public class GridGenerateHandler extends AbstractGridCreateHandler {
  private String sourceImageFamily;

  public GridGenerateHandler(SharedDependencies sharedDep
      , RequestGridCreate request) {
    super(sharedDep, request);
  }
  
  @Override
  public ResponseEntity<ResponseGridCreate> handle() throws Exception {
    Image image = null;
    // First try if we can get image from the inputs.
    if (!Strings.isNullOrEmpty(sourceImageFamily)) {
      image = computeCalls.getImageFromFamily(sourceImageFamily);
    }
    // If nothing worked, search an image.
    if (image == null) {
      image = searchImage();
    }
    // we've image, go ahead.
    return generateGrid(image);
  }
  
  public void setSourceImageFamily(String sourceImageFamily) {
    Assert.hasText(sourceImageFamily, "source image family can't be empty");
    
    this.sourceImageFamily = sourceImageFamily;
  }
  
  private Image searchImage() throws Exception {
    ResourceSearch search = getSearch();
    Optional<Image> image = search.searchImage();
    if (!image.isPresent()) {
      throw new ImageNotFoundException(
          String.format("No image matches the given search terms, search terms: %s %s"
          , request.getResourceSearchParams().toString()
          , buildProp.toString()));
    }
    return image.get();
  }
  
  private ResponseEntity<ResponseGridCreate> generateGrid(Image image) throws Exception {
    GridGenerator generator = getGridGenerator(image);
    CompletedOperation completedOperation = generator.create();
    Operation operation = completedOperation.get();
    if (!ResourceUtil.isOperationSuccess(operation))  {
      throw new GridNotCreatedException(
          String.format("Couldn't generate a new grid using image %s, operation: %s %s"
          , image.getName()
          , operation.toPrettyString()
          , buildProp.toString()));
    }
    // get the created grid instance
    Instance gridInstance = computeCalls.getInstance(operation.getName(), operation.getZone());
    onGridGeneratedEventHandler(gridInstance);
    
    ResponseGridCreate response = prepareResponse(gridInstance);
    return ResponseEntity
        .status(response.getHttpErrorStatusCode())
        .body(response);
  }
  
  private void onGridGeneratedEventHandler(Instance gridInstance) throws Exception {
    // label buildId to the created grid instance
    lockGridInstance(gridInstance.getName());
    // verify the grid is running and there's nothing wrong
    if (!isRunning(gridInstance)) {
      // shouldn't happen
      throw new GridNotRunningException(
          String.format("Grid instance found not running even after the operation"
          + " completed. grid instance: %s %s"
              , gridInstance.toPrettyString()
              , buildProp.toString()));
    }
  }
}
