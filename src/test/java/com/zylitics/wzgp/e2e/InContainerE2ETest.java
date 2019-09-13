package com.zylitics.wzgp.e2e;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.compute.ComputeService;
import com.zylitics.wzgp.web.FingerprintBasedUpdater;

/**
 * Test Dependencies:
 * 1. (Only if not running on a GCP VM) GOOGLE_APPLICATION_CREDENTIALS environment variable should 
 *    be in corresponding environment, pointing to the service account key file.
 * @author Praveen Tiwari
 *
 */
@Tag("in-container-e2e")
@SpringBootTest(webEnvironment=WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
public class InContainerE2ETest extends AbstractGridE2ETest {
  
  private static final String APP_VER_KEY = "app-short-version";
  
  @Autowired
  private WebTestClient client;
  
  @Autowired
  private Environment env;
  
  @Autowired
  private APICoreProperties apiCoreProps;
  
  @Autowired
  private ComputeService computeSrv;
  
  @Autowired
  private FingerprintBasedUpdater fingerprintBasedUpdater;
  
  @BeforeEach
  void setup() throws Exception {
    super.client = client.mutate().responseTimeout(Duration.ofMinutes(6)).build();
    apiVersion = env.getProperty(APP_VER_KEY);
    super.env = env;
    super.apiCoreProps = apiCoreProps;
    super.computeSrv = computeSrv;
    super.fingerprintBasedUpdater = fingerprintBasedUpdater;
    super.stoppedInstanceCustomIdentifier = "zl-wzgp-incontainer-e2e";
    
    super.beforeEach();
  }
}
