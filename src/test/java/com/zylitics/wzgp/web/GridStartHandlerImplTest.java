package com.zylitics.wzgp.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;

import java.math.BigInteger;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.ServiceAccount;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.zylitics.wzgp.http.RequestGridCreate;
import com.zylitics.wzgp.http.ResponseGridCreate;
import com.zylitics.wzgp.http.ResponseStatus;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.service.ComputeService;
import com.zylitics.wzgp.resource.util.ResourceUtil;
import com.zylitics.wzgp.test.dummy.DummyAPICoreProperties;
import com.zylitics.wzgp.test.dummy.DummyRequestGridCreate;
import com.zylitics.wzgp.web.exceptions.GridBeingDeletedFromOutsideException;
import com.zylitics.wzgp.web.exceptions.GridNotStartedException;
import com.zylitics.wzgp.web.exceptions.GridOccupiedByOtherException;
import com.zylitics.wzgp.web.exceptions.GridStartHandlerFailureException;

public class GridStartHandlerImplTest {

  private static final String ZONE = "zone-1";
  
  private static final String SEARCHED_INSTANCE_NAME = "grid-1";
  
  private static final BigInteger SEARCHED_INSTANCE_ID = BigInteger.valueOf(2020239494);
  
  private static final String SEARCHED_INSTANCE_NETWORK_IP = "192.168.1.1";
  
  private static final APICoreProperties API_CORE_PROPS = new DummyAPICoreProperties();
  
  private static final RequestGridCreate REQ_CREATE = new DummyRequestGridCreate();
  
  private static final BuildProperty BUILD_PROP = REQ_CREATE.getBuildProperties();
  
  @Test
  @DisplayName("verify handler finds and starts stopped instance test")
  void handlerTest() throws Exception {
    ResourceExecutor executor = mock(ResourceExecutor.class);
    ComputeService computeSrv = mock(ComputeService.class);
    
    when(computeSrv.listInstances(anyString(), eq(1L), eq(ZONE), eq(BUILD_PROP)))
        .thenReturn(ImmutableList.of(getSearchedInstance()));
    
    Operation lockGridOperation = stubGridLocking(executor, computeSrv);
    
    stubGridStart(executor, computeSrv, true);
    
    when(computeSrv.getInstance(SEARCHED_INSTANCE_NAME, ZONE, BUILD_PROP))
        .then(invocation -> {
          Instance instance = getSearchedInstance();
          instance
              .setStatus("RUNNING")
              .setLabels(
                  ImmutableMap.of(ResourceUtil.LABEL_LOCKED_BY_BUILD, BUILD_PROP.getBuildId()));
          return instance;
        });
    
    GridStartHandler handler = getHandler(executor, computeSrv);
    
    ResponseEntity<ResponseGridCreate> response = handler.handle();
    
    assertEquals("DONE", lockGridOperation.getStatus());
    
    validateResonse(response);
  }
  
  @Test
  @DisplayName("verify handler couldn't get a stopped instance raise exception test")
  void noStoppedInstanceFoundTest() throws Exception {
    ResourceExecutor executor = mock(ResourceExecutor.class);
    ComputeService computeSrv = mock(ComputeService.class);
    
    GridStartHandler handler = getHandler(executor, computeSrv);
    
    assertThrows(GridStartHandlerFailureException.class, () -> handler.handle());
    
    verify(computeSrv).listInstances(anyString(), eq(1L), eq(ZONE), eq(BUILD_PROP));
    
    verifyNoMoreInteractions(computeSrv);
    
    verifyNoMoreInteractions(executor);
  }
  
  @Test
  @DisplayName("verify GridStarter failure raises exception test")
  void gridStarterFailureTest() throws Exception {
    ResourceExecutor executor = mock(ResourceExecutor.class);
    ComputeService computeSrv = mock(ComputeService.class);
    
    when(computeSrv.listInstances(anyString(), eq(1L), eq(ZONE), eq(BUILD_PROP)))
    .thenReturn(ImmutableList.of(getSearchedInstance()));

    Operation lockGridOperation = stubGridLocking(executor, computeSrv);

    stubGridStart(executor, computeSrv, false);

    GridStartHandler handler = getHandler(executor, computeSrv);
    
    Throwable t = null;
    try {
      handler.handle();
    } catch(GridStartHandlerFailureException ex) {
      t = ex;
    }
    assertEquals(GridNotStartedException.class, t.getCause().getClass());
    
    assertEquals("DONE", lockGridOperation.getStatus());
  }
  
  @Test
  @DisplayName("verify grid locked by some other request in middle raises exception test")
  void gridLockedByOtherTest() throws Exception {
    ResourceExecutor executor = mock(ResourceExecutor.class);
    ComputeService computeSrv = mock(ComputeService.class);
    
    when(computeSrv.listInstances(anyString(), eq(1L), eq(ZONE), eq(BUILD_PROP)))
    .thenReturn(ImmutableList.of(getSearchedInstance()));

    Operation lockGridOperation = stubGridLocking(executor, computeSrv);

    stubGridStart(executor, computeSrv, true);
    
    when(computeSrv.getInstance(SEARCHED_INSTANCE_NAME, ZONE, BUILD_PROP))
        .then(invocation -> {
          Instance instance = getSearchedInstance();
          instance
              .setStatus("RUNNING")
              .setLabels(
                  ImmutableMap.of(ResourceUtil.LABEL_LOCKED_BY_BUILD, "some-other-req-build-Id"));
          return instance;
        });

    GridStartHandler handler = getHandler(executor, computeSrv);
    
    Throwable t = null;
    try {
      handler.handle();
    } catch(GridStartHandlerFailureException ex) {
      t = ex;
    }
    assertEquals(GridOccupiedByOtherException.class, t.getCause().getClass());
    
    assertEquals("DONE", lockGridOperation.getStatus());
  }
  
