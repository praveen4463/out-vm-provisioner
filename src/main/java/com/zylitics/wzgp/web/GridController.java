package com.zylitics.wzgp.web;

import com.zylitics.wzgp.http.*;
import com.zylitics.wzgp.web.exceptions.GridGetRunningHandlerFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.api.client.util.Strings;
import com.google.api.services.compute.Compute;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.compute.ComputeService;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.search.ResourceSearch;
import com.zylitics.wzgp.web.exceptions.GridStartHandlerFailureException;

/*
 * We should make sure our application don't throw any exception at this controller level and
 * convert all exceptions to responses so that spring don't send a response without response body.
 * If we let this happen, response will contain a http status code based on the exception and
 * a http status message based on exception message and no body. As per REST requirement, we should
 * always send a response body.
 * But, i feel for now we can throw unchecked exceptions from application to controller, it has a
 * catch all handler which will send 500 error and log the exception. This should be good for now as
 * we'd anyway want to do this in case on an application level error that is not recoverable.
 * 
 * For exceptions thrown by spring, this controller has @ExceptionHandler method that will catch
 * all type of exceptions. Ideally we should catch specific exceptions and put the specific http
 * status code and message in response, but for now I will just put 500 status for all exceptions,
 * and improve that later as the experience with type of exceptions grows. Being this is an internal
 * api and we're not doing much at client other than logging the returned response if the status is
 * FAILED, this should be ok.
 * To learn various exceptions, we can use this api with different set of invalid inputs and see the
 * logs. Incrementally document them and write methods specific to the exceptions.
 * 
 * !!! Note that, object of this class will be shared among all threads, take care with global
 * members.
 * 
 * Note: The controller is instantiated once and used for all requests processing. Then handler
 * factories used will be injected on the first instantiation and will remain until the life of
 * application together with other controller dependencies.
 */
@RestController
@RequestMapping("${app-short-version}/zones/{zone}/grids")
public class GridController {
  
  private static final Logger LOG = LoggerFactory.getLogger(GridController.class);
  
  private final Compute compute;
  private final APICoreProperties apiCoreProps;
  private final ResourceExecutor executor;
  private final ComputeService computeSrv;
  private final ResourceSearch search;
  private final FingerprintBasedUpdater fingerprintBasedUpdater;
  private final GridGenerateHandler.Factory gridGenerateHandlerFactory;
  private final GridGetRunningHandler.Factory gridGetRunningHandlerFactory;
  private final GridStartHandler.Factory gridStartHandlerFactory;
  private final GridDeleteHandler.Factory gridDeleteHandlerFactory;
  
  // All these dependencies are singleton and that's why we'll provide these to all objects
  // rather than letting them generate.
  @Autowired
  GridController(Compute compute
      , APICoreProperties apiCoreProps
      , ResourceExecutor executor
      , ComputeService computeSrv
      , ResourceSearch search
      , FingerprintBasedUpdater fingerprintBasedUpdater
      , GridGenerateHandler.Factory gridGenerateHandlerFactory
      , GridGetRunningHandler.Factory gridGetRunningHandlerFactory
      , GridStartHandler.Factory gridStartHandlerFactory
      , GridDeleteHandler.Factory gridDeleteHandlerFactory) {
    this.compute = compute;
    this.apiCoreProps = apiCoreProps;
    this.executor = executor;
    this.computeSrv = computeSrv;
    this.search = search;
    this.fingerprintBasedUpdater = fingerprintBasedUpdater;
    this.gridGenerateHandlerFactory = gridGenerateHandlerFactory;
    this.gridGetRunningHandlerFactory = gridGetRunningHandlerFactory;
    this.gridStartHandlerFactory = gridStartHandlerFactory;
    this.gridDeleteHandlerFactory = gridDeleteHandlerFactory;
  }

