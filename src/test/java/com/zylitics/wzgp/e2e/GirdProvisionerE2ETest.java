package com.zylitics.wzgp.e2e;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.zylitics.wzgp.http.RequestGridCreate;
import com.zylitics.wzgp.http.ResponseGridCreate;
import com.zylitics.wzgp.http.RequestGridCreate.GridProperties;
import com.zylitics.wzgp.http.RequestGridCreate.ResourceSearchParams;
import com.zylitics.wzgp.http.ResponseGridDelete;
import com.zylitics.wzgp.http.ResponseStatus;
import com.zylitics.wzgp.resource.util.ResourceUtil;
import com.zylitics.wzgp.util.Randoms;

/**
 * Test Dependencies:
 * 1. (Only if not running on a GCP VM) GOOGLE_APPLICATION_CREDENTIALS environment variable should 
 *    be in corresponding environment, pointing to the service account key file.
 * @author Praveen Tiwari
 *
 */
@SpringBootTest(webEnvironment=WebEnvironment.RANDOM_PORT, properties="debug=true")
@ActiveProfiles("e2e")
public class GirdProvisionerE2ETest {

  private static final String ZONE = "us-central1-f";
  
  private static final String APP_VER_KEY = "app-short-version";
  
  @Autowired
  private WebTestClient client;
  
  @Autowired
  private Environment env;
  
  @BeforeEach
  void setup() {
    client = client.mutate().responseTimeout(Duration.ofMinutes(6)).build();
  }
  
