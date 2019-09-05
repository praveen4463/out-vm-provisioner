package com.zylitics.wzgp.resource.executor;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.http.HttpStatus;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.Compute.Instances;
import com.google.api.services.compute.Compute.ZoneOperations;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.Operation.Error;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.CompletedOperation;
import com.zylitics.wzgp.resource.util.ResourceUtil;
import com.zylitics.wzgp.test.dummy.DummyAPICoreProperties;
import com.zylitics.wzgp.test.dummy.DummyRequestGridCreate;
import com.zylitics.wzgp.test.dummy.FakeCompute;
import com.zylitics.wzgp.test.util.FlexibleOffsetClock;
import com.zylitics.wzgp.test.util.ResourceTestUtil;

public class ResourceExecutorImplTest {

  private static final BuildProperty BUILD_PROP =
      new DummyRequestGridCreate().get().getBuildProperties();
  
  // shouldn't be in our re-attempt zone list.
  private static final String PRIMARY_ZONE = "us-central0-g";
  
  private static final String INSTANCE_NAME = "instance-a-1";
  
  private static final Instance INSTANCE = new Instance();
  
  private static final Compute COMPUTE = new FakeCompute().get();
  
  private static final Instances MOCK_INSTANCES =
      mock(Instances.class, withSettings().useConstructor().outerInstance(COMPUTE));
  
  private static final APICoreProperties API_CORE_PROPS = new DummyAPICoreProperties();
  
  private static final ResourceExecutor EXECUTOR =
      new ResourceExecutorImpl(COMPUTE, API_CORE_PROPS);
  
  @TestFactory
  Stream<DynamicTest> executeComputeRequestTest() throws Exception {
    
    HttpResponseException httpResponseException500 =
        new HttpResponseException.Builder(HttpStatus.INTERNAL_SERVER_ERROR.value()
            , "Internal Server Error", new HttpHeaders()).build();
    
    return Stream.of(
          dynamicTest("verify execute returns desired 'object' when no http error", () -> {
            
            Instances.Get mockGetInstance = getMockGetInstance(PRIMARY_ZONE, INSTANCE_NAME);
            
            when(mockGetInstance.execute()).thenReturn(INSTANCE);
            
            assertEquals(INSTANCE, EXECUTOR.executeWithReattempt(mockGetInstance, BUILD_PROP));
          }),
          
          dynamicTest("verify exception thrown on execute when http error occurs", () -> {
            Instances.Get mockGetInstance = getMockGetInstance(PRIMARY_ZONE, INSTANCE_NAME);
            
            when(mockGetInstance.execute()).thenThrow(httpResponseException500);
            
            assertThrows(IOException.class
                , () -> EXECUTOR.executeWithReattempt(mockGetInstance, BUILD_PROP));
          })
          
          // TODO: write more tests when we've logic built for the re-attempt on http status errors.
        );
  }
  
