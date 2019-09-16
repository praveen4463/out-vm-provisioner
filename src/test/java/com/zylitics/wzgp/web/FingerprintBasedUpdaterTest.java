package com.zylitics.wzgp.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.Operation;
import com.google.common.collect.ImmutableMap;
import com.zylitics.wzgp.resource.compute.ComputeService;
import com.zylitics.wzgp.resource.util.ResourceUtil;
import com.zylitics.wzgp.test.util.ResourceTestUtil;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness=Strictness.STRICT_STUBS)
public class FingerprintBasedUpdaterTest {

  private static final String ZONE = "us-central0-g";
  
  private static final String INSTANCE_NAME = "grid-1";
  
  @Test
  @DisplayName("verify update-labels call send required and valid parameters to compute")
  void updateLabelsTest() throws Exception {
    ComputeService computeSrv = mock(ComputeService.class);
    FingerprintBasedUpdater updater = new FingerprintBasedUpdater(computeSrv);
    
    Map<String, String> labelsToUpdate =
        ImmutableMap.of(
              ResourceUtil.LABEL_IS_PRODUCTION_INSTANCE, "false"
            );
    
    Map<String, String> suppliedInstanceLabels = ImmutableMap.of(
        "os", "win7",
        "browser1", "chrome",
        "browser2", "firefox",
        ResourceUtil.LABEL_IS_PRODUCTION_INSTANCE, "true"
      );
    
    Instance providedInstance = getNewInstanceWithLabel("3ljbd3qkPjI=", suppliedInstanceLabels);
    
    Map<String, String> labelsAfterInstanceFetch = new HashMap<>(suppliedInstanceLabels);
    labelsAfterInstanceFetch.put("browser2", "ie");
    String fingerprintAfterInstanceFetch = "lkmm44md23=";
    
    Instance fetchedInstance = getNewInstanceWithLabel(fingerprintAfterInstanceFetch
        , labelsAfterInstanceFetch);
    
    when(computeSrv.getInstance(INSTANCE_NAME, ZONE, null)).thenReturn(fetchedInstance);
    
    Map<String, String> mergedLabels = ImmutableMap.of(
        "os", "win7",
        "browser1", "chrome",
        "browser2", "ie",
        ResourceUtil.LABEL_IS_PRODUCTION_INSTANCE, "false"
      );
    
    when(computeSrv.setLabels(INSTANCE_NAME, mergedLabels, ZONE, fingerprintAfterInstanceFetch
        , null)).thenReturn(new Operation().setName("op-success-update-label"));
    
    assertEquals("op-success-update-label"
        , updater.updateLabels(providedInstance, labelsToUpdate, null).getName());
  }
  
  @Test
  @DisplayName("verify update-metadata when instance metadata is empty (stopped instance)")
  void updateMetadataWhenEmptyTest() throws Exception {
    ComputeService computeSrv = mock(ComputeService.class);
    FingerprintBasedUpdater updater = new FingerprintBasedUpdater(computeSrv);
    
    Map<String, String> metadataToUpdate =
        ImmutableMap.of(
              ResourceUtil.METADATA_CURRENT_TEST_SESSIONID, "build-xtt334hf",
              "user-desired-browser", "chrome",
              "user-screen", "1090x989"
            );
    
    // no metadata initially in the instance, same as what happens with a stopped instance.
    Instance providedInstance = getNewInstanceWithMetadata("3ljbd3qkPjI=", ImmutableMap.of());
    
    // return the provided instance assuming this is the first update being made.
    when(computeSrv.getInstance(INSTANCE_NAME, ZONE, null)).thenReturn(providedInstance);
    
    when(computeSrv.setMetadata(INSTANCE_NAME, metadataToUpdate, ZONE, "3ljbd3qkPjI="
        , null)).thenReturn(new Operation().setName("op-success-update-metadata"));
    
    assertEquals("op-success-update-metadata"
        , updater.updateMetadata(providedInstance, metadataToUpdate, null).getName());
  }
  
