package com.zylitics.wzgp.web;

import static com.zylitics.wzgp.resource.util.ResourceUtil.nameFromUrl;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.google.common.base.Strings;
import com.zylitics.wzgp.http.RequestGridCreate;
import com.zylitics.wzgp.http.ResponseGridCreate;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.CompletedOperation;
import com.zylitics.wzgp.resource.compute.ComputeService;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.grid.GridGenerator;
import com.zylitics.wzgp.resource.search.ResourceSearch;
import com.zylitics.wzgp.resource.util.ResourceUtil;
import com.zylitics.wzgp.web.exceptions.GridNotCreatedException;
import com.zylitics.wzgp.web.exceptions.GridNotRunningException;
import com.zylitics.wzgp.web.exceptions.ImageNotFoundException;

public class GridGenerateHandlerImpl extends AbstractGridCreateHandler
    implements GridGenerateHandler {
  
  private static final Logger LOG = LoggerFactory.getLogger(GridGenerateHandlerImpl.class);
  
  private final Compute compute;
  
  private String sourceImageFamily;

  private GridGenerateHandlerImpl(Compute compute
      , APICoreProperties apiCoreProps
      , ResourceExecutor executor
      , ComputeService computeSrv
      , ResourceSearch search
      , FingerprintBasedUpdater fingerprintBasedUpdater
      , String zone
      , RequestGridCreate request) {
    super(apiCoreProps, executor, computeSrv, search, fingerprintBasedUpdater, zone, request);
    
    this.compute = compute;
  }
  
  @Override
  public ResponseEntity<ResponseGridCreate> handle() throws Exception {
    Image image = null;
    // First try if we can get image from the inputs.
    if (!Strings.isNullOrEmpty(sourceImageFamily)) {
      image = computeSrv.getImageFromFamily(sourceImageFamily, buildProp);
      LOG.debug("found image {} from family {} {}", image, sourceImageFamily, addToException());
    }
    // If nothing worked, search an image.
    if (image == null) {
      image = searchImage();
      LOG.debug("found image {} after a search {}", image, addToException());
    }
    // we've image, go ahead.
    return generateGrid(image);
  }
  
  @Override
  public void setSourceImageFamily(String sourceImageFamily) {
    Assert.hasText(sourceImageFamily, "'sourceImageFamily' can't be empty");
    
    this.sourceImageFamily = sourceImageFamily;
  }
  
  private Image searchImage() throws Exception {
    Optional<Image> image = search.searchImage(request.getResourceSearchParams(), buildProp);
    if (!image.isPresent()) {
      throw new ImageNotFoundException(
          String.format("No image matches the given search terms, search terms: %s %s"
          , request.getResourceSearchParams().toString()
          , addToException()));
    }
    return image.get();
  }
  
  private ResponseEntity<ResponseGridCreate> generateGrid(Image image) throws Exception {
    GridGenerator generator = new GridGenerator(compute
        , apiCoreProps
        , executor
        , buildProp
        , request.getGridProperties()
        , image);
    CompletedOperation completedOperation = generator.create(zone);
    Operation operation = completedOperation.get();
    if (!ResourceUtil.isOperationSuccess(operation)) {
      throw new GridNotCreatedException(
          String.format("Couldn't generate a new grid using image %s, operation: %s %s"
          , image.getName()
          , operation.toPrettyString()
          , addToException()));
    }
    // get the created grid instance
    Instance gridInstance = computeSrv.getInstance(
        nameFromUrl(operation.getTargetLink())
        , nameFromUrl(operation.getZone())
        , buildProp);
    LOG.debug("generated a new grid {}:{} {}", gridInstance.getName(), gridInstance.getZone()
        , addToException());
    onGridGeneratedEventHandler(gridInstance);
    
    ResponseGridCreate response = prepareResponse(gridInstance, HttpStatus.CREATED);
    return ResponseEntity
        .status(response.getHttpStatusCode())
        .body(response);
  }
  
  private void onGridGeneratedEventHandler(Instance gridInstance) throws Exception {
    // label buildId to the created grid instance
    lockGridInstance(gridInstance);
    // verify the grid is running and there's nothing wrong
    if (isNotRunning(gridInstance)) {
      // shouldn't happen
      throw new GridNotRunningException(
          String.format("Grid instance found not running even after the operation"
          + " completed. grid instance: %s %s"
              , gridInstance.toPrettyString()
              , addToException()));
    }
  }
  
  public static class Factory implements GridGenerateHandler.Factory {
    
    @Override
    public GridGenerateHandler create(Compute compute, APICoreProperties apiCoreProps
        , ResourceExecutor executor, ComputeService computeSrv, ResourceSearch search
        , FingerprintBasedUpdater fingerprintBasedUpdater, String zone, RequestGridCreate request) {
      return new GridGenerateHandlerImpl(compute, apiCoreProps, executor, computeSrv, search
          , fingerprintBasedUpdater, zone, request);
    }
  }
}
