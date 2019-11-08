package com.zylitics.wzgp.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.google.api.services.compute.Compute;
import com.zylitics.wzgp.http.RequestGridCreate;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.compute.ComputeService;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.search.ResourceSearch;
import com.zylitics.wzgp.test.dummy.DummyAPICoreProperties;
import com.zylitics.wzgp.test.dummy.FakeCompute;
import com.zylitics.wzgp.web.exceptions.GridStartHandlerFailureException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness=Strictness.STRICT_STUBS)
class GridControllerTest {
  
  private static final String ZONE = "us-central0-g";
  
  private static final String GRID_NAME = "grid-1";
  
  private static final Compute COMPUTE = new FakeCompute().get();
  
  private static final APICoreProperties API_CORE_PROPS = new DummyAPICoreProperties();
  
  private static final ResourceExecutor EXECUTOR = mock(ResourceExecutor.class);
  
  private static final ComputeService COMPUTE_SRV = mock(ComputeService.class);
  
  private static final ResourceSearch SEARCH = mock(ResourceSearch.class);
  
  private static final FingerprintBasedUpdater FINGERPRINT_BASED_UPDATER =
      mock(FingerprintBasedUpdater.class);

  @Test
  @DisplayName("verify grid creates with source image family given")
  void gridCreatesWithSourceImageFamily() throws Exception {
    String sourceImageFamily = "win-2008-base";
    GridGenerateHandler generateHandler = mock(GridGenerateHandler.class);
    RequestGridCreate request = mock(RequestGridCreate.class);
    
    GridGenerateHandler.Factory generateHandlerFactory =
        getGridGenerateHandlerFactory(generateHandler, request);
    
    GridController controller = getGridController(generateHandlerFactory);
    
    controller.create(request, ZONE, false, sourceImageFamily);
    
    verify(generateHandler).setSourceImageFamily(sourceImageFamily);
    
    verify(generateHandler).handle();  // its ok to return nothing from controller's create, we've
    //already tested GridGenerateHandlerImpl separately to make sure the response is correct.
  }
  
  @Test
  @DisplayName("verify grid creates with no-rush given")
  void gridCreatesWithNoRush() throws Exception {
    GridGenerateHandler generateHandler = mock(GridGenerateHandler.class);
    RequestGridCreate request = mock(RequestGridCreate.class);
    
    GridGenerateHandler.Factory generateHandlerFactory =
        getGridGenerateHandlerFactory(generateHandler, request);
    
    GridController controller = getGridController(generateHandlerFactory);
    
    controller.create(request, ZONE, true, null);
    
    verify(generateHandler, never()).setSourceImageFamily(anyString());
    
    verify(generateHandler).handle();  // its ok to return nothing from controller's create, we've
    //already tested GridGenerateHandlerImpl separately to make sure the response is correct.
  }
  
  @Test
  @DisplayName("verify stopped grid searched and started when no-rush or source-image not given")
  void gridSearchedAndStarted() throws Exception {
    GridStartHandler startHandler = mock(GridStartHandler.class);
    RequestGridCreate request = mock(RequestGridCreate.class);
    
    GridStartHandler.Factory startHandlerFactory =
        getGridStartHandlerFactory(startHandler, request);
    
    GridController controller = getGridController(startHandlerFactory);
    
    controller.create(request, ZONE, false, null);
    
    verify(startHandler).handle();
  }
  
  @Test
  @DisplayName("verify stopped grid start 'known' failure lead to fresh grid generate")
  void gridStartedKnowFailureTriggerGridGenerate() throws Exception {
    gridStartedFailureTriggerGridGenerate(GridStartHandlerFailureException.class);
  }
  
  @Test
  @DisplayName("verify stopped grid start 'unknown' failure lead to fresh grid generate")
  void gridStartedUnknownFailureTriggerGridGenerate() throws Exception {
    gridStartedFailureTriggerGridGenerate(RuntimeException.class);
  }
  
