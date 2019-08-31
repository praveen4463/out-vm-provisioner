package com.zylitics.wzgp.web;

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
import com.zylitics.wzgp.http.ResponseGridError;
import com.zylitics.wzgp.http.RequestGridCreate;
import com.zylitics.wzgp.http.ResponseGridCreate;
import com.zylitics.wzgp.http.ResponseGridDelete;
import com.zylitics.wzgp.http.ResponseStatus;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.service.ComputeService;
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
 */
@RestController
@RequestMapping("${app-short-version}/zones/{zone}/grids")
public class GridController {
  
  private static final Logger LOG = LoggerFactory.getLogger(GridController.class);
  
  private final Compute compute;
  private final APICoreProperties apiCoreProps;
  private final ResourceExecutor executor;
  private final ComputeService computeSrv;
  private final GridGenerateHandler.Factory gridGenerateHandlerFactory;
  private final GridStartHandler.Factory gridStartHandlerFactory;
  private final GridDeleteHandler.Factory gridDeleteHandlerFactory;
  
  // All these dependencies are singleton and that's why we'll provide these to all domain objects
  // rather than letting them generate. Domain objects are created per request and don't need to
  // worry about thread safety.
  @Autowired
  public GridController(Compute compute
      , APICoreProperties apiCoreProps
      , ResourceExecutor executor
      , ComputeService computeSrv
      , GridGenerateHandler.Factory gridGenerateHandlerFactory
      , GridStartHandler.Factory gridStartHandlerFactory
      , GridDeleteHandler.Factory gridDeleteHandlerFactory) {
    this.compute = compute;
    this.apiCoreProps = apiCoreProps;
    this.executor = executor;
    this.computeSrv = computeSrv;
    this.gridGenerateHandlerFactory = gridGenerateHandlerFactory;
    this.gridStartHandlerFactory = gridStartHandlerFactory;
    this.gridDeleteHandlerFactory = gridDeleteHandlerFactory;
  }

  @PostMapping
  public ResponseEntity<ResponseGridCreate> create(
      @Validated @RequestBody RequestGridCreate gridCreateReq
      , @PathVariable String zone
      , @RequestParam(defaultValue="false") boolean noRush
      , @RequestParam(required=false) String sourceImageFamily) throws Exception {
    
    if (!Strings.isNullOrEmpty(sourceImageFamily) || noRush) {
      GridGenerateHandler generateHandler = gridGenerateHandlerFactory.create(compute
          , apiCoreProps
          , executor
          , computeSrv
          , zone
          , gridCreateReq);
      if (!Strings.isNullOrEmpty(sourceImageFamily)) {
        generateHandler.setSourceImageFamily(sourceImageFamily);
      }
      return generateHandler.handle();
    }
    // we'll now require to search instances, first validate that we got search parameters in
    // requests as they need manual validation.
    gridCreateReq.getResourceSearchParams().validate();
    
    // first try to find and start a stopped grid instance.
    GridStartHandler startHandler = gridStartHandlerFactory.create(apiCoreProps
        , executor
        , computeSrv
        , zone
        , gridCreateReq);
    try {
      return startHandler.handle();
    } catch (GridStartHandlerFailureException failure) {
      // we couldn't get a stopped grid instance, fallback to a fresh one.
      return gridGenerateHandlerFactory.create(compute
          , apiCoreProps
          , executor
          , computeSrv
          , zone
          , gridCreateReq).handle();
    }
  }
  
  @DeleteMapping("/{gridName}")
  public ResponseEntity<ResponseGridDelete> delete(@PathVariable String zone
      , @PathVariable String gridName
      , @RequestParam(defaultValue="false") boolean noRush
      , @RequestParam(required=false) String sessionId) throws Exception {
    GridDeleteHandler deleteHandler = gridDeleteHandlerFactory.create(apiCoreProps
        , executor
        , computeSrv
        , zone
        , gridName);
    if (!Strings.isNullOrEmpty(sessionId)) {
      deleteHandler.setSessionId(sessionId);
    }
    deleteHandler.setNoRush(noRush);
    return deleteHandler.handle();
  }
  
  /**
   * Invoked when @RequestBody binding is failed 
   */
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
  @ExceptionHandler
  public ResponseEntity<ResponseGridError> handleExceptions(Exception ex) {
    return processErrResponse(ex, HttpStatus.INTERNAL_SERVER_ERROR);
  }
  
  private ResponseEntity<ResponseGridError> processErrResponse(Throwable ex, HttpStatus status) {
    // Log exception.
    // TODO: we'll have to see what type of errors we may get here and may require more information
    // from handler classes to better debug error causes, for example the state of program when this
    // exception occurred, the received parameters from client, etc.
    LOG.error(ex.getMessage(), ex);
    
    ResponseGridError errRes = new ResponseGridError();
    errRes.setHttpErrorStatusCode(status.value());
    errRes.setError(ex.getMessage());
    errRes.setStatus(ResponseStatus.FAILURE.name());
    
    return ResponseEntity
        .status(status)
        .body(errRes);
  }
}
