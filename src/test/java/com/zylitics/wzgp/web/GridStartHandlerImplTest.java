package com.zylitics.wzgp.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.ServiceAccount;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.zylitics.wzgp.http.RequestGridCreate;
import com.zylitics.wzgp.http.ResponseGridCreate;
import com.zylitics.wzgp.http.ResponseStatus;
import com.zylitics.wzgp.http.RequestGridCreate.GridProperties;
import com.zylitics.wzgp.http.RequestGridCreate.ResourceSearchParams;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.compute.ComputeService;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.search.ResourceSearch;
import com.zylitics.wzgp.resource.util.ResourceUtil;
import com.zylitics.wzgp.test.dummy.DummyAPICoreProperties;
import com.zylitics.wzgp.test.util.ResourceTestUtil;
import com.zylitics.wzgp.util.Randoms;
import com.zylitics.wzgp.web.exceptions.AcquireStoppedMaxReattemptException;
import com.zylitics.wzgp.web.exceptions.GridBeingDeletedFromOutsideException;
import com.zylitics.wzgp.web.exceptions.GridNotStartedException;
import com.zylitics.wzgp.web.exceptions.GridStartHandlerFailureException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness=Strictness.LENIENT)
class GridStartHandlerImplTest {
  
  private static final Logger LOG = LoggerFactory.getLogger(GridStartHandlerImplTest.class);
  
  private final static String RANDOM_CHAR_SET = "0123456789abcdefghizklmnopqrstuvwxyz";
  
  private static final String ZONE = "us-central0-g";
  
  private static final APICoreProperties API_CORE_PROPS = new DummyAPICoreProperties();
  
