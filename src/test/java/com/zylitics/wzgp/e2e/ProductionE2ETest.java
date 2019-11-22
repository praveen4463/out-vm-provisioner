package com.zylitics.wzgp.e2e;

import java.time.Duration;

import com.zylitics.wzgp.test.util.AuthUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.google.api.client.util.Preconditions;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.compute.ComputeService;
import com.zylitics.wzgp.web.FingerprintBasedUpdater;

/**
 * Run tests using a production server running this api, used to test the api after deployment to
 * production systems.
 * Test Dependencies:
 * 1. PRODUCTION_API_ROOT_URL system property
 * 2. PRODUCTION_API_VERSION system property
 * 3. Should run from a GCP VM so that internal api could be accessed and container can start
 *    without having needed a service account file explicitly.
 * When you want to debug any production element like logging, change the profile via sys prop to choose production
 * profile in test configuration.
 * 
 * This test also starts the container so that api properties and apis could be used for any
 * information/processing that is not available from the production api and required to thoroughly
 * test the production api. For example instance 'get' or getting {@link APICoreProperties} will
 * be required in this test that could be fetched easily using the container. Thus, we're dependent
 * on the container only for 'extra' processing requirements and not for the base test which is
 * done on the remote server.
 * @author Praveen Tiwari
 *
 */
@Tag("production-e2e")
@SpringBootTest(webEnvironment=WebEnvironment.NONE)
@ActiveProfiles("e2e")
// !!! When you need to test something that is activate on production profile, change the profile
// through system property and supply it in test, for example testing stackdriver logging.
@SuppressWarnings("unused")
class ProductionE2ETest extends AbstractGridE2ETest {
  
  private static final String PRODUCTION_API_ROOT_URL_PROP = "zl.wzgp.productionRootURL";
  
  private static final String PRODUCTION_API_VERSION_PROP = "zl.wzgp.productionVersion";
  
  private static final String PRODUCTION_API_ROOT_URL =
     Preconditions.checkNotNull(System.getProperty(PRODUCTION_API_ROOT_URL_PROP)
         , PRODUCTION_API_ROOT_URL_PROP + " system property is missing");
  
  private static final String PRODUCTION_API_VERSION =
      Preconditions.checkNotNull(System.getProperty(PRODUCTION_API_VERSION_PROP)
          , PRODUCTION_API_VERSION_PROP + " system property is missing");
  
  private static final String WZGP_AUTH_USER = "prod-test";
  
  private static final String WZGP_AUTH_SECRET_CLOUD_FILE = "zl-wzgp-prod-test-auth.encrypt";
  
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
    String secret;
    
    try (SecretsManager secretsManager = SecretsManager.Factory.getDefault().create()) {
      secret = secretsManager.getSecretAsPlainText(WZGP_AUTH_SECRET_CLOUD_FILE);
    }
    
    client = WebTestClient.bindToServer().baseUrl(PRODUCTION_API_ROOT_URL)
        .defaultHeader(AuthUtil.AUTHORIZATION,
            AuthUtil.getBasicAuthHeaderValue(WZGP_AUTH_USER, secret))
        .responseTimeout(Duration.ofMinutes(10)).build();
    apiVersion = PRODUCTION_API_VERSION;
    super.env = env;
    super.apiCoreProps = apiCoreProps;
    super.computeSrv = computeSrv;
    super.fingerprintBasedUpdater = fingerprintBasedUpdater;
    super.stoppedInstanceCustomIdentifier = "zl-wzgp-production-e2e";
    
    super.beforeEach();
  }
}