  @TestFactory
  Stream<DynamicTest> executeComputeRequestWithZonalReattemptTest() throws Exception {
    List<String> zonalErrors = new ArrayList<>(API_CORE_PROPS.getGceZonalReattemptErrors());
    
    return Stream.of(
          dynamicTest("verify execute returns successful 'Operation' when no api error", () -> {
            
            Operation successfulOperation = getSuccessfulOperation(INSTANCE_NAME, PRIMARY_ZONE
                , "DONE"); // DONE is used so that blockUntilComplete method doesn't run fully.
            
            Instances.Insert mockInsertInstance = getMockInsertInstance(PRIMARY_ZONE, INSTANCE);
            
            when(mockInsertInstance.execute()).thenReturn(successfulOperation);
            
            CompletedOperation returnedOperation = EXECUTOR.executeWithZonalReattempt(
                mockInsertInstance, null, BUILD_PROP);
            
            assertTrue(ResourceUtil.isOperationSuccess(returnedOperation.get()));
          }),
          
          dynamicTest("verify 'Operation' fails before reattempting when code mismatch", () ->
          {
            Operation unsuccessfulOperation = getUnSuccessfulOperation(INSTANCE_NAME, PRIMARY_ZONE
                , "DONE", "UNKNOWN_CODE1", "UNKNOWN_CODE2");
            // send unknown codes.
            
            Instances.Insert mockInsertInstance = getMockInsertInstance(PRIMARY_ZONE, INSTANCE);
            
            @SuppressWarnings("unchecked")
            Function<String, Instances.Insert> factoryForInsertPerZone =
                (Function<String, Instances.Insert>) spy(Function.class);
            
            when(factoryForInsertPerZone.apply(anyString())).thenReturn(mockInsertInstance);
            
            when(mockInsertInstance.execute()).thenReturn(unsuccessfulOperation);
            
            CompletedOperation returnedOperation = EXECUTOR.executeWithZonalReattempt(
                mockInsertInstance, factoryForInsertPerZone, BUILD_PROP);
            
            assertFalse(ResourceUtil.isOperationSuccess(returnedOperation.get()));
            
            verify(factoryForInsertPerZone, never()).apply(anyString());
          }),
          
          dynamicTest("verify 'Operation' succeeds on reattempts", () -> {
            Operation unsuccessfulOperation = getUnSuccessfulOperation(INSTANCE_NAME, PRIMARY_ZONE
                , "DONE", zonalErrors.get(0));  // send a known code.
            
            Instances.Insert mockInsertInstancePrimaryZone =
                getMockInsertInstance(PRIMARY_ZONE, INSTANCE);
            
            when(mockInsertInstancePrimaryZone.execute()).thenReturn(unsuccessfulOperation);
            
            @SuppressWarnings("unchecked")
            Function<String, Instances.Insert> factoryForInsertPerZone =
                (Function<String, Instances.Insert>) spy(Function.class);
            
            Random random = new Random();
            when(factoryForInsertPerZone.apply(anyString())).thenAnswer(invocation -> {
              String randomZone = invocation.getArgument(0);
              
              // generate new Instances.Insert for this zone.
              Instance newZoneInstance = new Instance();
              
              newZoneInstance.setZone(ResourceTestUtil.getZoneLink(randomZone));
              
              Instances.Insert mockInsertInstanceRandomZone =
                  getMockInsertInstance(randomZone, newZoneInstance);
              
              // succeed on the any re-attempt. Randomly selects between successful and unsuccessful
              // operation response. This verifies our re-attempt logic is strong enough to sustain
              // a few unsuccessful responses, thus the maximum re-attempts should be > 4
              //TODO: we may apply some logic so that if random.nextBoolean() is false for max
              // re-attempt times, we force it to true so tests won't fail.  
              Operation operation = random.nextBoolean()
                  ? getSuccessfulOperation(INSTANCE_NAME, randomZone, "DONE")
                  : getUnSuccessfulOperation(INSTANCE_NAME, randomZone, "DONE", zonalErrors.get(0));
              when(mockInsertInstanceRandomZone.execute()).thenReturn(operation);
              
              return mockInsertInstanceRandomZone;
            });
            
            CompletedOperation returnedOperation = EXECUTOR.executeWithZonalReattempt(
                mockInsertInstancePrimaryZone, factoryForInsertPerZone, BUILD_PROP);
            
            assertTrue(ResourceUtil.isOperationSuccess(returnedOperation.get()));
            
            String returnedOperationZone =
                ResourceUtil.nameFromUrl(returnedOperation.get().getZone());
            
            assertNotEquals(returnedOperationZone, PRIMARY_ZONE);
            
            assertTrue(API_CORE_PROPS.getGceReattemptZones().contains(returnedOperationZone));
            // verify, random zone is taken from our list.
            
          }),
          
          dynamicTest("verify 'Operation' fails after reattempting when code mismatch", () -> {
            Operation unsuccessfulOperation = getUnSuccessfulOperation(INSTANCE_NAME, PRIMARY_ZONE
                , "DONE", zonalErrors.get(0));  // send a known code.
            
            Instances.Insert mockInsertInstancePrimaryZone =
                getMockInsertInstance(PRIMARY_ZONE, INSTANCE);
            
            when(mockInsertInstancePrimaryZone.execute()).thenReturn(unsuccessfulOperation);
            
            @SuppressWarnings("unchecked")
            Function<String, Instances.Insert> factoryForInsertPerZone =
                (Function<String, Instances.Insert>) spy(Function.class);
            
            Random random = new Random();
            when(factoryForInsertPerZone.apply(anyString())).thenAnswer(invocation -> {
              String randomZone = invocation.getArgument(0);
              
              // generate new Instances.Insert for this zone.
              Instance newZoneInstance = new Instance();
              
              newZoneInstance.setZone(ResourceTestUtil.getZoneLink(randomZone));
              
              Instances.Insert mockInsertInstanceRandomZone =
                  getMockInsertInstance(randomZone, newZoneInstance);
              
              // get known or unknown code randomly so that we get failed operation during any
              // re-attempt.
              String errorCode = random.nextBoolean() ? "UNKNOWN_CODE1" : zonalErrors.get(0);
              
              Operation unsuccessfulOpRandomCode =
                  getUnSuccessfulOperation(INSTANCE_NAME, randomZone, "DONE", errorCode);
              
              when(mockInsertInstanceRandomZone.execute()).thenReturn(unsuccessfulOpRandomCode);
              
              return mockInsertInstanceRandomZone;
            });
            
            CompletedOperation returnedOperation = EXECUTOR.executeWithZonalReattempt(
                mockInsertInstancePrimaryZone, factoryForInsertPerZone, BUILD_PROP);
            
            assertFalse(ResourceUtil.isOperationSuccess(returnedOperation.get()));
          }),
          
          dynamicTest("verify 'Operation' fails when max reattempt reaches", () -> {
            Operation unsuccessfulOperation = getUnSuccessfulOperation(INSTANCE_NAME, PRIMARY_ZONE
                , "DONE", zonalErrors.get(0));  // send a known code.
            
            Instances.Insert mockInsertInstancePrimaryZone =
                getMockInsertInstance(PRIMARY_ZONE, INSTANCE);
            
            when(mockInsertInstancePrimaryZone.execute()).thenReturn(unsuccessfulOperation);
            
            @SuppressWarnings("unchecked")
            Function<String, Instances.Insert> factoryForInsertPerZone =
                (Function<String, Instances.Insert>) spy(Function.class);
            
            when(factoryForInsertPerZone.apply(anyString())).thenAnswer(invocation -> {
              String randomZone = invocation.getArgument(0);
              
              // generate new Instances.Insert for this zone.
              Instance newZoneInstance = new Instance();
              
              newZoneInstance.setZone(ResourceTestUtil.getZoneLink(randomZone));
              
              Instances.Insert mockInsertInstanceRandomZone =
                  getMockInsertInstance(randomZone, newZoneInstance);
              
              Operation unsuccessfulOpKnownCode =
                  getUnSuccessfulOperation(INSTANCE_NAME, randomZone, "DONE", zonalErrors.get(0));
              
              when(mockInsertInstanceRandomZone.execute()).thenReturn(unsuccessfulOpKnownCode);
              
              return mockInsertInstanceRandomZone;
            });
            
            CompletedOperation returnedOperation = EXECUTOR.executeWithZonalReattempt(
                mockInsertInstancePrimaryZone, factoryForInsertPerZone, BUILD_PROP);
            
            assertFalse(ResourceUtil.isOperationSuccess(returnedOperation.get()));
            
            verify(factoryForInsertPerZone
                , times(ResourceExecutorImpl.ZONAL_ISSUES_MAX_REATTEMPTS)).apply(anyString());
          })
        );
    
  }
  
