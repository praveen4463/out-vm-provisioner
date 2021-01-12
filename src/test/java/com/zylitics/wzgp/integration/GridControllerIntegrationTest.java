package com.zylitics.wzgp.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import org.assertj.core.util.Strings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.compute.Compute;
import com.google.common.collect.ImmutableMap;
import com.zylitics.wzgp.http.RequestGridCreate;
import com.zylitics.wzgp.http.ResponseGridCreate;
import com.zylitics.wzgp.http.ResponseGridDelete;
import com.zylitics.wzgp.http.ResponseStatus;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.compute.ComputeService;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.search.ResourceSearch;
import com.zylitics.wzgp.test.dummy.DummyRequestGridCreate;
import com.zylitics.wzgp.test.dummy.FakeCompute;
import com.zylitics.wzgp.web.FingerprintBasedUpdater;
import com.zylitics.wzgp.web.GridDeleteHandler;
import com.zylitics.wzgp.web.GridGenerateHandler;
import com.zylitics.wzgp.web.GridStartHandler;

@Tag("integration")
@Tag("in-container")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness=Strictness.STRICT_STUBS)
@SpringBootTest(webEnvironment=WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@SuppressWarnings("unused")
class GridControllerIntegrationTest {
  
  private static final String ZONE = "us-central0-g";
  
  private static final String APP_VER_KEY = "app-short-version";

  private static final String GRID_NAME = "grid-1";
  
  private static final GridGenerateHandler.Factory GENERATE_HANDLER_FACTORY =
      mock(GridGenerateHandler.Factory.class);
  private static final GridStartHandler.Factory START_HANDLER_FACTORY =
      mock(GridStartHandler.Factory.class);
  private static final GridDeleteHandler.Factory DELETE_HANDLER_FACTORY =
      mock(GridDeleteHandler.Factory.class);
  
  private static final GridGenerateHandler GENERATE_HANDLER = mock(GridGenerateHandler.class);
  private static final GridStartHandler START_HANDLER = mock(GridStartHandler.class);
  private static final GridDeleteHandler DELETE_HANDLER = mock(GridDeleteHandler.class);
  
  @Autowired
  private MockMvc mvc;
  
  @Autowired
  private Environment env;
  
  @Autowired
  private Compute compute;
  
  @Autowired
  private APICoreProperties apiCoreProps;
  
  @Autowired
  private ResourceExecutor executor;
  
  @Autowired
  private ComputeService computeSrv;
  
  @Autowired
  private ResourceSearch search;
  
  @Autowired
  private FingerprintBasedUpdater fingerprintBasedUpdater;
  
  private JacksonTester<RequestGridCreate> createReq;
  
  @BeforeEach
  void setup() {
    // Reset all handler mocks so that distinct stubbing can take place in distinct test methods. We
    // had to reset rather than creating new mock because container use the same handler factory
    // objects in all tests
    reset(GENERATE_HANDLER_FACTORY);
    reset(START_HANDLER_FACTORY);
    reset(DELETE_HANDLER_FACTORY);
    
    reset(GENERATE_HANDLER);
    reset(START_HANDLER);
    reset(DELETE_HANDLER);
  }
  
  @Test
  @DisplayName("verify accessibility of post endpoints together with validity of request/response")
  void postReqEndpointTest() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    JacksonTester.initFields(this, mapper);
    
    String sourceImageFamily = "family-1";
    RequestGridCreate requestSent = new DummyRequestGridCreate().get();
    
    when(GENERATE_HANDLER_FACTORY.create(eq(compute), eq(apiCoreProps), eq(executor)
        , eq(computeSrv), eq(search), eq(fingerprintBasedUpdater), eq(ZONE)
        , any(RequestGridCreate.class))).then(invocation -> {
          RequestGridCreate requestReceived = invocation.getArgument(7);
          assertEquals(requestSent, requestReceived);
          return GENERATE_HANDLER;
        });
    
    when(GENERATE_HANDLER.handle()).thenReturn(
          ResponseEntity.status(HttpStatus.CREATED).body(gridCreateResponse()));
    
    mvc.perform(
          post("/{version}/zones/{zone}/grids", env.getProperty(APP_VER_KEY), ZONE)
              .param("noRush", "true")
              .param("sourceImageFamily", sourceImageFamily)
              .accept(MediaType.APPLICATION_JSON)
              .contentType(MediaType.APPLICATION_JSON)
              .content(createReq.write(requestSent).getJson().getBytes(StandardCharsets.UTF_8))
        )
        .andExpect(status().isCreated())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.gridName").value(GRID_NAME))
        .andExpect(jsonPath("$.zone").value(ZONE))
        .andExpect(jsonPath("$.status").value(ResponseStatus.SUCCESS.name()));
    
    verify(GENERATE_HANDLER).setSourceImageFamily(sourceImageFamily);
  }
  
  @Test
  @DisplayName("verify insufficient request fails and returns error")
  void postReqInsufficientData() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    JacksonTester.initFields(this, mapper);
    
    String sourceImageFamily = "family-1";
    
    RequestGridCreate requestSent = new RequestGridCreate();
    requestSent.getBuildProperties().setBuildId(null);
    requestSent.getGridProperties().setMetadata(ImmutableMap.of("screen", "1x1"));
    
    mvc.perform(
          post("/{version}/zones/{zone}/grids", env.getProperty(APP_VER_KEY), ZONE)
              .param("noRush", "true")
              .param("sourceImageFamily", sourceImageFamily)
              .accept(MediaType.APPLICATION_JSON)
              .contentType(MediaType.APPLICATION_JSON)
              .content(createReq.write(requestSent).getJson().getBytes(StandardCharsets.UTF_8))
        )
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(ResponseStatus.FAILURE.name()));
    
    verifyNoInteractions(GENERATE_HANDLER_FACTORY);
    verifyNoInteractions(GENERATE_HANDLER);
  }
  
  @Test
  @DisplayName("verify accessibility of delete endpoints")
  void deleteReqEndpointTest() throws Exception {
    String sessionId = "session-1";
    
    when(DELETE_HANDLER_FACTORY.create(apiCoreProps, executor, computeSrv, fingerprintBasedUpdater
        , ZONE, GRID_NAME)).thenReturn(DELETE_HANDLER);
    
    when(DELETE_HANDLER.handle()).thenReturn(
          ResponseEntity.status(HttpStatus.OK).body(gridDeleteResponse()));
    
    mvc.perform(
          delete("/{version}/zones/{zone}/grids/{gridName}", env.getProperty(APP_VER_KEY), ZONE
              , GRID_NAME)
              .param("noRush", "true")
              .param("sessionId", sessionId)
              .accept(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.zone").value(ZONE))
        .andExpect(jsonPath("$.status").value(ResponseStatus.SUCCESS.name()));
    
    verify(DELETE_HANDLER).setSessionId(sessionId);
    verify(DELETE_HANDLER).setNoRush(true);
  }
  
  @Test
  @DisplayName("verify injected ApiCoreProperties was loaded correctly by container")
  void validateApiCoreProperties() {
    assertEquals("zl-win-nodes", apiCoreProps.getProjectId());
    assertTrue(apiCoreProps.getGceTimeoutMillis() > 0);
    assertTrue(apiCoreProps.getGceZonalReattemptErrors().size() > 0);
    assertTrue(apiCoreProps.getGceReattemptZones().size() > 0);
    
    APICoreProperties.GridDefault gridDefault = apiCoreProps.getGridDefault();
    
    assertEquals("zl-default-vpc", gridDefault.getNetwork());
    assertFalse(Strings.isNullOrEmpty(gridDefault.getMachineType()));
    assertFalse(Strings.isNullOrEmpty(gridDefault.getServiceAccount()));
    assertTrue(gridDefault.getTags().size() > 0);
    assertTrue(gridDefault.getLabels().size() > 0);
    assertTrue(gridDefault.getMetadata().size() > 0);
    assertTrue(gridDefault.getImageSpecificLabelsKey().size() > 0);
    assertTrue(gridDefault.getInstanceSearchParams().size() > 0);
    assertTrue(gridDefault.getImageSearchParams().size() > 0);
    assertTrue(gridDefault.getMaxStoppedInstanceInSearch() > 0);
  }
  
  private ResponseGridCreate gridCreateResponse() {
    ResponseGridCreate response = new ResponseGridCreate();
    response.setGridName(GRID_NAME);
    response.setHttpStatusCode(HttpStatus.CREATED.value());
    response.setStatus(ResponseStatus.SUCCESS.name());
    response.setZone(ZONE);
    return response;
  }
  
  private ResponseGridDelete gridDeleteResponse() {
    ResponseGridDelete response = new ResponseGridDelete();
    response.setHttpStatusCode(HttpStatus.OK.value());
    response.setStatus(ResponseStatus.SUCCESS.name());
    response.setZone(ZONE);
    return response;
  }

  @TestConfiguration
  static class TestConfig {
    
    @Bean
    public Compute compute() {
      return new FakeCompute().get();
    }
    
    @Bean
    public GridGenerateHandler.Factory gridGenerateHandlerFactory() {
      return GENERATE_HANDLER_FACTORY;
    }
    
    @Bean
    public GridStartHandler.Factory gridStartHandlerFactory() {
      return START_HANDLER_FACTORY;
    }
    
    @Bean
    public GridDeleteHandler.Factory gridDeleteHandlerFactory() {
      return DELETE_HANDLER_FACTORY;
    }
  }
}
