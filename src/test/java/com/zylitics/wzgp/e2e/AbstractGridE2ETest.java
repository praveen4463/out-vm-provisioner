package com.zylitics.wzgp.e2e;

import static org.junit.jupiter.api.Assertions.*;
import static com.zylitics.wzgp.resource.util.ResourceUtil.nameFromUrl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.util.UriBuilder;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.util.Preconditions;
import com.google.api.services.compute.model.Disk;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Metadata.Items;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.zylitics.wzgp.http.RequestGridCreate;
import com.zylitics.wzgp.http.ResponseGridCreate;
import com.zylitics.wzgp.http.ResponseGridDelete;
import com.zylitics.wzgp.http.ResponseStatus;
import com.zylitics.wzgp.http.RequestGridCreate.GridProperties;
import com.zylitics.wzgp.http.RequestGridCreate.ResourceSearchParams;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.compute.ComputeService;
import com.zylitics.wzgp.resource.util.ResourceUtil;
import com.zylitics.wzgp.util.Randoms;
import com.zylitics.wzgp.web.FingerprintBasedUpdater;

/**
 * Note: Don't run tests in parallel, they should run sequentially.
 * @author Praveen Tiwari
 *
 */
abstract class AbstractGridE2ETest {
  
  private static final Logger LOG = LoggerFactory.getLogger(AbstractGridE2ETest.class);
  
  private final static String RANDOM_CHAR_SET = "0123456789abcdefghizklmnopqrstuvwxyz";
  
  private static final String API_BASE_PATH = "/{version}/zones/{zone}/grids";
  
  private static final String ZONE_PROP = "zl.wzgp.e2e.zone";
  
  private static final String ZONE = Preconditions.checkNotNull(System.getProperty(ZONE_PROP)
      , ZONE_PROP + " system property is missing");
  
  private static final String IP4_PATTERN = "\\d{1,3}.\\d{1,3}.\\d{1,3}.\\d{1,3}.";
  
  private static final String SOURCE_IMAGE_FAMILY_PROP = "zl.wzgp.e2e.sourceImageFamily";
  
  private static final String SOURCE_IMAGE_FAMILY =
      Preconditions.checkNotNull(System.getProperty(SOURCE_IMAGE_FAMILY_PROP)
          , SOURCE_IMAGE_FAMILY_PROP + " system property is missing");
  
  //fixed search parameters and their predefined values for stopped instance. we'll verify
  // the image we're using contains these as label key-value. 
  private static final Map<String, String> FIXED_SEARCH_PARAMS = ImmutableMap.of(
      "os", "win8_1",
      "browser1", "chrome",
      "shots", "true"
    );

  WebTestClient client;
  
  String apiVersion;
  
  Environment env;
  
  APICoreProperties apiCoreProps;
  
  ComputeService computeSrv;
  
  FingerprintBasedUpdater fingerprintBasedUpdater;
  
  String stoppedInstanceCustomIdentifier;
  
  // a test class is instantiated per test method, that's why we require to build some of the
  // members every time.
  private boolean debug;
  
  private static boolean imageLabelsVerified = false;
  /**
   * should be invoked after @BeforeEach of test class.
   */
  void beforeEach() throws Exception {
    debug = Boolean.parseBoolean(env.getProperty("debug"));
    
    // verify that the image returned by this image family has all labels with defined search values
    // required to search an instance successfully.
    // Note that, we'll verify this only once because the source-image-family is going to
    // remain the same.
    // Note that, we can't verify this statically because the autowired components becomes available
    // only when the class is instantiated. 
    if (!imageLabelsVerified) {
      Image image = computeSrv.getImageFromFamily(SOURCE_IMAGE_FAMILY, null);
      assertNotNull(image);
      assertNotNull(image.getLabels());
      LOG.debug("labels are {}", image.getLabels().toString());
      assertTrue(image.getLabels().entrySet().containsAll(FIXED_SEARCH_PARAMS.entrySet()),
          "This image family " + SOURCE_IMAGE_FAMILY + " doesn't contain labels"
              + " that match required search parameters");
      imageLabelsVerified = true;
    }
  }
  
  @Test
  void markVMAsAvailable() throws Exception {
    String name = "";
    String zone = "";
    ResponseGridDelete response = client.delete()
        .uri(uriBuilder -> uriBuilder.path(API_BASE_PATH)
            .pathSegment("{gridName}")
            .queryParam("requireRunningVM", "true")
            .build(apiVersion, zone, name))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType(MediaType.APPLICATION_JSON)
        .expectBody(ResponseGridDelete.class)
        .returnResult().getResponseBody();
  
    assertNotNull(response);
    assertEquals(ResponseStatus.SUCCESS.name(), response.getStatus());
  }
  