  @PostMapping
  public ResponseEntity<ResponseGridCreate> create(
      @Validated @RequestBody RequestGridCreate gridCreateReq,
      @PathVariable String zone,
      @RequestParam(required = false) boolean noRush,
      @RequestParam(required = false) boolean requireRunningVM,
      @RequestParam(required = false) String sourceImageFamily) throws Exception {
    
    LOG.info("received request: {}", gridCreateReq.toString());
    
    if (!Strings.isNullOrEmpty(sourceImageFamily) || noRush) {
      LOG.debug("Going to create a new instance, noRush: {}, sourceImageFamily: {} {}", noRush
          , sourceImageFamily, addToException(gridCreateReq.getBuildProperties()));
      GridGenerateHandler generateHandler = gridGenerateHandlerFactory.create(compute
          , apiCoreProps
          , executor
          , computeSrv
          , search
          , fingerprintBasedUpdater
          , zone
          , gridCreateReq);
      if (!Strings.isNullOrEmpty(sourceImageFamily)) {
        generateHandler.setSourceImageFamily(sourceImageFamily);
      }
      return generateHandler.handle();
    }
    
    // get a running instance if that's needed
    if (requireRunningVM) {
      GridGetRunningHandler getRunningHandler = gridGetRunningHandlerFactory.create(apiCoreProps
          , executor
          , computeSrv
          , search
          , fingerprintBasedUpdater
          , zone
          , gridCreateReq);
      try {
        return getRunningHandler.handle();
      } catch (Throwable failure) {
        if (!(failure instanceof GridGetRunningHandlerFailureException)) {
          LOG.error("Get running handler experienced an unexpected exception, trying to" +
              " find a stopped instance "
              + addToException(gridCreateReq.getBuildProperties()), failure);
        }
        // go on to find a stopped one
      }
    }
    
    // find a stopped instance and start it.
    GridStartHandler startHandler = gridStartHandlerFactory.create(apiCoreProps
        , executor
        , computeSrv
        , search
        , fingerprintBasedUpdater
        , zone
        , gridCreateReq);
    try {
      return startHandler.handle();
    } catch (Throwable failure) {
      if (!(failure instanceof GridStartHandlerFailureException)) {
        LOG.error("start handler experienced an unexpected exception, trying to create fresh grid "
            + addToException(gridCreateReq.getBuildProperties()), failure);
      }
      LOG.debug("Couldn't find a stopped instance, going to create a fresh one. {}"
          , addToException(gridCreateReq.getBuildProperties()));
      // we couldn't get a stopped grid instance, fallback to a fresh one.
      return gridGenerateHandlerFactory.create(compute
          , apiCoreProps
          , executor
          , computeSrv
          , search
          , fingerprintBasedUpdater
          , zone
          , gridCreateReq).handle();
    }
  }
  
  @DeleteMapping("/{gridName}")
  public ResponseEntity<ResponseGridDelete> delete(
      @PathVariable String zone,
      @PathVariable String gridName,
      @RequestParam(required = false) boolean noRush,
      @RequestParam(required = false) boolean requireRunningVM,
      @RequestParam(required = false) String sessionId) throws Exception {
    GridDeleteHandler deleteHandler = gridDeleteHandlerFactory.create(apiCoreProps
        , executor
        , computeSrv
        , fingerprintBasedUpdater
        , zone
        , gridName);
    if (!Strings.isNullOrEmpty(sessionId)) {
      deleteHandler.setSessionId(sessionId);
    }
    deleteHandler.setNoRush(noRush);
    deleteHandler.setRequireRunningVM(requireRunningVM);
    return deleteHandler.handle();
  }
  
  /**
   * Invoked when @RequestBody binding is failed 
   */
  @SuppressWarnings("unused")
  @ExceptionHandler
  public ResponseEntity<ResponseGridError> handleExceptions(MethodArgumentNotValidException ex) {
    return processErrResponse(ex, HttpStatus.BAD_REQUEST);
  }
  
  /**
   * Catch all exception handler for spring raised errors. Later divide it into specific errors.
   * Reference:
   * docs.spring.io/spring/docs/current/spring-framework-reference/web.html#mvc-ann-exceptionhandler
   * @param ex the catched {@link Exception} type.
   * @return {@link ResponseEntity}
   */
  @SuppressWarnings("unused")
  @ExceptionHandler
  public ResponseEntity<ResponseGridError> handleExceptions(Exception ex) {
    return processErrResponse(ex, HttpStatus.INTERNAL_SERVER_ERROR);
  }
  
  private ResponseEntity<ResponseGridError> processErrResponse(Throwable ex, HttpStatus status) {
    // Log exception.
    // TODO: we'll have to see what type of errors we may get here and may require more information
    // from handler classes to better debug error causes, for example the state of program when this
    // exception occurred, the received parameters from client, etc.
    LOG.error("", ex);
    
    ResponseGridError errRes = new ResponseGridError();
    errRes.setHttpStatusCode(status.value());
    errRes.setError(ex.getMessage());
    errRes.setStatus(ResponseStatus.FAILURE.name());
    
    return ResponseEntity
        .status(status)
        .body(errRes);
  }
  
  private String addToException(BuildProperty buildProp) {
    StringBuilder sb = new StringBuilder();
    if (buildProp != null) {
      sb.append(buildProp.toString());
    }
    return sb.toString();
  }
}