  @Test
  @DisplayName("verify post startup finding is-deleting label raises exception test")
  void gridIsDeletingLabelFoundTest() throws Exception {
    ResourceExecutor executor = mock(ResourceExecutor.class);
    ComputeService computeSrv = mock(ComputeService.class);
    
    when(computeSrv.listInstances(anyString(), eq(1L), eq(ZONE), eq(BUILD_PROP)))
    .thenReturn(ImmutableList.of(getSearchedInstance()));

    Operation lockGridOperation = stubGridLocking(executor, computeSrv);

    stubGridStart(executor, computeSrv, true);
    
    when(computeSrv.getInstance(SEARCHED_INSTANCE_NAME, ZONE, BUILD_PROP))
        .then(invocation -> {
          Instance instance = getSearchedInstance();
          instance
              .setStatus("RUNNING")
              .setLabels(
                  ImmutableMap.of(
                      ResourceUtil.LABEL_LOCKED_BY_BUILD, BUILD_PROP.getBuildId(),
                      ResourceUtil.LABEL_IS_DELETING, "true"));
          return instance;
        });

    GridStartHandler handler = getHandler(executor, computeSrv);
    
    Throwable t = null;
    try {
      handler.handle();
    } catch(GridStartHandlerFailureException ex) {
      t = ex;
    }
    assertEquals(GridBeingDeletedFromOutsideException.class, t.getCause().getClass());
    
    assertEquals("DONE", lockGridOperation.getStatus());
  }
  
  private GridStartHandler getHandler(ResourceExecutor executor, ComputeService computeSrv) {
    return new GridStartHandlerImpl.Factory().create(API_CORE_PROPS, executor, computeSrv
        , ZONE, REQ_CREATE);
  }
  
  private Instance getSearchedInstance() {
    return new Instance()
        .setId(SEARCHED_INSTANCE_ID)
        .setName(SEARCHED_INSTANCE_NAME)
        .setNetworkInterfaces(
            ImmutableList.of(new NetworkInterface().setNetworkIP(SEARCHED_INSTANCE_NETWORK_IP)))
        .setZone(ZONE)
        .setMachineType("machine-1")
        .setServiceAccounts(ImmutableList.of(new ServiceAccount().setEmail("unknown@email.com")))
        .setStatus("TERMINATED");
  }
  
  private void stubGridStart(ResourceExecutor executor, ComputeService computeSrv
      , boolean shouldSucceed) throws Exception {
    // Not stubbing update handler methods at grid-start because the component has already been
    // tested separately and we don't require them to run successfully for this test.
    Operation startOperation = new Operation().setStatus("RUNNING");
    when(computeSrv.startInstance(SEARCHED_INSTANCE_NAME, ZONE, BUILD_PROP))
        .thenReturn(startOperation);
    when(executor.blockUntilComplete(startOperation, BUILD_PROP))
        .thenReturn(getOperation(SEARCHED_INSTANCE_NAME, ZONE, shouldSucceed));
  }
  
  private Operation stubGridLocking(ResourceExecutor executor, ComputeService computeSrv)
      throws Exception {
    Map<String, String> lockGridLabel =
        ImmutableMap.of(ResourceUtil.LABEL_LOCKED_BY_BUILD, BUILD_PROP.getBuildId());
    Operation lockGridOperation = new Operation().setStatus("RUNNING");
    when(computeSrv.setLabels(SEARCHED_INSTANCE_NAME, lockGridLabel, ZONE, BUILD_PROP))
        .thenReturn(lockGridOperation);
    when(executor.blockUntilComplete(lockGridOperation, BUILD_PROP))
        .then(inv -> {
          lockGridOperation.setStatus("DONE");
          return lockGridOperation;
        });
    return lockGridOperation;
  }
  
  private Operation getOperation(String resourceName, String zone, boolean isSuccess) {
    return new Operation()
        .setHttpErrorStatusCode(
            isSuccess? HttpStatus.OK.value() : HttpStatus.INTERNAL_SERVER_ERROR.value())
        .setStatus("DONE")
        .setName(resourceName)
        .setZone(zone);
  }
  
  private void validateResonse(ResponseEntity<ResponseGridCreate> response) {
    assertEquals(HttpStatus.OK, response.getStatusCode());
    
    ResponseGridCreate responseBody = response.getBody();
    
    assertEquals(SEARCHED_INSTANCE_NETWORK_IP, responseBody.getGridInternalIP());
    assertEquals(SEARCHED_INSTANCE_ID, responseBody.getGridId());
    assertEquals(SEARCHED_INSTANCE_NAME, responseBody.getGridName());
    assertEquals(ZONE, responseBody.getZone());
    assertEquals(ResponseStatus.SUCCESS.name(), responseBody.getStatus());
    assertEquals(HttpStatus.OK.value(), responseBody.getHttpErrorStatusCode());
  }
}
