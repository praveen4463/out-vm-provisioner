package com.zylitics.wzgp.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.google.common.collect.ImmutableMap;
import com.zylitics.wzgp.http.ResponseGridDelete;
import com.zylitics.wzgp.http.ResponseStatus;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.service.ComputeService;
import com.zylitics.wzgp.resource.util.ResourceUtil;
import com.zylitics.wzgp.test.dummy.DummyAPICoreProperties;

public class GridDeleteHandlerImplTest {

  private static final String ZONE = "zone-1";
  
  private static final String GRID_NAME = "grid-1";
  
  private static final APICoreProperties API_CORE_PROPS = new DummyAPICoreProperties();
  
  @Test
  @DisplayName("verify handler deletes grid when noRush option is given test")
  void handlerDeletesOnRush() throws Exception {
    String sessionId = "session-1";
    boolean noRush = true;
    Instance instance = new Instance()
        .setName(GRID_NAME)
        .setStatus("RUNNING")
        .setLabels(ImmutableMap.of(ResourceUtil.LABEL_IS_DELETING, "false"));
    
    ResourceExecutor executor = mock(ResourceExecutor.class);
    ComputeService computeSrv = mock(ComputeService.class);
    
    stubSessionIdLabelling(computeSrv, sessionId);
    
    // LABEL_IS_DELETING is false in the returned instance as this is default situation.
    when(computeSrv.getInstance(GRID_NAME, ZONE, null)).thenReturn(instance);
    
    // Since LABEL_IS_DELETING is false, we'll add this label as 'true' to instance before deletion
    when(computeSrv.setLabels(GRID_NAME
        , ImmutableMap.of(ResourceUtil.LABEL_IS_DELETING, "true"), ZONE, null))
        .thenReturn(new Operation().setStatus("DONE"));  // no need to stub operation wait.
    
    stubGridDelete(executor, computeSrv, true);
    
    GridDeleteHandler handler = getHandler(executor, computeSrv);
    
    handler.setNoRush(noRush);
    
    handler.setSessionId(sessionId);
    
    ResponseEntity<ResponseGridDelete> response = handler.handle();
    
    validateResonse(response);
  }
  
  @Test
  @DisplayName("verify handler deletes grid when is-deleting label is true test")
  void handlerDeletesWhenIsDeleting() throws Exception {
    String sessionId = "session-1";
    Instance instance = new Instance()
        .setName(GRID_NAME)
        .setStatus("RUNNING")
        .setLabels(ImmutableMap.of(ResourceUtil.LABEL_IS_DELETING, "true"));
    
    ResourceExecutor executor = mock(ResourceExecutor.class);
    ComputeService computeSrv = mock(ComputeService.class);
    
    stubSessionIdLabelling(computeSrv, sessionId);
    
    when(computeSrv.getInstance(GRID_NAME, ZONE, null)).thenReturn(instance);
    
    stubGridDelete(executor, computeSrv, true);
    
    GridDeleteHandler handler = getHandler(executor, computeSrv);
    
    handler.setSessionId(sessionId);
    
    ResponseEntity<ResponseGridDelete> response = handler.handle();
    
    validateResonse(response);
  }
  
  @Test
  @DisplayName("verify handler stops grid when noRush and is-deleting both unavailable test")
  void handlerStopsGrid() throws Exception {
    String sessionId = "session-1";
    Instance instance = new Instance()
        .setName(GRID_NAME)
        .setStatus("RUNNING")
        .setLabels(ImmutableMap.of(ResourceUtil.LABEL_IS_DELETING, "false"));
    
    ResourceExecutor executor = mock(ResourceExecutor.class);
    ComputeService computeSrv = mock(ComputeService.class);
    
    stubSessionIdLabelling(computeSrv, sessionId);
    
    // LABEL_IS_DELETING is false in the returned instance as this is default situation.
    when(computeSrv.getInstance(GRID_NAME, ZONE, null)).thenReturn(instance);
    
    // LABEL_IS_DELETING won't be added to grid as we're going to stop not delete.
    
    stubGridStop(executor, computeSrv, true);
    
    GridDeleteHandler handler = getHandler(executor, computeSrv);
    handler.setSessionId(sessionId);
    
    ResponseEntity<ResponseGridDelete> response = handler.handle();
    
    validateResonse(response);
  }
  
  private GridDeleteHandler getHandler(ResourceExecutor executor, ComputeService computeSrv) {
    return new GridDeleteHandlerImpl.Factory().create(
        API_CORE_PROPS, executor, computeSrv, ZONE, GRID_NAME);
  }
  
  private void stubSessionIdLabelling(ComputeService computeSrv, String sessionId)
      throws Exception {
    // sessionId should always be passed, stub so that it could be added as metadata.
    when(computeSrv.setMetadata(GRID_NAME
        , ImmutableMap.of(ResourceUtil.METADATA_CURRENT_TEST_SESSIONID, sessionId)
        , ZONE
        , null))
        .thenReturn(new Operation().setStatus("DONE"));  // no need to stub operation wait.
  }
  
  private void stubGridStop(ResourceExecutor executor, ComputeService computeSrv
      , boolean shouldSucceed) throws Exception {
    Operation stopOperation = new Operation().setStatus("RUNNING");
    when(computeSrv.stopInstance(GRID_NAME, ZONE, null))
        .thenReturn(stopOperation);
    when(executor.blockUntilComplete(stopOperation, null))
        .thenReturn(getOperation(GRID_NAME, ZONE, shouldSucceed));
  }
  
  private void stubGridDelete(ResourceExecutor executor, ComputeService computeSrv
      , boolean shouldSucceed) throws Exception {
    Operation deleteOperation = new Operation().setStatus("RUNNING");
    when(computeSrv.deleteInstance(GRID_NAME, ZONE, null))
        .thenReturn(deleteOperation);
    when(executor.blockUntilComplete(deleteOperation, null))
        .thenReturn(getOperation(GRID_NAME, ZONE, shouldSucceed));
  }
  
  private Operation getOperation(String resourceName, String zone, boolean isSuccess) {
    return new Operation()
        .setHttpErrorStatusCode(
            isSuccess? HttpStatus.OK.value() : HttpStatus.INTERNAL_SERVER_ERROR.value())
        .setStatus("DONE")
        .setName(resourceName)
        .setZone(zone);
  }
  
  private void validateResonse(ResponseEntity<ResponseGridDelete> response) {
    assertEquals(HttpStatus.OK, response.getStatusCode());
    
    ResponseGridDelete responseBody = response.getBody();
    
    assertEquals(ZONE, responseBody.getZone());
    assertEquals(ResponseStatus.SUCCESS.name(), responseBody.getStatus());
    assertEquals(HttpStatus.OK.value(), responseBody.getHttpErrorStatusCode());
  }
}
