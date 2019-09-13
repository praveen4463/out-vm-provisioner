package com.zylitics.wzgp.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.Compute.Instances;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Operation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.zylitics.wzgp.http.RequestGridCreate;
import com.zylitics.wzgp.http.ResponseGridCreate;
import com.zylitics.wzgp.http.ResponseStatus;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.CompletedOperation;
import com.zylitics.wzgp.resource.compute.ComputeService;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.util.ResourceUtil;
import com.zylitics.wzgp.test.dummy.DummyAPICoreProperties;
import com.zylitics.wzgp.test.dummy.DummyRequestGridCreate;
import com.zylitics.wzgp.test.dummy.FakeCompute;
import com.zylitics.wzgp.test.util.ResourceTestUtil;
import com.zylitics.wzgp.web.exceptions.GridNotCreatedException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness=Strictness.STRICT_STUBS)
public class GridGenerateHandlerImplTest {
  
  private static final String ZONE = "us-central0-g";
  
  private static final String GENERATED_INSTANCE_NAME = "grid-1";
  
  private static final BigInteger GENERATED_INSTANCE_ID = BigInteger.valueOf(2020239494);
  
  private static final String GENERATED_INSTANCE_NETWORK_IP = "192.168.1.1";
  
  private static final String IMAGE_NAME = "image-1";

  private static final Compute COMPUTE = new FakeCompute().get();
  
  private static final APICoreProperties API_CORE_PROPS = new DummyAPICoreProperties();
  
  private static final RequestGridCreate REQ_CREATE = new DummyRequestGridCreate().get();
  
  private static final BuildProperty BUILD_PROP = REQ_CREATE.getBuildProperties();
  
  @Test
  @DisplayName("verify handler creates grid when source image family is provided")
  void handlerWithSourceImageTest() throws Exception {
    String sourceImageFamily = "image-family-1";
    Image image = new Image()
        .setName(IMAGE_NAME)
        .setFamily(sourceImageFamily)
        .setLabels(ImmutableMap.of("os","win7"));
    
    Instance generatedInstance = getGeneratedInstance();
    
    ResourceExecutor executor = mock(ResourceExecutor.class);
    ComputeService computeSrv = mock(ComputeService.class);
    FingerprintBasedUpdater fingerprintBasedUpdater = mock(FingerprintBasedUpdater.class);
    
    when(computeSrv.getImageFromFamily(sourceImageFamily, BUILD_PROP))
        .thenReturn(image);
    
    stubGridGenerate(executor, sourceImageFamily, true);
    
    when(computeSrv.getInstance(GENERATED_INSTANCE_NAME, ZONE, BUILD_PROP))
        .thenReturn(generatedInstance);
    
    Operation lockGridOperation = stubGridLocking(executor, fingerprintBasedUpdater
        , generatedInstance);

    GridGenerateHandler handler = getHandler(executor, computeSrv, fingerprintBasedUpdater);
    
    handler.setSourceImageFamily(sourceImageFamily);
    
    ResponseEntity<ResponseGridCreate> response = handler.handle();
    
    assertEquals("DONE", lockGridOperation.getStatus());
    
    validateResonse(response);
  }
  
  @Test
  @DisplayName("verify handler creates grid without source image family")
  void handlerWithoutSourceImageTest() throws Exception {
    String searchedImageFamily = "any-family";
    Image searchedImage = new Image()
        .setName(IMAGE_NAME)
        .setFamily(searchedImageFamily)
        .setLabels(ImmutableMap.of("os","win7"));
    
    Instance generateInstance = getGeneratedInstance();
    
    ResourceExecutor executor = mock(ResourceExecutor.class);
    ComputeService computeSrv = mock(ComputeService.class);
    FingerprintBasedUpdater fingerprintBasedUpdater = mock(FingerprintBasedUpdater.class);
    
    when(computeSrv.listImages(anyString(), eq(1L), eq(BUILD_PROP)))
        .thenReturn(ImmutableList.of(searchedImage));
    
    stubGridGenerate(executor, searchedImageFamily, true);
    
    when(computeSrv.getInstance(GENERATED_INSTANCE_NAME, ZONE, BUILD_PROP))
        .thenReturn(generateInstance);
    
    Operation lockGridOperation = stubGridLocking(executor, fingerprintBasedUpdater
        , generateInstance);

    GridGenerateHandler handler = getHandler(executor, computeSrv, fingerprintBasedUpdater);
    
    ResponseEntity<ResponseGridCreate> response = handler.handle();
    
    assertEquals("DONE", lockGridOperation.getStatus());
    
    validateResonse(response);
  }
  