  @Test
  @DisplayName("verify update-metadata call send required and valid parameters to compute")
  void updateMetadataTest() throws Exception {
    ComputeService computeSrv = mock(ComputeService.class);
    FingerprintBasedUpdater updater = new FingerprintBasedUpdater(computeSrv);
    
    Map<String, String> metadataToUpdate =
        ImmutableMap.of(
              ResourceUtil.METADATA_CURRENT_TEST_SESSIONID, "session-xtt334hf"
            );
    
    Map<String, String> suppliedInstanceMetadata = ImmutableMap.of(
          ResourceUtil.METADATA_CURRENT_TEST_SESSIONID, "build-xtt334hf",
          "user-desired-browser", "chrome",
          "user-screen", "1090x989"
      );
    
    Instance providedInstance = getNewInstanceWithMetadata("3ljbd3qkPjI="
        , suppliedInstanceMetadata);
    
    Map<String, String> metadataAfterInstanceFetch = new HashMap<>(suppliedInstanceMetadata);
    metadataAfterInstanceFetch.put("time-zone-with-dst", "Alaskan Standard Time_dstoff");
    String fingerprintAfterInstanceFetch = "lkmm44md23=";
    
    Instance fetchedInstance = getNewInstanceWithMetadata(fingerprintAfterInstanceFetch
        , metadataAfterInstanceFetch);
    
    when(computeSrv.getInstance(INSTANCE_NAME, ZONE, null)).thenReturn(fetchedInstance);
    
    Map<String, String> mergedMetadata = ImmutableMap.of(
        ResourceUtil.METADATA_CURRENT_TEST_SESSIONID, "session-xtt334hf",
        "user-desired-browser", "chrome",
        "user-screen", "1090x989",
        "time-zone-with-dst", "Alaskan Standard Time_dstoff"
      );
    
    when(computeSrv.setMetadata(INSTANCE_NAME, mergedMetadata, ZONE, fingerprintAfterInstanceFetch
        , null)).thenReturn(new Operation().setName("op-success-update-metadata"));
    
    assertEquals("op-success-update-metadata"
        , updater.updateMetadata(providedInstance, metadataToUpdate, null).getName());
  }
  
  @Test
  @DisplayName("verify delete-metadata call send required and valid parameters to compute")
  void deleteMetadataTest() throws Exception {
    ComputeService computeSrv = mock(ComputeService.class);
    FingerprintBasedUpdater updater = new FingerprintBasedUpdater(computeSrv);
    
    Map<String, String> suppliedInstanceMetadata = ImmutableMap.of(
        ResourceUtil.METADATA_CURRENT_TEST_SESSIONID, "session-xtt334hf",
        "user-desired-browser", "chrome",
        "user-screen", "1090x989",
        "time-zone-with-dst", "Alaskan Standard Time_dstoff"
      );
    
    Instance providedInstance = getNewInstanceWithMetadata("3ljbd3qkPjI="
        , suppliedInstanceMetadata);
    
    // return the provided instance assuming there was no other update.
    when(computeSrv.getInstance(INSTANCE_NAME, ZONE, null)).thenReturn(providedInstance);
    
    when(computeSrv.setMetadata(INSTANCE_NAME, ImmutableMap.of(), ZONE, "3ljbd3qkPjI="
        , null)).thenReturn(new Operation().setName("op-success-update-metadata"));
    
    assertEquals("op-success-update-metadata"
        , updater.deleteAllMetadata(providedInstance, null).getName());
  }
  
  private Instance getNewInstanceWithMetadata(String metadataFingerprint
      , Map<String, String> metadata) {
    Metadata gcpMetata = ResourceUtil.getGCPMetadata(metadata);
    gcpMetata.setFingerprint(metadataFingerprint);
    
    return new Instance()
        .setName(INSTANCE_NAME)
        .setZone(ResourceTestUtil.getZoneLink(ZONE))
        .setMetadata(gcpMetata);
  }
  
  private Instance getNewInstanceWithLabel(String labelFingerprint, Map<String, String> labels) {
    return new Instance()
        .setName(INSTANCE_NAME)
        .setZone(ResourceTestUtil.getZoneLink(ZONE))
        .setLabels(labels)
        .setLabelFingerprint(labelFingerprint);
  }
}
