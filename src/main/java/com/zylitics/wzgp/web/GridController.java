package com.zylitics.wzgp.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
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
import com.zylitics.wzgp.config.GCPCompute;
import com.zylitics.wzgp.config.SharedDependencies;
import com.zylitics.wzgp.http.AbstractResponse;
import com.zylitics.wzgp.http.ResponseGridError;
import com.zylitics.wzgp.http.RequestGridCreate;
import com.zylitics.wzgp.http.ResponseGridCreate;
import com.zylitics.wzgp.http.ResponseGridDelete;
import com.zylitics.wzgp.http.ResponseStatus;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.executor.ResourceExecutorImpl;

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
@RequestMapping("${api-core.short-version}/zones/{zone}/grids")
public class GridController {
  
  private static final Logger LOG = LoggerFactory.getLogger(GridController.class);
  
  private final Compute compute;
  private final String token;
  private final APICoreProperties apiCoreProps;
  
  @Autowired
  public GridController(GCPCompute gcpCompute, APICoreProperties apiCoreProps) {
    Assert.notNull(gcpCompute, "GCPCompute object can't be null.");
    compute = gcpCompute.getCompute();
    token = gcpCompute.getToken().getTokenValue();
    
    Assert.notNull(apiCoreProps, "APICoreProperties object can't be null.");
    this.apiCoreProps = apiCoreProps;
  }
  
  private SharedDependencies getSharedDependencies(String zone) {
    return new SharedDependencies(compute
        , token
        , apiCoreProps
        , zone);
  }

  @PostMapping
  public ResponseEntity<AbstractResponse> create(
      @Validated @RequestBody RequestGridCreate gridCreateReq
      , @PathVariable String zone
      , @RequestParam(defaultValue="false") boolean noRush
      , @RequestParam(required=false) String sourceImageFamily) {
    SharedDependencies sharedDep = getSharedDependencies(zone);
    
    
    if (!Strings.isNullOrEmpty(sourceImageFamily)) {
      // when sourceImageFamily is given, we'll create a new instance straight away without checking
      // anything else.
      
    }
    
    // since sourceImageFamily is not supplied, we'll require to search instances, first validate
    // that we got search parameters in requests as they need manual validation.
    gridCreateReq.getResourceSearchParams().validate();
    
    
    return null;
  }
  
  @DeleteMapping("/{gridName}")
  public ResponseEntity<AbstractResponse> delete(@PathVariable String zone
      , @PathVariable String gridName
      , @RequestParam(defaultValue="false") boolean noRush
      , @RequestParam(required=false) String sessionId) {
    return null;
  }
  
  /**
   * Invoked when @RequestBody binding is failed 
   */
  @ExceptionHandler
  public ResponseEntity<AbstractResponse> handleExceptions(MethodArgumentNotValidException ex) {
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
  public ResponseEntity<AbstractResponse> handleExceptions(Exception ex) {
    return processErrResponse(ex, HttpStatus.INTERNAL_SERVER_ERROR);
  }
  
  private ResponseEntity<AbstractResponse> processErrResponse(Throwable ex, HttpStatus status) {
 // Log exception.
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
