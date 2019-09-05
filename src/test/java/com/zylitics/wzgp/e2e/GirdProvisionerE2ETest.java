package com.zylitics.wzgp.e2e;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.zylitics.wzgp.http.RequestGridCreate;
import com.zylitics.wzgp.http.ResponseGridCreate;
import com.zylitics.wzgp.http.RequestGridCreate.GridProperties;
import com.zylitics.wzgp.http.ResponseGridDelete;
import com.zylitics.wzgp.http.ResponseStatus;
import com.zylitics.wzgp.resource.util.ResourceUtil;
import com.zylitics.wzgp.util.Randoms;

import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;

/**
 * Test Dependencies:
 * 1. (Only if not running on a GCP VM) GOOGLE_APPLICATION_CREDENTIALS environment variable should 
 *    be in corresponding environment, pointing to the service account key file.
 * @author Praveen Tiwari
 *
 */
@SpringBootTest(webEnvironment=WebEnvironment.RANDOM_PORT)
@ActiveProfiles("production")
public class GirdProvisionerE2ETest {

  private static final String ZONE = "us-central1-f";
  
  private static final String APP_VER_KEY = "app-short-version";
  
  private static final String SOURCE_IMAGE_FAMILY = "win2008-base";
  
  @Autowired
  private WebTestClient client;
  
  @Autowired
  private Environment env;
  
  @Test
  @DisplayName("verify a new grid can be created and deleted successfully on GCP")
  void gridProvisioningLiveTest() throws Exception {
    client = client.mutate().responseTimeout(Duration.ofMinutes(6)).build();
    
    String buildId = new Randoms().generateRandom(10);
    String sessionId = new Randoms().generateRandom(10);
    
    RequestGridCreate request = new RequestGridCreate();
    request.getBuildProperties().setBuildId(buildId);
    GridProperties gridProps = request.getGridProperties();
    gridProps.setMachineType("n1-standard-1");
    gridProps.getCustomLabels().put(ResourceUtil.LABEL_IS_PRODUCTION_INSTANCE, "false");
    Map<String, String> metadata = gridProps.getMetadata();
    metadata.put("user-screen", "1680x1050");
    metadata.put("user-desired-browser", "chrome;76");
    
    ResponseGridCreate responseCreate = client.post()
        .uri("/{version}/zones/{zone}/grids?noRush=true&sourceImageFamily={image-family}"
            , env.getProperty(APP_VER_KEY), ZONE, SOURCE_IMAGE_FAMILY)
        .accept(MediaType.APPLICATION_JSON_UTF8)
        .contentType(MediaType.APPLICATION_JSON_UTF8)
        .syncBody(request)
        .exchange()
        .expectStatus().isCreated()
        .expectHeader().contentType(MediaType.APPLICATION_JSON_UTF8)
        .expectBody(ResponseGridCreate.class)
        .returnResult().getResponseBody();

    assertNotNull(responseCreate);
    assertEquals(ResponseStatus.SUCCESS.name(), responseCreate.getStatus());
    assertTrue(!Strings.isNullOrEmpty(responseCreate.getGridName()));
    assertTrue(!Strings.isNullOrEmpty(responseCreate.getGridInternalIP()));
    
    String generateGridName = responseCreate.getGridName();
    String generateGridZone = responseCreate.getZone();
    
    ResponseGridDelete responseDelete = client.delete()
        .uri("/{version}/zones/{zone}/grids/{gridName}?noRush=true&sessionId={sessionId}"
            , env.getProperty(APP_VER_KEY), generateGridZone, generateGridName, sessionId)
        .accept(MediaType.APPLICATION_JSON_UTF8)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType(MediaType.APPLICATION_JSON_UTF8)
        .expectBody(ResponseGridDelete.class)
        .returnResult().getResponseBody();
        
    assertNotNull(responseDelete);
    assertEquals(ResponseStatus.SUCCESS.name(), responseDelete.getStatus());
  }
}