  @Test
  @DisplayName("verify GridGenerator failure raises exception")
  void gridGeneratorFailureTest() throws Exception {
    String searchedImageFamily = "any-family";
    Image searchedImage = new Image()
        .setName(IMAGE_NAME)
        .setFamily(searchedImageFamily)
        .setLabels(ImmutableMap.of("os","win7"));
    
    ResourceExecutor executor = mock(ResourceExecutor.class);
    ComputeService computeSrv = mock(ComputeService.class);
    FingerprintBasedUpdater fingerprintBasedUpdater = mock(FingerprintBasedUpdater.class);
    
    when(computeSrv.listImages(anyString(), eq(1L), eq(BUILD_PROP)))
        .thenReturn(ImmutableList.of(searchedImage));
    
    stubGridGenerate(executor, searchedImageFamily, false);
    
    // No other stubbing required as we want grid generator to respond negatively.
    
    GridGenerateHandler handler = getHandler(executor, computeSrv, fingerprintBasedUpdater);
    
    assertThrows(GridNotCreatedException.class, () -> handler.handle());
  }
  
  private GridGenerateHandler getHandler(ResourceExecutor executor, ComputeService computeSrv
      , FingerprintBasedUpdater finerprintUpdater) {
    return new GridGenerateHandlerImpl.Factory().create(
        COMPUTE, API_CORE_PROPS, executor, computeSrv, finerprintUpdater, ZONE, REQ_CREATE);
  }
  
  private Instance getGeneratedInstance() {
    return new Instance()
        .setId(GENERATED_INSTANCE_ID)
        .setName(GENERATED_INSTANCE_NAME)
        .setNetworkInterfaces(
            ImmutableList.of(new NetworkInterface().setNetworkIP(GENERATED_INSTANCE_NETWORK_IP)))
        .setZone(ResourceTestUtil.getZoneLink(ZONE))
        .setStatus("RUNNING");
  }
  
  @SuppressWarnings("unchecked")
  private void stubGridGenerate(ResourceExecutor executor, String sourceImageFamily
      , boolean shouldSucceed)
      throws Exception {
    when(executor.executeWithZonalReattempt(any(Instances.Insert.class), any(Function.class)
        , any(BuildProperty.class))).then(invocation -> {
          Instances.Insert insertInstanceProvided = (Instances.Insert) invocation.getArgument(0);
          Instance instance = (Instance) insertInstanceProvided.getJsonContent();
          if (!ResourceUtil.nameFromUrl(instance.getDisks().get(0).getInitializeParams()
              .getSourceImage()).equals(sourceImageFamily)) {
            throw new RuntimeException("GridGenerator didn't get the valid source-image-family");
          }
          return new CompletedOperation(getOperation(GENERATED_INSTANCE_NAME, ZONE, shouldSucceed));
        });
  }
  
  private Operation stubGridLocking(ResourceExecutor executor
      , FingerprintBasedUpdater fingerprintBasedUpdater, Instance gridInstance) throws Exception {
    Map<String, String> lockGridLabel =
        ImmutableMap.of(ResourceUtil.LABEL_LOCKED_BY_BUILD, BUILD_PROP.getBuildId());
    Operation lockGridOperation = new Operation().setStatus("RUNNING");
    when(fingerprintBasedUpdater.updateLabels(gridInstance, lockGridLabel, BUILD_PROP))
        .thenReturn(lockGridOperation);
    when(executor.blockUntilComplete(lockGridOperation, BUILD_PROP))
        .then(inv -> {
          lockGridOperation.setStatus("DONE");
          return lockGridOperation;
        });
    return lockGridOperation;
  }
  
  private void validateResonse(ResponseEntity<ResponseGridCreate> response) {
    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    
    ResponseGridCreate responseBody = response.getBody();
    
    assertEquals(GENERATED_INSTANCE_NETWORK_IP, responseBody.getGridInternalIP());
    assertEquals(GENERATED_INSTANCE_ID, responseBody.getGridId());
    assertEquals(GENERATED_INSTANCE_NAME, responseBody.getGridName());
    assertEquals(ZONE, responseBody.getZone());
    assertEquals(ResponseStatus.SUCCESS.name(), responseBody.getStatus());
    assertEquals(HttpStatus.CREATED.value(), responseBody.getHttpStatusCode());
  }
  
  private Operation getOperation(String resourceName, String zone, boolean isSuccess) {
    return new Operation()
        .setHttpErrorStatusCode(
            isSuccess? null : HttpStatus.INTERNAL_SERVER_ERROR.value())
        .setStatus("DONE")
        .setName("operation-" + UUID.randomUUID())
        .setTargetLink(ResourceTestUtil.getOperationTargetLink(resourceName, zone))
        .setZone(ResourceTestUtil.getZoneLink(zone));
  }
}