  @Test
  void getRunningInstance() {
    String buildId = getNewBuildId();
    LOG.info("Going to get a running vm, buildId {}", buildId);
    RequestGridCreate request = new RequestGridCreate();
    request.getBuildProperties().setBuildId(buildId);
    GridProperties gridProps = request.getGridProperties();
    gridProps.setMetadata(ImmutableMap.of(
        "no-start-shut-script", "1",
        "time-zone-with-dst", "Alaskan Standard Time_dstoff",
        "build-id", buildId
    ));
    gridProps.setCustomLabels(ImmutableMap.of(
        "build-id", buildId
    ));
    ResourceSearchParams searchParams = request.getResourceSearchParams();
    searchParams.setOS("win10");
    searchParams.setBrowser("firefox");
    searchParams.setShots(true);
    long start = System.currentTimeMillis();
    ResponseGridCreate response = getSuccessfulCreateResponse(request, false, false, true);
    LOG.debug("took {}secs waiting for to get a running instance",
        TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start));
    LOG.debug("instance is {}, zone is {}", response.getGridName(), response.getZone());
    assertEquals("", response.getGridName());
    assertEquals(ZONE, response.getZone());
  }
  
  @Test
  @DisplayName("verify properties of new grid are same as defined")
  void newGridPropertiesVerificationTest() throws Exception {
    String buildIdCreate = getNewBuildId();
    LOG.info("Going to start a new grid, buildId {}", buildIdCreate);
    
    String machineType = "n1-standard-1";
    
    Map<String, String> customLabels =
        ImmutableMap.of(ResourceUtil.LABEL_IS_PRODUCTION_INSTANCE, "false");
    
    Map<String, String> metadata = getEntireMetadata("1680x1050", "chrome;76", buildIdCreate);
    
    RequestGridCreate requestCreate = getCreateRequest(buildIdCreate, machineType, null
        , customLabels, metadata, false);
    
    ResponseGridCreate responseCreate = getSuccessfulCreateResponse(requestCreate, true, true,
        false);
    
    String generatedGridName = responseCreate.getGridName();
    String generatedGridZone = responseCreate.getZone();
    
    LOG.info("Successfully created a new grid at {}:{}, going ahead to verify it"
        , generatedGridName, generatedGridZone);
    
    // ====================================================================================
    
    // verify that the generated grid has all properties that we expect.
    // first fetch this grid and image using container.
    try {
      Instance grid = computeSrv.getInstance(generatedGridName, generatedGridZone, null);
      Image image = computeSrv.getImageFromFamily(SOURCE_IMAGE_FAMILY, null);
      
      APICoreProperties.GridDefault gridDefault = apiCoreProps.getGridDefault();
      
      // tags, List euaqlsTo consider the ordering of the list, that's why the sorting.
      assertEquals(ImmutableList.sortedCopyOf(gridDefault.getTags()), grid.getTags().getItems());
      
      //machine
      assertEquals(machineType, nameFromUrl(grid.getMachineType()));
      
      // grid status
      assertEquals("RUNNING", grid.getStatus());
      
      // network
      assertEquals(ResourceUtil.getSubnetURLFromZone(apiCoreProps.getSharedVpcProjectId(), ZONE),
          grid.getNetworkInterfaces().get(0).getSubnetwork());
      
      // internal ip
      String internalIP = grid.getNetworkInterfaces().get(0).getNetworkIP();
      assertTrue(internalIP.matches(IP4_PATTERN));
      
      // external ip shouldn't be there as we didn't ask for it
      assertNull(grid.getNetworkInterfaces().get(0).getAccessConfigs());
      
      // disk source image. We need to get the disk to match source-image (not family) and disk type
      // because initialize-parameters are not written back in the response, they're tied to disk.
      Disk disk = computeSrv.getDisk(nameFromUrl(grid.getDisks().get(0).getSource())
          , generatedGridZone, null);
      assertEquals(image.getName(), nameFromUrl(disk.getSourceImage()));
      
      // disk type
      assertEquals("pd-ssd", nameFromUrl(disk.getType()));
      
      // metadata, use code to derive merged metadata rather than hardcoding since we're not doing
      // unit test
      Map<String, String> gridMetadata = grid.getMetadata().getItems().stream()
          .collect(Collectors.toMap(Items::getKey, Items::getValue));
      assertEquals(metadata, gridMetadata);
      
      // service-account
      assertEquals(gridDefault.getServiceAccount(), grid.getServiceAccounts().get(0).getEmail());
      
      // preemptible
      assertFalse(grid.getScheduling().getPreemptible());
      
      // labels, use code to derive merged labels rather than hard-coding since we're not doing
      // unit test.
      Map<String, String> mergedLabels = new HashMap<>(gridDefault.getLabels());
      // get the labels set in the image
      Map<String, String> gridLabelsFromImage = image.getLabels().entrySet().stream()
          .filter(entry -> !gridDefault.getImageSpecificLabelsKey().contains(entry.getKey()))
          .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
      mergedLabels.putAll(gridLabelsFromImage);
      mergedLabels.putAll(customLabels);
      mergedLabels.put(ResourceUtil.LABEL_LOCKED_BY_BUILD, buildIdCreate);
      mergedLabels.put(ResourceUtil.LABEL_SOURCE_FAMILY, SOURCE_IMAGE_FAMILY);
      assertEquals(mergedLabels, grid.getLabels());
    } finally {
      if (!debug) {
        deleteGridWithApi(generatedGridName, generatedGridZone, true);
      } else {
        System.err.println("debug is true, skipping deletion of grid at " + generatedGridName + ":"
            + generatedGridZone + ". please do it manually once done debugging.");
      }
    }
    LOG.info("Successfully verified new grid at {}:{}, test is done", generatedGridName
        , generatedGridZone);
  }
  
  @Test
  @DisplayName("verify properties of a started grid are same as defined")
  // Although we verify started grid in parallel access test too, this test is made specifically
  // to validate a started grid, validating each and everything and has value. We should keep it.
  void startedGridPropertiesVerificationTest() throws Exception {
    ResponseGridCreate responseStopped = getStoppedInstance("n1-standard-1", "1680x1080"
        , "chrome;76");
    String generatedGridName = responseStopped.getGridName();
    String generatedGridZone = responseStopped.getZone();
    
    LOG.info("A stopped grid is created at {}:{}, going ahead starting it", generatedGridName
        , generatedGridZone);
    try {
      // Now, we need to start the stopped grid after searching it.
      String buildIdStart = getNewBuildId();
      
      String machineTypeStart = "n1-standard-2";
      
      String serviceAccountStart = "809428419389-compute@developer.gserviceaccount.com";
      
      // send a custom label to see that it added up.
      Map<String, String> gridStartCustomLabels = ImmutableMap.of("key-xyz", "value-xyz");
      
      // provide different metadata than while getting stopped instance to see its updated.
      Map<String, String> gridStartMetadata = getEntireMetadata("1080x709", "firefox;76"
          , buildIdStart);
      
      RequestGridCreate requestStart = getCreateRequest(buildIdStart, machineTypeStart
          , serviceAccountStart, gridStartCustomLabels, gridStartMetadata, true);
      
      ResponseGridCreate responseStart = getSuccessfulCreateResponse(requestStart, false, false,
          false);
      
      assertEquals(generatedGridName, responseStart.getGridName());
      assertEquals(generatedGridZone, responseStart.getZone());
      
      LOG.info("The stopped grid at {}:{} was found and started, going ahead to verify it"
          , generatedGridName, generatedGridZone);
      
      // ====================================================================================
      // verify that everything we sent updated correctly.
      Instance grid = computeSrv.getInstance(generatedGridName, generatedGridZone, null);
      
      assertEquals(machineTypeStart, nameFromUrl(grid.getMachineType()));
      
      assertEquals(serviceAccountStart, grid.getServiceAccounts().get(0).getEmail());
      
      grid.getLabels().entrySet().containsAll(gridStartCustomLabels.entrySet());
      
      Map<String, String> gridMetadata = grid.getMetadata().getItems().stream()
          .collect(Collectors.toMap(Items::getKey, Items::getValue));
      assertEquals(gridStartMetadata, gridMetadata);
      
      String lockedByBuild = grid.getLabels().get(ResourceUtil.LABEL_LOCKED_BY_BUILD);
      assertEquals(buildIdStart, lockedByBuild);
      
    } finally {
      if (!debug) {
        deleteGridWithApi(generatedGridName, generatedGridZone, true);
      } else {
        System.err.println("debug is true, skipping deletion of grid at " + generatedGridName + ":"
            + generatedGridZone + ". please do it manually once done debugging.");
      }
    }
    LOG.info("Successfully verified started grid at {}:{}, test is done", generatedGridName
        , generatedGridZone);
  }
  
  @Tag("slow")
  @Test
  @DisplayName("verify no-rush and on-demand parallel requests can get a grid")
  void onDemandAndNewGridParallelRequestTest() throws Exception {
    gridParallelAccessTest(2, 2, 4);
  }
  
  // TODO: This test is disabled until we've full capacity in all zones. Currently we don't have
  // capacity to create 5 instances in one zone. When we create 5 of them, they scatter across zones
  // that leads to failed searches because search look only in the supplied zone of the request and
  // not across zones.
  @Tag("slow")
  @Disabled
  @Test
  @DisplayName("verify all on-demand parallel requests can get a stopped grid")
  void onDemandParallelRequestTest() throws Exception {
    gridParallelAccessTest(5, 0, 5);
  }
  
  /**
   * Given the number of stopped, no-rush, on-demand instances, we verify that the given number of
   * no-rush + on-demand instances can successfully start, effectively using all the stopped
   * instances available.
   * @param totalStoppedInstances Total number of instances you want to put in stopped state to
   *        reserve for on-demand test
   * @param totalNoRushInstances Total number of instances you want to start for no-rush tests like
   *        recurring tests.
   * @param totalOnDemandInstances Total number of instances you want to start for on-demand tests
   *        like tests started from browser extension. These should use stopped instances to start
   *        them if there are available, else new instances will be created. This number should be
   *        larger or equal to totalStoppedInstances.
   * @throws Exception If there are problems executing
   */
  private void gridParallelAccessTest(int totalStoppedInstances, int totalNoRushInstances
      , int totalOnDemandInstances)
      throws Exception {
    assertTrue(totalOnDemandInstances >= totalStoppedInstances,
        "totalOnDemandInstances should be larger or equal to totalStoppedInstances so that"
            + " we could verify all stopped instances were used by the test");
    
    LOG.info("Creating {} stopped grid(s)", totalStoppedInstances);
    
    List<ResponseGridCreate> stoppedInstances = new ArrayList<>();
    for (int i = 0; i < totalStoppedInstances; i++) {
      ResponseGridCreate response = getStoppedInstance("n1-standard-1", "1680x1080"
          , "chrome;76");
      assertEquals(ZONE, response.getZone(), "Test can't proceed because all stopped grids"
          + " are not in the desired zone: " + ZONE + ". Searches look into only the supplied zone,"
              + " if we don't have enough stopped grids, on-demand requests will create grids from"
              + " scratch. Aborting test..");
      stoppedInstances.add(response);
    }
    
    LOG.info("{} stopped grid(s) are now created", totalStoppedInstances);
    
    // ready to start parallel threads
    // ====================================================================================
    ExecutorService executor = Executors.newFixedThreadPool(6);
    
    List<Future<String>> futures = new ArrayList<>(10);
    
    LOG.info("submitting request for {} no-rush-grid(s) and {} on-demand-grid(s)"
        , totalNoRushInstances, totalOnDemandInstances);
    
    for (int i = 0; i < totalNoRushInstances; i++) {
      futures.add(executor.submit(getAGrid(true, "n1-standard-2", "1080x766", "ie;10")));
    }
    
    for (int i = 0; i < totalOnDemandInstances; i++) {
      // send these unique every time so that we could verify the started instance (if any) has
      // updates only from its own process.
      String userBrowser = "ie;" + i + 1;
      String userScreen = "1080x" + i + 1;
      futures.add(executor.submit(getAGrid(false, "n1-standard-2", userBrowser, userScreen)));
    }
    
    executor.shutdown();
    executor.awaitTermination(10, TimeUnit.MINUTES);
    
    // ImmutableSet don't allow null values.
    ImmutableSet.Builder<String> instanceWithZoneBuilder = ImmutableSet.builder();
    for (Future<String> future : futures) {
      instanceWithZoneBuilder.add(future.get());
    }
    
    // a set proves that all instance:zone strings are unique.
    assertEquals(totalNoRushInstances + totalOnDemandInstances
        , instanceWithZoneBuilder.build().size());
    
    // verify that stopped grids were deleted by one of thread after acquiring it.
    for (ResponseGridCreate stoppedInstance : stoppedInstances) {
      int statusCode = 0;
      try {
        computeSrv.getInstance(stoppedInstance.getGridName(), stoppedInstance.getZone(), null);
      } catch (GoogleJsonResponseException ex) {
        statusCode = ex.getStatusCode();
      }
      assertEquals(404, statusCode);
    }
    
    LOG.info("All stopped grid(s) are deleted by on-demand-grid requests");
  }
  
  @SuppressWarnings("SameParameterValue")
  private Callable<String> getAGrid(boolean noRush, String machineType, String userScreen
      , String userBrowser) {
    return () -> {
      String buildId = getNewBuildId();
      
      Map<String, String> metadata = getEntireMetadata(userScreen, userBrowser, buildId);
      
      RequestGridCreate request = getCreateRequest(buildId, machineType, null
          , ImmutableMap.of(ResourceUtil.LABEL_IS_PRODUCTION_INSTANCE, "false"), metadata, true);
      
      ResponseGridCreate response = getSuccessfulCreateResponse(request, noRush, false, false);
      
      String generatedGridName = response.getGridName();
      String generatedGridZone = response.getZone();
      
      // verify what we sent is on the grid, useful mainly for 'started stopped instances'.
      Instance grid = computeSrv.getInstance(generatedGridName, generatedGridZone, null);
      
      String lockedByBuild = grid.getLabels().get(ResourceUtil.LABEL_LOCKED_BY_BUILD);
      
      assertEquals(buildId, lockedByBuild);
      
      assertEquals(machineType, nameFromUrl(grid.getMachineType()));
      
      Map<String, String> gridMetadata = grid.getMetadata().getItems().stream()
          .collect(Collectors.toMap(Items::getKey, Items::getValue));
      assertEquals(metadata, gridMetadata);
      
      // now delete this instance, send noRush=true as we want deletion.
      deleteGridWithApi(generatedGridName, generatedGridZone, true);
      
      return generatedGridName + ":" + generatedGridZone;
    };
  }
  
  @SuppressWarnings("SameParameterValue")
  private ResponseGridCreate getStoppedInstance(String machineType
      , String userScreen, String userBrowser) throws Exception {
    String buildId = getNewBuildId();
    
    // add a custom identifier so that searches can use it to guarantee they search stopped
    // instances, created by this test class only.
    Map<String, String> customLabels = ImmutableMap.of(
        ResourceUtil.LABEL_IS_PRODUCTION_INSTANCE, "false",
        ResourceUtil.LABEL_STOPPED_INSTANCE_CUSTOM_IDENTIFIER, stoppedInstanceCustomIdentifier
      );
    Map<String, String> metadata = getEntireMetadata(userScreen, userBrowser, buildId);
    
    RequestGridCreate request =
        getCreateRequest(buildId, machineType, null, customLabels, metadata, false);
    
    ResponseGridCreate response = getSuccessfulCreateResponse(request, true, true, false);
    
    String generatedGridName = response.getGridName();
    String generatedGridZone = response.getZone();
    
    // now we'll stop this grid.
    String sessionId = deleteGridWithApi(generatedGridName, generatedGridZone, false);
    
    // verify sessionId is set to the metadata after delete command is processed.
    Instance grid = computeSrv.getInstance(generatedGridName, generatedGridZone, null);
    Items metadataItems = grid.getMetadata().getItems().stream()
        .filter(items -> items.getKey().equals(ResourceUtil.METADATA_CURRENT_TEST_SESSIONID))
        .findFirst().orElse(null);
    assertNotNull(metadataItems);
    assertEquals(sessionId, metadataItems.getValue());
    
    // when an instance is stopped, shutdown script would reset some labels and delete
    // all metadata. Since we don't run shutdown script from test instance, we will do it manually.
    fingerprintBasedUpdater.updateLabels(grid
        , ImmutableMap.of(ResourceUtil.LABEL_LOCKED_BY_BUILD, "none"), null);
    fingerprintBasedUpdater.deleteAllMetadata(grid, null);
    
    return response;
  }
  
  /**
   * @param gridName name of grid to delete
   * @param gridZone name of zone where grid resides
   * @param noRush whether its a no-rush request
   * @return sessionId, sent to api to set as {@link ResourceUtil#METADATA_CURRENT_TEST_SESSIONID}
   */
  private String deleteGridWithApi(String gridName, String gridZone, boolean noRush) {
    String sessionId = "session-" + new Randoms(RANDOM_CHAR_SET).generateRandom(10);
    
    ResponseGridDelete response = client.delete()
        .uri(uriBuilder -> uriBuilder.path(API_BASE_PATH)
            .pathSegment("{gridName}")
            .queryParam("noRush", noRush)
            .queryParam("sessionId", sessionId)
            .build(apiVersion, gridZone, gridName))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType(MediaType.APPLICATION_JSON)
        .expectBody(ResponseGridDelete.class)
        .returnResult().getResponseBody();
    
    assertNotNull(response);
    assertEquals(ResponseStatus.SUCCESS.name(), response.getStatus());
    return sessionId;
  }
  
  private String getNewBuildId() {
    return "build-" + new Randoms(RANDOM_CHAR_SET).generateRandom(10);
  }
  
  // Contains all the metadata entries from application default and custom, if we increase any
  // metadata, it should be added up here.
  private Map<String, String> getEntireMetadata(String userScreen, String userBrowser
      , String buildId) {
    // we send buildId as ResourceUtil.METADATA_CURRENT_TEST_SESSIONID so that shutdown script sees
    // a guaranteed sessionId.
    return ImmutableMap.of(
        "user-screen", userScreen,
        "user-desired-browser", userBrowser,
        ResourceUtil.METADATA_CURRENT_TEST_SESSIONID, buildId,
        "no-start-shut-script", "1",
        "time-zone-with-dst", "Alaskan Standard Time_dstoff"
      );
  }
  
  @SuppressWarnings("SameParameterValue")
  private RequestGridCreate getCreateRequest(String buildId, String machineType
      , String serviceAccount, Map<String, String> customLabels, Map<String, String> metadata
      , boolean addSearchParams) {
    // !! Note: we're using RequestGridCreate here which is fundamentally wrong because it is used
    // to interpret the incoming request in json and not to build the request. Only the container
    // use the 'set' apis in it to convert the request to a POJO.
    // Ideally we should declare a similar model to create requests separately but as this test is
    // for the api, we will use it. For outside client, they will have their own model.
    RequestGridCreate request = new RequestGridCreate();
    
    request.getBuildProperties().setBuildId(buildId);
    
    GridProperties gridProps = request.getGridProperties();
    
    if (!Strings.isNullOrEmpty(machineType)) {
      gridProps.setMachineType(machineType);  // select a small machine as it's a test.
    }
    
    if (!Strings.isNullOrEmpty(serviceAccount)) {
      gridProps.setServiceAccount(serviceAccount);
    }
    
    if (customLabels != null) {
      gridProps.setCustomLabels(customLabels);
    }
    
    if (metadata != null) {
      gridProps.setMetadata(metadata);
    }
    
    if (addSearchParams) {
      ResourceSearchParams searchParams = request.getResourceSearchParams();
      
      searchParams.setOS(FIXED_SEARCH_PARAMS.get("os"));
      searchParams.setBrowser(FIXED_SEARCH_PARAMS.get("browser1"));
      searchParams.setShots(Boolean.parseBoolean(FIXED_SEARCH_PARAMS.get("shots")));
      // override default search param of production-instance=true and give identifier to limit the
      // search this to test class only.
      searchParams.setCustomInstanceSearchParams(ImmutableMap.of(
          "labels." + ResourceUtil.LABEL_IS_PRODUCTION_INSTANCE
          , "false",
          "labels." + ResourceUtil.LABEL_STOPPED_INSTANCE_CUSTOM_IDENTIFIER
          , stoppedInstanceCustomIdentifier
        ));
    }
    
    return request;
  }
  
  private ResponseGridCreate getSuccessfulCreateResponse(RequestGridCreate request, boolean noRush
      , boolean addSourceImageFamily, boolean requireRunningVM) {
    ResponseGridCreate response = client.post()
        .uri(uriBuilder -> {
          UriBuilder builder = uriBuilder.path(API_BASE_PATH)
              .queryParam("noRush", noRush);
          if (addSourceImageFamily) {
            builder.queryParam("sourceImageFamily", SOURCE_IMAGE_FAMILY);
          }
          builder.queryParam("requireRunningVM", requireRunningVM);
          return builder.build(apiVersion, ZONE);
        })
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus().is2xxSuccessful()
        .expectHeader().contentType(MediaType.APPLICATION_JSON)
        .expectBody(ResponseGridCreate.class)
        .returnResult().getResponseBody();
    
    assertNotNull(response);
    assertEquals(ResponseStatus.SUCCESS.name(), response.getStatus());
    assertFalse(Strings.isNullOrEmpty(response.getGridName()));
    assertFalse(Strings.isNullOrEmpty(response.getGridInternalIP()));
    
    return response;
  }
}