  @TestFactory
  Stream<DynamicTest> blockUntilCompleteTest() throws Exception {
    
    return Stream.of(
          dynamicTest("verify 'Operation' completes before timeout", () -> {
            
            ResourceExecutorImpl executor = mock(ResourceExecutorImpl.class
                , withSettings().useConstructor(COMPUTE, API_CORE_PROPS)
                    .defaultAnswer(CALLS_REAL_METHODS));
            
            Clock fixedClock = Clock.fixed(Instant.now(), ZoneId.of("UTC"));
            
            doReturn(getOpForStatusCheck("PENDING"))
                .doReturn(getOpForStatusCheck("RUNNING"))
                .doReturn(getOpForStatusCheck("DONE"))
                .when(executor).executeWithReattempt(any(ZoneOperations.Get.class), eq(BUILD_PROP));
            
            Operation completedOperation = executor.blockUntilComplete(
                getOpForStatusCheck("PENDING"), 0, fixedClock, BUILD_PROP);
            assertEquals("DONE", completedOperation.getStatus());
          }),
          
          dynamicTest("verify 'Operation' timeouts before completion", () -> {
            
            ResourceExecutorImpl executor = mock(ResourceExecutorImpl.class
                , withSettings().useConstructor(COMPUTE, API_CORE_PROPS)
                    .defaultAnswer(CALLS_REAL_METHODS));
            
            FlexibleOffsetClock flexiClock = new FlexibleOffsetClock(Clock.systemUTC()
                , Duration.ofNanos(0));
            
            // let the process check for status a few times before moving the time forward.
            doReturn(getOpForStatusCheck("PENDING"))
                .doReturn(getOpForStatusCheck("PENDING"))
                .doReturn(getOpForStatusCheck("RUNNING"))
                .doAnswer(invocation -> {
                  // choose an offset > API_CORE_PROPS.getGceTimeoutMillis()
                  flexiClock.setOffset(Duration.ofMillis(API_CORE_PROPS.getGceTimeoutMillis() + 1));
                  return getOpForStatusCheck("RUNNING");
                })
                .when(executor).executeWithReattempt(any(ZoneOperations.Get.class), eq(BUILD_PROP));
            
            assertThrows(TimeoutException.class, () -> {
              executor.blockUntilComplete(
                  getOpForStatusCheck("PENDING"), 0, flexiClock, BUILD_PROP);
            });
          })
        );
  }
  