  void gridStartedFailureTriggerGridGenerate(Class<? extends Throwable> failureType)
      throws Exception {
    GridGenerateHandler generateHandler = mock(GridGenerateHandler.class);
    
    GridStartHandler startHandler = mock(GridStartHandler.class);
    when(startHandler.handle()).thenThrow(failureType);
    
    RequestGridCreate request = mock(RequestGridCreate.class);
    
    GridGenerateHandler.Factory generateHandlerFactory =
        getGridGenerateHandlerFactory(generateHandler, request);
    
    GridStartHandler.Factory startHandlerFactory =
        getGridStartHandlerFactory(startHandler, request);
    
    GridController controller = getGridController(generateHandlerFactory, startHandlerFactory);
    
    controller.create(request, ZONE, false, null);
    
    verify(startHandler).handle();
    
    verify(generateHandler).handle();
  }
  
  @Test
  @DisplayName("verify grid delete handler invokes")
  void gridDeleteHandlerInvoke() throws Exception {
    String sessionId = "session-1";
    GridDeleteHandler deleteHandler = mock(GridDeleteHandler.class);
    
    GridDeleteHandler.Factory deleteHandlerFactory =
        getGridDeleteHandlerFactory(deleteHandler);
    
    GridController controller = getGridController(deleteHandlerFactory);
    
    controller.delete(ZONE, GRID_NAME, false, sessionId);
    
    verify(deleteHandler).setSessionId(sessionId);
    
    verify(deleteHandler).setNoRush(false);
    
    verify(deleteHandler).handle();  // its ok to return nothing from controller's create, we've
    //already tested GridDeleteHandlerImpl separately to make sure the response is correct.
  }
  
  private GridController getGridController(GridGenerateHandler.Factory gridGenerateHandlerFactory
      , GridStartHandler.Factory gridStartHandlerFactory) {
    return new GridController(COMPUTE, API_CORE_PROPS, EXECUTOR, COMPUTE_SRV, SEARCH
        , FINGERPRINT_BASED_UPDATER, gridGenerateHandlerFactory, gridStartHandlerFactory
        , mock(GridDeleteHandler.Factory.class));
  }
  
  private GridController getGridController(GridGenerateHandler.Factory gridGenerateHandlerFactory) {
    return new GridController(COMPUTE, API_CORE_PROPS, EXECUTOR, COMPUTE_SRV, SEARCH
        , FINGERPRINT_BASED_UPDATER, gridGenerateHandlerFactory
        , mock(GridStartHandler.Factory.class), mock(GridDeleteHandler.Factory.class));
  }
  
  private GridController getGridController(GridStartHandler.Factory gridStartHandlerFactory) {
    return new GridController(COMPUTE, API_CORE_PROPS, EXECUTOR, COMPUTE_SRV, SEARCH
        , FINGERPRINT_BASED_UPDATER, mock(GridGenerateHandler.Factory.class)
        , gridStartHandlerFactory, mock(GridDeleteHandler.Factory.class));
  }
  
  private GridController getGridController(GridDeleteHandler.Factory gridDeleteHandlerFactory) {
    return new GridController(COMPUTE, API_CORE_PROPS, EXECUTOR, COMPUTE_SRV, SEARCH
        , FINGERPRINT_BASED_UPDATER, mock(GridGenerateHandler.Factory.class)
        , mock(GridStartHandler.Factory.class), gridDeleteHandlerFactory);
  }
  
  private GridGenerateHandler.Factory getGridGenerateHandlerFactory(GridGenerateHandler handler
      , RequestGridCreate request) {
    GridGenerateHandler.Factory factory = mock(GridGenerateHandler.Factory.class);
    when(factory.create(COMPUTE, API_CORE_PROPS, EXECUTOR, COMPUTE_SRV, SEARCH
        , FINGERPRINT_BASED_UPDATER, ZONE, request)).thenReturn(handler);
    return factory;
  }
  
  private GridStartHandler.Factory getGridStartHandlerFactory(GridStartHandler handler
      , RequestGridCreate request) {
    GridStartHandler.Factory factory = mock(GridStartHandler.Factory.class);
    when(factory.create(API_CORE_PROPS, EXECUTOR, COMPUTE_SRV, SEARCH, FINGERPRINT_BASED_UPDATER
        , ZONE, request)).thenReturn(handler);
    return factory;
  }
  
  private GridDeleteHandler.Factory getGridDeleteHandlerFactory(GridDeleteHandler handler) {
    GridDeleteHandler.Factory factory = mock(GridDeleteHandler.Factory.class);
    when(factory.create(API_CORE_PROPS, EXECUTOR, COMPUTE_SRV, FINGERPRINT_BASED_UPDATER, ZONE
        , GRID_NAME)).thenReturn(handler);
    return factory;
  }
}