  @Test
  @DisplayName("verify a new grid can be created, stopped and started successfully on GCP")
  void gridProvisionStartStopTest() throws Exception {
    // =====================================================================================
    String sourceImageFamily = "win2008-base"; // !!! make sure the image in this family has all
    // required 'labels' like os, browser1.... etc that matches the ones used while 'starting'.
    
    String buildIdCreate = "build-" + new Randoms().generateRandom(10);
    
    // first create a noRush request so that a new grid is created
    // !! Note: we're using RequestGridCreate here which is fundamentally wrong because it is used
    // to interpret the incoming request in json and not to build the request. Only the container
    // use the 'set' apis in it to convert the request to a POJO.
    // Ideally we should declare a similar model to create requests separately but as this test is
    // for the api, we will use it. For outside client, they will have their own model.
    RequestGridCreate requestCreate = new RequestGridCreate();
    
    requestCreate.getBuildProperties().setBuildId(buildIdCreate);
    
    GridProperties gridPropsCreate = requestCreate.getGridProperties();
    
    gridPropsCreate.setMachineType("n1-standard-1");  // select a small machine
    
    gridPropsCreate.setCustomLabels(
        ImmutableMap.of(ResourceUtil.LABEL_IS_PRODUCTION_INSTANCE, "false"));
    
    // we send buildId as ResourceUtil.METADATA_CURRENT_TEST_SESSIONID so that shutdown script sees
    // a guaranteed sessionId.
    gridPropsCreate.setMetadata(ImmutableMap.of(
          "user-screen", "1680x1050",
          "user-desired-browser", "chrome;76",
          ResourceUtil.METADATA_CURRENT_TEST_SESSIONID, buildIdCreate,
          "no-start-shut-script", "1"
        ));
    
    ResponseGridCreate responseCreate = client.post()
        .uri("/{version}/zones/{zone}/grids?noRush=true&sourceImageFamily={image-family}"
            , env.getProperty(APP_VER_KEY), ZONE, sourceImageFamily)
        .accept(MediaType.APPLICATION_JSON_UTF8)
        .contentType(MediaType.APPLICATION_JSON_UTF8)
        .syncBody(requestCreate)
        .exchange()
        .expectStatus().isCreated()
        .expectHeader().contentType(MediaType.APPLICATION_JSON_UTF8)
        .expectBody(ResponseGridCreate.class)
        .returnResult().getResponseBody();
    
    assertNotNull(responseCreate);
    
    String generatedGridName = responseCreate.getGridName();
    String generatedGridZone = responseCreate.getZone();
    
    assertEquals(ResponseStatus.SUCCESS.name(), responseCreate.getStatus());
    assertTrue(!Strings.isNullOrEmpty(generatedGridName));
    assertTrue(!Strings.isNullOrEmpty(responseCreate.getGridInternalIP()));
    
    System.err.println("grid " + generatedGridName + " is now created, going to wait for"
        + " a minute before stopping it, while you check the details. buildId = " + buildIdCreate);
    Thread.sleep(60 * 1000);
    
    // =====================================================================================
    
    String sessionIdStop = "session-" + new Randoms().generateRandom(10);
    // now we'll stop this grid rather than delete, thus don't send a noRush
    ResponseGridDelete responseStop = client.delete()
        .uri("/{version}/zones/{zone}/grids/{gridName}?sessionId={sessionId}"
            , env.getProperty(APP_VER_KEY), generatedGridZone, generatedGridName, sessionIdStop)
        .accept(MediaType.APPLICATION_JSON_UTF8)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType(MediaType.APPLICATION_JSON_UTF8)
        .expectBody(ResponseGridDelete.class)
        .returnResult().getResponseBody();
    
    assertNotNull(responseStop);
    assertEquals(ResponseStatus.SUCCESS.name(), responseStop.getStatus());
    
    System.err.println("grid " + generatedGridName + " is now stopped, going to wait for 30 seconds"
        + " before starting it.");
    Thread.sleep(30 * 1000);
    
    // =====================================================================================
    
    // !! The shutdown script resets labels like 'locked-by-build' and removes all metadata as the
    // instance is stopped/deleted, but since we don't run it on non-production instances, we'll
    // leave them as-is.
    
    // Now, we need to start the stopped grid. The api searches for an instance to do so. Since we
    // we want instance created in this test to get searched, we'll modify the search like:
    // provide locked-by-build = buildIdCreate parameter. The create handler applied this to the
    // instance when it created it and since there is no shutdown script this label is not modified
    // to default value, hence we can use it to find our specific instance. If we didn't have this
    // label on instance, we could add our own custom label identifying the instance uniquely and
    // use that in search.
    
    // Also provide ResourceUtil.LABEL_IS_PRODUCTION_INSTANCE = false parameter to search so that
    // default value of true taken by api gets overridden because we want our non-production
    // instance to get searched.
    
    String buildIdStart = "build-" + new Randoms().generateRandom(10);
    
    RequestGridCreate requestStart = new RequestGridCreate();
    
    requestStart.getBuildProperties().setBuildId(buildIdStart);
    
    GridProperties gridPropsStart = requestStart.getGridProperties();
    
    // set both machine and service account to see they updated while starting up.
    gridPropsStart.setMachineType("n1-standard-2");
    
    gridPropsStart.setServiceAccount("809428419389-compute@developer.gserviceaccount.com");
    
    // no custom label need to be set for this test.
    
    // provide different metadata than 'create' to see its updated.
    gridPropsStart.setMetadata(ImmutableMap.of(
        "user-screen", "1080x709",
        "user-desired-browser", "firefox;76",
        ResourceUtil.METADATA_CURRENT_TEST_SESSIONID, buildIdStart
      ));
    
    ResourceSearchParams searchParamsStart = requestStart.getResourceSearchParams();
    searchParamsStart.setOS("win7");
    searchParamsStart.setBrowser("chrome");
    searchParamsStart.setShots(true);
    
    searchParamsStart.setCustomInstanceSearchParams(ImmutableMap.of(
          "labels." + ResourceUtil.LABEL_LOCKED_BY_BUILD, buildIdCreate,
          "labels." + ResourceUtil.LABEL_IS_PRODUCTION_INSTANCE, "false"
        ));
    
    ResponseGridCreate responseStart = client.post()
        .uri("/{version}/zones/{zone}/grids", env.getProperty(APP_VER_KEY), ZONE)
        .accept(MediaType.APPLICATION_JSON_UTF8)
        .contentType(MediaType.APPLICATION_JSON_UTF8)
        .syncBody(requestStart)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType(MediaType.APPLICATION_JSON_UTF8)
        .expectBody(ResponseGridCreate.class)
        .returnResult().getResponseBody();
    
    assertNotNull(responseStart);
    
    String startedGridName = responseStart.getGridName();
    String startedGridZone = responseStart.getZone();
    
    assertEquals(ResponseStatus.SUCCESS.name(), responseStart.getStatus());
    assertTrue(!Strings.isNullOrEmpty(responseStart.getGridInternalIP()));
    assertEquals(generatedGridName, startedGridName);
    assertEquals(generatedGridZone, startedGridZone);
    
    System.err.println("grid " + startedGridName + " is now started, going to wait for 90 seconds"
        + " before deleting it while you check it.");
    Thread.sleep(90 * 1000);
    
    // =====================================================================================
    
    String sessionIdDelete = "session-" + new Randoms().generateRandom(10);
    // now we'll delete this grid, thus send a noRush
    ResponseGridDelete responseDelete = client.delete()
        .uri("/{version}/zones/{zone}/grids/{gridName}?noRush=true&sessionId={sessionId}"
            , env.getProperty(APP_VER_KEY), startedGridZone, startedGridName, sessionIdDelete)
        .accept(MediaType.APPLICATION_JSON_UTF8)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType(MediaType.APPLICATION_JSON_UTF8)
        .expectBody(ResponseGridDelete.class)
        .returnResult().getResponseBody();
    
    assertNotNull(responseDelete);
    assertEquals(ResponseStatus.SUCCESS.name(), responseDelete.getStatus());
    
    System.err.println("grid " + startedGridName + " is now deleted. Test done!");
  }
}
