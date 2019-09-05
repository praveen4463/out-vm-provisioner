package com.zylitics.wzgp.resource.grid;

import static com.zylitics.wzgp.resource.util.ResourceUtil.nameFromUrl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.Compute.Instances;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.APICoreProperties.GridDefault;
import com.zylitics.wzgp.resource.CompletedOperation;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.util.ResourceUtil;
import com.zylitics.wzgp.test.dummy.DummyAPICoreProperties;
import com.zylitics.wzgp.test.dummy.DummyRequestGridCreate;
import com.zylitics.wzgp.test.dummy.FakeCompute;

public class GridGeneratorTest {

  private static final String IMAGE_FAMILY = "win-2008-base";
  
  // note that it contains labels that are image specific.
  private static final Map<String, String> IMAGE_LABELS = ImmutableMap.of(
        "os", "win7",
        "release", "2.0",
        "browser1", "chrome",
        "test-vms", "10"
      );
  
  private static final String MACHINE_TYPE = "machine-1";
  
  private static final String SERVICE_ACCOUNT = "service-account-email-1";
  
  private static final String NETWORK = "netowork-1";
  
  private static final boolean PREEMPTIBLE = false;
  
  private static final Set<String> TAGS = ImmutableSet.of("tag-1", "tag-2");
  
  private static final Map<String, String> DEFAULT_LABELS =
      ImmutableMap.of("zl-grid", "true", "is-deleting", "false", "is-production-instance", "true");
  
  private static final Map<String, String> CUSTOM_LABELS =
      ImmutableMap.of("is-production-instance", "false");
  
  private static final Map<String, String> DEFAULT_METADATA =
      ImmutableMap.of("default-timezone", "utc");
  
  private static final Map<String, String> REQUEST_METADATA = ImmutableMap.of("screen", "1x1");
  
  private static final Set<String> IMAGE_SPECIFIC_LABELS_KEY = ImmutableSet.of("test-vms");
  
  // metadata that should be assigned to grid by generator
  private static final Map<String, String> MERGED_METADATA =
      ImmutableMap.of("default-timezone", "utc", "screen", "1x1");
  
  // labels that should be assigned to grid by generator
  private static final Map<String, String> MERGED_LABELS =
      new ImmutableMap.Builder<String, String>()
          .put("zl-grid", "true")
          .put("is-deleting", "false")
          .put("is-production-instance", "false")
          .put("os", "win7")
          .put("release", "2.0")
          .put("browser1", "chrome")
          .put(ResourceUtil.LABEL_SOURCE_FAMILY, IMAGE_FAMILY).build();
  
  private static final BuildProperty BUILD_PROP =
      new DummyRequestGridCreate().get().getBuildProperties();
  
  private static final Compute COMPUTE = new FakeCompute().get();
  