  @Test
  @DisplayName("verify requests give up when can't acquire an instance post max reattempts")
  void requestsGiveUpMaxReattemptTest() throws Exception {
    // Note: we've kept all mocks in Callable so that overriding won't happen and every request
    // has its own mocks. This is not same as what happens in application as all those are singleton
    // objects, but its ok and doesn't make any difference. For example search mock is created every
    // time, but it returns the same instance created outside so it doesn't matter if its the same
    // search object or different. Making different mocks allow us to provide request specific
    // request object, buildProp etc that wouldn't be possible in shared mocks since overriding
    // of mock will occur.
    int totalRequests = 3;
    ExecutorService threadExecutor = Executors.newFixedThreadPool(totalRequests);
    // A latch is used because of the uncertain execution times and order of threads. We want the
    // instance acquiring thread to not remove its entry from the map until all other threads reach
    // max-reattempt. This latch will make the acquiring thread wait for other threads to reach
    // an exception and once they countdown, it will finish process to start the instance, this is
    // the safest way to simulate a max reattempt reach exception in concurrent requests.
    CountDownLatch latch = new CountDownLatch(totalRequests - 1);
    // only one stopped instance is available
    // searches will always return same instance for all requests. Except the one that acquire it
    // first, others will see it being reserved and thus re-attempt searching until max re-attempts
    // are reached.
    // we kept it outside of the callable otherwise a new instance with a different instanceId will
    // generate everytime, we need a single instance and a single instanceId.
    String gridName = "grid-1";
    Instance gridInstance = getInstance(gridName);
    
    Callable<GridStartResponse> gridStarter =  () -> {
      try {
        ResourceExecutor executor = mock(ResourceExecutor.class);
        ComputeService computeSrv = mock(ComputeService.class);
        ResourceSearch search = mock(ResourceSearch.class);
        FingerprintBasedUpdater fingerprintBasedUpdater = mock(FingerprintBasedUpdater.class);
        
        String buildId = getNewBuildId();
        RequestGridCreate requestCreate = getCreateRequest(buildId);
        BuildProperty buildProp = requestCreate.getBuildProperties();
        
        when(search.searchStoppedInstance(requestCreate.getResourceSearchParams(), ZONE, buildProp))
            .thenReturn(Optional.of(gridInstance));
        
        when(fingerprintBasedUpdater.updateLabels(gridInstance
            , ImmutableMap.of(ResourceUtil.LABEL_LOCKED_BY_BUILD, buildId), buildProp))
            .then(inv -> {
              // request that will be able to acquire the instance first, will wait on lock-build
              // method until the other requests reach max-reattempts where they signal it to
              // release and go ahead.
              latch.await(5, TimeUnit.SECONDS);
              return null;
            });
        
        stubGridStart(gridName, executor, computeSrv, buildProp, true);
        
        Instance startedInstanceFresh = getInstance(gridName).setStatus("RUNNING")
            .setLabels(ImmutableMap.of(ResourceUtil.LABEL_LOCKED_BY_BUILD, buildProp.getBuildId()));
        when(computeSrv.getInstance(gridName, ZONE, buildProp)).thenReturn(startedInstanceFresh);
        
        GridStartHandler handler = getHandler(executor, computeSrv, search, fingerprintBasedUpdater
            , requestCreate);
        try {
          ResponseEntity<ResponseGridCreate> response = handler.handle();
          // just verify that this interaction happened.
          verify(fingerprintBasedUpdater).updateLabels(gridInstance
              , ImmutableMap.of(ResourceUtil.LABEL_LOCKED_BY_BUILD, buildProp.getBuildId())
              , buildProp);
          validateResponse(startedInstanceFresh, response);
          return GridStartResponse.SUCCESS;
        } catch (GridStartHandlerFailureException ex) {
          if (ex.getCause().getClass().equals(AcquireStoppedMaxReattemptException.class)) {
            latch.countDown();
            return GridStartResponse.FAILURE;
          }
        }
      } catch (Exception ex) {
        LOG.error("", ex);
      }
      return GridStartResponse.UNKNOWN;
    };
    
    List<Future<GridStartResponse>> futures = new ArrayList<>(); 
    for (int i = 0; i < totalRequests; i++) {
      futures.add(threadExecutor.submit(gridStarter));
    }
    
    threadExecutor.shutdown();
    threadExecutor.awaitTermination(5, TimeUnit.SECONDS);
    
    assertEquals(1, futures.stream().filter(future -> {
      try {
        return future.get().equals(GridStartResponse.SUCCESS);
      } catch (Exception ex) {
        // ignore
      }
      return false;
    }).count());
    
    assertEquals(2, futures.stream().filter(future -> {
      try {
        return future.get().equals(GridStartResponse.FAILURE);
      } catch (Exception ex) {
        // ignore
      }
      return false;
    }).count());
  }
  
