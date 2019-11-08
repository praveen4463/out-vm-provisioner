package com.zylitics.wzgp.web;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.google.common.collect.ImmutableMap;
import com.zylitics.wzgp.http.ResponseGridDelete;
import com.zylitics.wzgp.http.ResponseStatus;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.compute.ComputeService;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.util.ResourceUtil;
import com.zylitics.wzgp.test.dummy.DummyAPICoreProperties;
import com.zylitics.wzgp.test.util.ResourceTestUtil;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness=Strictness.LENIENT)
class GridDeleteHandlerImplTest {

  private static final String ZONE = "us-central0-g";
  
  private static final String GRID_NAME = "grid-1";
  
  private static final APICoreProperties API_CORE_PROPS = new DummyAPICoreProperties();
  
  @Test
  @DisplayName("verify handler deletes grid when noRush option is given test")
  void handlerDeletesOnRush() throws Exception {
    String sessionId = "session-1";
    Instance instance = new Instance()
        .setName(GRID_NAME)
        .setStatus("RUNNING")
        .setLabels(ImmutableMap.of(ResourceUtil.LABEL_IS_DELETING, "false"));
    
    ResourceExecutor executor = mock(ResourceExecutor.class);
    ComputeService computeSrv = mock(ComputeService.class);
    FingerprintBasedUpdater fingerprintBasedUpdater = mock(FingerprintBasedUpdater.class);
    
    // LABEL_IS_DELETING is false in the returned instance as this is default situation.
    when(computeSrv.getInstance(GRID_NAME, ZONE, null)).thenReturn(instance);
    
    Operation operationSessionId =
        stubSessionIdLabelling(fingerprintBasedUpdater, instance, sessionId);

    // Since LABEL_IS_DELETING is false, we'll add this label as 'true' to instance before deletion
    Operation operationIsDeleting = new Operation().setStatus("DONE").setName("op-is-deleting");
    when(fingerprintBasedUpdater.updateLabels(instance
        , ImmutableMap.of(ResourceUtil.LABEL_IS_DELETING, "true"), null))
        .thenReturn(operationIsDeleting);  // no need to stub operation wait.
    
    stubGridDelete(executor, computeSrv, true);
    
    GridDeleteHandler handler = getHandler(executor, computeSrv, fingerprintBasedUpdater);
    
    handler.setNoRush(true);
    
    handler.setSessionId(sessionId);
    
    ResponseEntity<ResponseGridDelete> response = handler.handle();
    
    validateResonse(response);
    
    verify(executor).blockUntilComplete(operationSessionId, null);
    verify(executor).blockUntilComplete(operationIsDeleting, null);
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
    FingerprintBasedUpdater fingerprintBasedUpdater = mock(FingerprintBasedUpdater.class);
    
    when(computeSrv.getInstance(GRID_NAME, ZONE, null)).thenReturn(instance);
    
    Operation operationSessionId =
        stubSessionIdLabelling(fingerprintBasedUpdater, instance, sessionId);
    
    stubGridDelete(executor, computeSrv, true);
    
    GridDeleteHandler handler = getHandler(executor, computeSrv, fingerprintBasedUpdater);
    
    handler.setSessionId(sessionId);
    
    ResponseEntity<ResponseGridDelete> response = handler.handle();
    
    validateResonse(response);
    
    verify(executor).blockUntilComplete(operationSessionId, null);
    
    verify(fingerprintBasedUpdater, never()).updateLabels(instance
        , ImmutableMap.of(ResourceUtil.LABEL_IS_DELETING, "true"), null);
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
    FingerprintBasedUpdater fingerprintBasedUpdater = mock(FingerprintBasedUpdater.class);
    
    // LABEL_IS_DELETING is false in the returned instance as this is default situation.
    when(computeSrv.getInstance(GRID_NAME, ZONE, null)).thenReturn(instance);
    
    Operation operationSessionId =
        stubSessionIdLabelling(fingerprintBasedUpdater, instance, sessionId);
    
    // LABEL_IS_DELETING won't be added to grid as we're going to stop not delete.
    
    stubGridStop(executor, computeSrv, true);
    
    GridDeleteHandler handler = getHandler(executor, computeSrv, fingerprintBasedUpdater);
    handler.setSessionId(sessionId);
    
    ResponseEntity<ResponseGridDelete> response = handler.handle();
    
    validateResonse(response);
    
    verify(executor).blockUntilComplete(operationSessionId, null);
    
    verify(fingerprintBasedUpdater, never()).updateLabels(instance
        , ImmutableMap.of(ResourceUtil.LABEL_IS_DELETING, "true"), null);
  }
  
  private GridDeleteHandler getHandler(ResourceExecutor executor, ComputeService computeSrv
      , FingerprintBasedUpdater fingerprintBasedUpdater) {
    return new GridDeleteHandlerImpl.Factory().create(
        API_CORE_PROPS, executor, computeSrv, fingerprintBasedUpdater, ZONE, GRID_NAME);
  }
  
  private Operation stubSessionIdLabelling(FingerprintBasedUpdater fingerprintBasedUpdater
      , Instance instance, String sessionId) throws Exception {
    Operation operation =  new Operation().setStatus("DONE").setName("op-session-id");
    // sessionId should always be passed, stub so that it could be added as metadata.
    when(fingerprintBasedUpdater.updateMetadata(instance
        , ImmutableMap.of(ResourceUtil.METADATA_CURRENT_TEST_SESSIONID, sessionId), null))
        .thenReturn(operation);
    return operation;
  }
  
  @SuppressWarnings("SameParameterValue")
  private void stubGridStop(ResourceExecutor executor, ComputeService computeSrv
      , boolean shouldSucceed) throws Exception {
    Operation stopOperation = new Operation().setStatus("RUNNING").setName("op-grid-stop");
    when(computeSrv.stopInstance(GRID_NAME, ZONE, null))
        .thenReturn(stopOperation);
    when(executor.blockUntilComplete(stopOperation, null))
        .thenReturn(getOperation(GRID_NAME, ZONE, shouldSucceed));
  }
  
  @SuppressWarnings("SameParameterValue")
  private void stubGridDelete(ResourceExecutor executor, ComputeService computeSrv
      , boolean shouldSucceed) throws Exception {
    Operation deleteOperation = new Operation().setStatus("RUNNING").setName("op-grid-delete");
    when(computeSrv.deleteInstance(GRID_NAME, ZONE, null))
        .thenReturn(deleteOperation);
    when(executor.blockUntilComplete(deleteOperation, null))
        .thenReturn(getOperation(GRID_NAME, ZONE, shouldSucceed));
  }
  
  @SuppressWarnings("SameParameterValue")
  private Operation getOperation(String resourceName, String zone, boolean isSuccess) {
    return new Operation()
        .setHttpErrorStatusCode(
            isSuccess? null : HttpStatus.INTERNAL_SERVER_ERROR.value())
        .setStatus("DONE")
        .setName("operation-" + UUID.randomUUID())
        .setTargetLink(ResourceTestUtil.getOperationTargetLink(resourceName, zone))
        .setZone(ResourceTestUtil.getZoneLink(zone));
  }
  
  private void validateResonse(ResponseEntity<ResponseGridDelete> response) {
    assertEquals(HttpStatus.OK, response.getStatusCode());
    
    ResponseGridDelete responseBody = response.getBody();
    assertNotNull(responseBody);
    assertEquals(ZONE, responseBody.getZone());
    assertEquals(ResponseStatus.SUCCESS.name(), responseBody.getStatus());
    assertEquals(HttpStatus.OK.value(), responseBody.getHttpStatusCode());
  }
}