  /**
   * Since we've covered ResourceExecutor in other test, its ok to mock it in this test, with the
   * mock of it we will access each argument passed to executor method. We just need to make sure
   * that the arguments are valid.
   * GridGenerator provides Instances.Insert object to executor with a function that can generate
   * the same object when given a zone. We'll check both the objects and verify that it has all
   * details to be processed to a successful grid Operation.
   * We've taken two dummy classes separately for our test and not using the dummy objects already
   * available for test so that we can manually build list of labels, metadata and other thing to
   * verify the logic at generator precisely. 
   * @throws Exception
   */
  @SuppressWarnings("unchecked")
  @DisplayName("verify grid creates and validate its properties")
  @Test
  void gridGenerateTest() throws Exception {
    // Prepare spy that return a private instance of GridDefault so that we can provide any values.
    APICoreProperties apiCorePropsSpy = spy(new DummyAPICoreProperties());
    doReturn(new DummyGridDefaults()).when(apiCorePropsSpy).getGridDefault();
    
    String primaryZone = "us-central0-g";
    String primaryZoneRegion = "us-central0";
    String randomZone = "us-west0-k";
    String randomZoneRegion = "us-west0";
    
    Image image = new Image().setFamily(IMAGE_FAMILY).setLabels(IMAGE_LABELS);
    
    ResourceExecutor executor = mock(ResourceExecutor.class);
    
    when(executor.executeWithZonalReattempt(any(Instances.Insert.class), any(Function.class)
        , eq(BUILD_PROP))).thenAnswer(invocation -> {
          
          Instances.Insert insertInstanceProvided = (Instances.Insert) invocation.getArgument(0);
          
          verifyGridConfiguration((Instance) insertInstanceProvided.getJsonContent()
              , primaryZoneRegion);
          
          Function<String, Instances.Insert> insertInstanceFactory =
              (Function<String, Instances.Insert>) invocation.getArgument(1);
          
          Instances.Insert insertInstanceGenerated = insertInstanceFactory.apply(randomZone);
          
          verifyGridConfiguration((Instance) insertInstanceGenerated.getJsonContent()
              , randomZoneRegion);
          
          Operation operation = new Operation();
          operation.setStatus("DONE");
          
          return new CompletedOperation(operation);
        });
    
    GridGenerator generator = new GridGenerator(COMPUTE, apiCorePropsSpy, executor
        , BUILD_PROP, new DummyGridProperties(), image);
    
    // just verify that we get a completed operation, don't get into details whether it was
    // successful because that something executor deals with.
    assertEquals("DONE", generator.create(primaryZone).get().getStatus());
  }
  
  private void verifyGridConfiguration(Instance instance, String region) {
    assertTrue(instance.getName().matches(IMAGE_FAMILY + "-" + "[a-z0-9]{10}-vm"));
    
    assertEquals(MACHINE_TYPE, nameFromUrl(instance.getMachineType()));
    
    assertEquals(NETWORK
        , nameFromUrl(instance.getNetworkInterfaces().get(0).getNetwork()));
    
    // match URI instead cause we need to verify the region as well.
    String subnetURL = String.format("regions/%s/subnetworks/%s", region, "subnet-" + region);
    assertEquals(subnetURL, instance.getNetworkInterfaces().get(0).getSubnetwork());
    
    assertEquals(IMAGE_FAMILY
        , nameFromUrl(instance.getDisks().get(0).getInitializeParams().getSourceImage()));
    
    assertEquals(SERVICE_ACCOUNT, instance.getServiceAccounts().get(0).getEmail());
    
    assertEquals(PREEMPTIBLE, instance.getScheduling().getPreemptible());
    
    assertEquals(TAGS, new HashSet<>(instance.getTags().getItems()));
    
    assertEquals(ResourceUtil.getGCPMetadata(MERGED_METADATA), instance.getMetadata());
    
    assertEquals(MERGED_LABELS, instance.getLabels());
  }
  
  private static class DummyGridDefaults implements GridDefault {
    
    @Override
    public String getMachineType() {
      return MACHINE_TYPE;
    }
    
    @Override
    public String getNetwork() {
      return NETWORK;
    }
    
    @Override
    public String getServiceAccount() {
      return "xyz@xyz.com";
    }
    
    @Override
    public Set<String> getTags() {
      return TAGS;
    }
    
    @Override
    public Map<String, String> getLabels() {
      return DEFAULT_LABELS;
    }
    
    @Override
    public Map<String, String> getMetadata() {
      return DEFAULT_METADATA;
    }
    
    @Override
    public Set<String> getImageSpecificLabelsKey() {
      return IMAGE_SPECIFIC_LABELS_KEY;
    }
  }
  
  private static class DummyGridProperties implements GridProperty {
    
    @Override
    public String getMachineType() {
      return null;
    }
    
    @Override
    public String getServiceAccount() {
      return SERVICE_ACCOUNT;
    }
    
    @Override
    public boolean isPreemptible() {
      return PREEMPTIBLE;
    }
    
    @Override
    public Map<String, String> getCustomLabels() {
      return CUSTOM_LABELS;
    }
    
    @Override
    public Map<String, String> getMetadata() {
      return REQUEST_METADATA;
    }
  }
}