  @Test
  @DisplayName("verify requests fail when search start to return nothing post acquire of available"
      + " instances")
  void requestsFailNoSearchResultTest() throws Exception {
    int totalRequests = 4;
    String grid1Name = "grid-1";
    String grid2Name = "grid-2";
    Instance grid1 = getInstance(grid1Name);
    Instance grid2 = getInstance(grid2Name);
    ExecutorService threadExecutor = Executors.newFixedThreadPool(totalRequests);
    Random random = new Random();
    CountDownLatch latch = new CountDownLatch(totalRequests - 2);
    
    Callable<GridStartResponse> gridStarter =  () -> {
      try {
        ResourceExecutor executor = mock(ResourceExecutor.class);
        ComputeService computeSrv = mock(ComputeService.class);
        ResourceSearch search = mock(ResourceSearch.class);
        FingerprintBasedUpdater fingerprintBasedUpdater = mock(FingerprintBasedUpdater.class);
        
        String buildId = getNewBuildId();
        RequestGridCreate requestCreate = getCreateRequest(buildId);
        BuildProperty buildProp = requestCreate.getBuildProperties();
        Map<String, String> lockGridLabel =
            ImmutableMap.of(ResourceUtil.LABEL_LOCKED_BY_BUILD, buildId);
        
        // search should check the locked-by-build label on the instance and refuse to provide
        // result if it's not none, but the grid acquiring threads may reach the lock-instance
        // code post the max-re-attempt of other non-acquiring threads. That's why we can't rely on
        // that in this concurrent environment.
        // To efficiently simulate search failure, we'll directly access the FOUND_INSTANCES map.
        when(search.searchStoppedInstance(requestCreate.getResourceSearchParams(), ZONE, buildProp))
            .then(inv -> {
              Map<BigInteger, String> foundInstances = getFoundInstances();
              if (foundInstances.size() == 2) {
                return Optional.empty();
              }
              List<Instance> availableGrids = ImmutableList.of(grid1, grid2).stream()
                  .filter(grid -> !foundInstances.containsKey(grid.getId()))
                  .collect(Collectors.toList());
              try {
                // sometimes the size of the map changes so frequently that it may become 2 even
                // just after checking it wasn't 2, following catch statement covers this
                // possibility and returns empty optional if it happens.
                return Optional.of(availableGrids.get(random.nextInt(availableGrids.size())));
              } catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
                return Optional.empty();
              }
            });
        
        when(fingerprintBasedUpdater.updateLabels(grid1, lockGridLabel, buildProp))
            .then(inv -> {
              latch.await(5, TimeUnit.SECONDS);
              return null;
            });
        when(fingerprintBasedUpdater.updateLabels(grid2, lockGridLabel, buildProp))
            .then(inv -> {
              latch.await(5, TimeUnit.SECONDS);
              return null;
            });
        
        stubGridStart(grid1Name, executor, computeSrv, buildProp, true);
        stubGridStart(grid2Name, executor, computeSrv, buildProp, true);
        
        Instance startedGrid1Fresh = getInstance(grid1Name).setStatus("RUNNING")
            .setLabels(lockGridLabel);
        when(computeSrv.getInstance(grid1Name, ZONE, buildProp)).thenReturn(startedGrid1Fresh);
        Instance startedGrid2Fresh = getInstance(grid2Name).setStatus("RUNNING")
            .setLabels(lockGridLabel);
        when(computeSrv.getInstance(grid2Name, ZONE, buildProp)).thenReturn(startedGrid2Fresh);
        
        GridStartHandler handler = getHandler(executor, computeSrv, search, fingerprintBasedUpdater
            , requestCreate);
        try {
          ResponseEntity<ResponseGridCreate> response = handler.handle();
          assertNotNull(response);
          assertNotNull(response.getBody());
          if (response.getBody().getGridName().equals(startedGrid1Fresh.getName())) {
            validateResponse(startedGrid1Fresh, response);
          } else {
            validateResponse(startedGrid2Fresh, response);
          }
          return GridStartResponse.SUCCESS;
        } catch (GridStartHandlerFailureException ex) {
          if (ex.getCause() == null) {
            latch.countDown();
            return GridStartResponse.FAILURE;
          }
        }
      } catch (Exception ex) {
        LOG.error("", ex);
      }
      return GridStartResponse.UNKNOWN;
    };
    
    List<Future<GridStartResponse>> futures = new ArrayList<>();
    for (int i = 0; i < totalRequests; i++) {
      futures.add(threadExecutor.submit(gridStarter));
    }
    
    threadExecutor.shutdown();
    threadExecutor.awaitTermination(5, TimeUnit.SECONDS);
    
    assertEquals(2, futures.stream().filter(future -> {
      try {
        return future.get().equals(GridStartResponse.SUCCESS);
      } catch (Exception ex) {
        // ignore
      }
      return false;
    }).count());
    
    assertEquals(2, futures.stream().filter(future -> {
      try {
        return future.get().equals(GridStartResponse.FAILURE);
      } catch (Exception ex) {
        // ignore
      }
      return false;
    }).count());
    