  private Operation getOpForStatusCheck(String status) {
    return new Operation()
        .setStatus(status)
        .setName("operation-" + UUID.randomUUID())
        .setZone(ResourceTestUtil.getZoneLink(PRIMARY_ZONE));
  }
  
  private Operation getSuccessfulOperation(String resourceName, String zone, String status) {
    return new Operation()
        .setStatus(status)
        .setName("operation-" + UUID.randomUUID())
        .setTargetLink(ResourceTestUtil.getOperationTargetLink(resourceName, zone))
        .setZone(ResourceTestUtil.getZoneLink(zone));
  }
  
  private Operation getUnSuccessfulOperation(String resourceName, String zone, String status
      , String... codes) {
    java.util.List<Error.Errors> listErrors = new ArrayList<>();
    for(String code : codes) {
      Error.Errors newError = new Error.Errors();
      newError.setCode(code);
      listErrors.add(newError);
    }
    Error error = new Error();
    error.setErrors(listErrors);
    
    return new Operation()
        .setHttpErrorStatusCode(HttpStatus.TOO_MANY_REQUESTS.value())
        .setStatus(status)
        .setName("operation-" + UUID.randomUUID())
        .setTargetLink(ResourceTestUtil.getOperationTargetLink(resourceName, zone))
        .setZone(ResourceTestUtil.getZoneLink(zone))
        .setError(error);
  }
  
  private Instances.Insert getMockInsertInstance(String zone, Instance instance) {
    return mock(Instances.Insert.class, withSettings()
        .useConstructor(API_CORE_PROPS.getProjectId(), zone, instance)
        .outerInstance(MOCK_INSTANCES));
  }
  
  private Instances.Get getMockGetInstance(String zone, String instanceName) {
    return mock(Instances.Get.class, withSettings()
        .useConstructor(API_CORE_PROPS.getProjectId(), zone, instanceName)
        .outerInstance(MOCK_INSTANCES));
  }
}