    // assert that after successful start, request's remove their found instances from map.
    Map<BigInteger, String> foundInstances = getFoundInstances();
    assertEquals(0, foundInstances.size());
  }
  
  @Test
  @DisplayName("verify GridStarter failure raises exception test")
  void gridStarterFailureTest() throws Exception {
    ResourceExecutor executor = mock(ResourceExecutor.class);
    ComputeService computeSrv = mock(ComputeService.class);
    ResourceSearch search = mock(ResourceSearch.class);
    FingerprintBasedUpdater fingerprintBasedUpdater = mock(FingerprintBasedUpdater.class);
    String gridName = "grid-1";
    Instance gridInstance = getInstance(gridName);
    
    String buildId = getNewBuildId();
    RequestGridCreate requestCreate = getCreateRequest(buildId);
    BuildProperty buildProp = requestCreate.getBuildProperties();
    
    when(search.searchStoppedInstance(requestCreate.getResourceSearchParams(), ZONE, buildProp))
        .thenReturn(Optional.of(gridInstance));
    
    stubGridStart(gridName, executor, computeSrv, buildProp, false);

    GridStartHandler handler = getHandler(executor, computeSrv, search, fingerprintBasedUpdater
        , requestCreate);
    
    Throwable t = null;
    try {
      handler.handle();
    } catch(GridStartHandlerFailureException ex) {
      t = ex;
    }
    assertNotNull(t);
    assertEquals(GridNotStartedException.class, t.getCause().getClass());
    
    // just verify that this interaction happened.
    verify(fingerprintBasedUpdater).updateLabels(gridInstance
        , ImmutableMap.of(ResourceUtil.LABEL_LOCKED_BY_BUILD, buildProp.getBuildId()), buildProp);
  }
  
  @Test
  @DisplayName("verify post startup finding is-deleting label raises exception test")
  void gridIsDeletingLabelFoundTest() throws Exception {
    ResourceExecutor executor = mock(ResourceExecutor.class);
    ComputeService computeSrv = mock(ComputeService.class);
    ResourceSearch search = mock(ResourceSearch.class);
    FingerprintBasedUpdater fingerprintBasedUpdater = mock(FingerprintBasedUpdater.class);
    String gridName = "grid-1";
    Instance gridInstance = getInstance(gridName);
    
    String buildId = getNewBuildId();
    RequestGridCreate requestCreate = getCreateRequest(buildId);
    BuildProperty buildProp = requestCreate.getBuildProperties();
    
    when(search.searchStoppedInstance(requestCreate.getResourceSearchParams(), ZONE, buildProp))
        .thenReturn(Optional.of(gridInstance));
    
    stubGridStart(gridName, executor, computeSrv, buildProp, true);
    
    Instance startedInstanceFresh = getInstance(gridName).setStatus("RUNNING")
        .setLabels(ImmutableMap.of(
              ResourceUtil.LABEL_LOCKED_BY_BUILD, buildProp.getBuildId(),
              ResourceUtil.LABEL_IS_DELETING, "true"
            ));
    when(computeSrv.getInstance(gridName, ZONE, buildProp)).thenReturn(startedInstanceFresh);

    GridStartHandler handler = getHandler(executor, computeSrv, search, fingerprintBasedUpdater
        , requestCreate);
    
    Throwable t = null;
    try {
      handler.handle();
    } catch(GridStartHandlerFailureException ex) {
      t = ex;
    }
    assertNotNull(t);
    assertEquals(GridBeingDeletedFromOutsideException.class, t.getCause().getClass());
    
    verify(fingerprintBasedUpdater).updateLabels(gridInstance
        , ImmutableMap.of(ResourceUtil.LABEL_LOCKED_BY_BUILD, buildProp.getBuildId()), buildProp);
  }
  
  private Instance getInstance(String name) {
    return new Instance()
        .setId(BigInteger.valueOf(new Random().nextInt(100) + 1))
        .setName(name)
        .setNetworkInterfaces(
            ImmutableList.of(new NetworkInterface().setNetworkIP("192.168.1.1")))
        .setZone(ResourceTestUtil.getZoneLink(ZONE))
        .setMachineType(String.format("zones/%s/machineTypes/%s", ZONE, "machine-1"))
        .setServiceAccounts(ImmutableList.of(new ServiceAccount().setEmail("123@gmail.com")))
        .setStatus("TERMINATED")
        .setLabels(ImmutableMap.of(ResourceUtil.LABEL_LOCKED_BY_BUILD, "none"));
  }
  
  private void stubGridStart(String gridName, ResourceExecutor executor, ComputeService computeSrv
      , BuildProperty buildProp, boolean shouldSucceed) throws Exception {
    // Not stubbing, update handler methods at grid-start because the component has already been
    // tested separately and we don't require them to run successfully for this test.
    Operation startOperation = new Operation().setStatus("RUNNING");
    when(computeSrv.startInstance(gridName, ZONE, buildProp))
        .thenReturn(startOperation);
    when(executor.blockUntilComplete(startOperation, buildProp))
        .thenReturn(getOperation(gridName, ZONE, shouldSucceed));
  }
  
  @SuppressWarnings("SameParameterValue")
  private Operation getOperation(String resourceName, String zone, boolean isSuccess) {
    return new Operation()
        .setHttpErrorStatusCode(
            isSuccess ? null : HttpStatus.INTERNAL_SERVER_ERROR.value())
        .setStatus("DONE")
        .setName("operation-" + UUID.randomUUID())
        .setTargetLink(ResourceTestUtil.getOperationTargetLink(resourceName, zone))
        .setZone(ResourceTestUtil.getZoneLink(zone));
  }
  
  private void validateResponse(Instance startedInstance
      ,ResponseEntity<ResponseGridCreate> response) {
    assertEquals(HttpStatus.OK, response.getStatusCode());
    
    ResponseGridCreate responseBody = response.getBody();
    assertNotNull(responseBody);
    assertEquals(startedInstance.getId(), responseBody.getGridId());
    assertEquals("192.168.1.1", responseBody.getGridInternalIP());
    assertEquals(startedInstance.getName(), responseBody.getGridName());
    assertEquals(ZONE, responseBody.getZone());
    assertEquals(ResponseStatus.SUCCESS.name(), responseBody.getStatus());
    assertEquals(HttpStatus.OK.value(), responseBody.getHttpStatusCode());
  }
  
  private RequestGridCreate getCreateRequest(String buildId) {
    RequestGridCreate request = new RequestGridCreate();
    
    request.getBuildProperties().setBuildId(buildId);
    
    GridProperties gridProps = request.getGridProperties();
    gridProps.setMetadata(ImmutableMap.of("screen", "1x1"));
    
    ResourceSearchParams searchParams = request.getResourceSearchParams();
    searchParams.setOS("win7");
    searchParams.setBrowser("chrome");
    searchParams.setShots(true);
    
    return request;
  }
  
  private GridStartHandler getHandler(ResourceExecutor executor, ComputeService computeSrv
      , ResourceSearch search, FingerprintBasedUpdater fingerprintBasedUpdater
      , RequestGridCreate requestCreate) {
    return new GridStartHandlerImpl.Factory().create(API_CORE_PROPS, executor, computeSrv, search
        , fingerprintBasedUpdater, ZONE, requestCreate);
  }
  
  private String getNewBuildId() {
    return "build-" + new Randoms(RANDOM_CHAR_SET).generateRandom(10);
  }
  
  @SuppressWarnings("unchecked")
  private Map<BigInteger, String> getFoundInstances() {
    return (Map<BigInteger, String>) ReflectionTestUtils.getField(
        GridStartHandlerImpl.class, "FOUND_INSTANCES");
  }
  
  enum GridStartResponse {
    SUCCESS,
    FAILURE,
    UNKNOWN
  }
}
