package com.zylitics.wzgp.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.junit.jupiter.api.Assertions.assertEquals;

import static com.zylitics.wzgp.web.FingerprintBasedUpdater.STALE_FINGERPRINT_IDENTIFIER;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

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
  @DisplayName("verify consecutive label updates fetch instance every time to get fresh details")
  void consecutiveLabelUpdateTest() throws Exception {
    ComputeService computeSrv = mock(ComputeService.class);
    FingerprintBasedUpdater updater = new FingerprintBasedUpdater(computeSrv);
    
    Map<String, String> originalLabels = ImmutableMap.of(
        "os", "win7",
        "browser1", "chrome",
        "browser2", "firefox"
      );
    String fingerprintOriginalLabels = "3ljbd3qkPjI=";
    
    Instance instance = getNewInstance(fingerprintOriginalLabels, null, originalLabels, null);
    
    //1. make first label update
    
    // take a same label so we know that the logic is overriding existing labels with requested.
    Map<String, String> labelsFirstUpdate =
        ImmutableMap.of(
              ResourceUtil.LABEL_IS_PRODUCTION_INSTANCE, "false",
              "browser2", "ie"
            );
    
    updater.updateLabels(instance, labelsFirstUpdate, null);
    
    // hard code merging rather than putAll to show that we replaced existing label.
    Map<String, String> mergedLabelsFirstUpdate = ImmutableMap.of(
        "os", "win7",
        "browser1", "chrome",
        "browser2", "ie",
        ResourceUtil.LABEL_IS_PRODUCTION_INSTANCE, "false"
      );
    
    verify(computeSrv).setLabels(INSTANCE_NAME, mergedLabelsFirstUpdate, ZONE
        , fingerprintOriginalLabels, null);
    
    // verify now the instance's label fingerprint is stale.
    assertEquals(STALE_FINGERPRINT_IDENTIFIER, instance.getLabelFingerprint());
    
    //2. now make second label update
    
    // After the first update, assume GCP updated fingerprint with following. 
    String fingerprintFirstUpdate = "lkmm44md23=";
    
    // when a fresh instance is fetched, GCP would send updated label fingerprint and merged
    // (original labels + first update labels) labels. 
    when(computeSrv.getInstance(INSTANCE_NAME, ZONE, null)).thenReturn(
        getNewInstance(fingerprintFirstUpdate, null, mergedLabelsFirstUpdate, null));
    
    Map<String, String> labelsSecondUpdate =
        ImmutableMap.of(
              ResourceUtil.LABEL_LOCKED_BY_BUILD, "xyz"
            );
    
    Map<String, String> mergedLabelsSecondUpdate = new HashMap<>(mergedLabelsFirstUpdate);
    mergedLabelsSecondUpdate.putAll(labelsSecondUpdate);
    
    // rather than verify that setLabels was invoked, we'll do a 'when' and use 'answer' so that
    // we can verify the state of 'instance' was updated. If we don't do it here, the 'updateLabels'
    // method would set fingerprint to stale once method is returned.
    when(computeSrv.setLabels(INSTANCE_NAME, mergedLabelsSecondUpdate, ZONE
        , fingerprintFirstUpdate, null)).then(inv -> {
          // verify that instance was updated with new fingerprint and labels
          assertEquals(fingerprintFirstUpdate, instance.getLabelFingerprint());
          assertEquals(mergedLabelsFirstUpdate, instance.getLabels());
          return new Operation().setName("operation-setlabel");
        });
    
    Operation operation = updater.updateLabels(instance, labelsSecondUpdate, null);
    assertEquals("operation-setlabel", operation.getName());
    
    // verify label fingerprint again set to stale.
    assertEquals(STALE_FINGERPRINT_IDENTIFIER, instance.getLabelFingerprint());
  }
  
  @Test
  @DisplayName("verify consecutive metadata updates fetch instance every time to get fresh details")
  void consecutiveMetadataUpdateTest() throws Exception {
    ComputeService computeSrv = mock(ComputeService.class);
    FingerprintBasedUpdater updater = new FingerprintBasedUpdater(computeSrv);
    
    Map<String, String> originalMetadata = ImmutableMap.of(
        ResourceUtil.METADATA_CURRENT_TEST_SESSIONID, "session-1",
        "user-desired-browser", "chrome",
        "user-screen", "1090x989"
      );
    String fingerprintOriginalMetadata = "3ljbd3qkPjI=";
    
    Instance instance = getNewInstance(null, fingerprintOriginalMetadata, null
        , originalMetadata);
    
    //1. make first metadata update
    
    // take a same metadata so we know that the logic is overriding existing metadata.
    Map<String, String> metadataFirstUpdate =
        ImmutableMap.of(
            ResourceUtil.METADATA_CURRENT_TEST_SESSIONID, "session-2",
              "no-start-shut-script", "1"
            );
    
    updater.updateMetadata(instance, metadataFirstUpdate, null);
    
    // hard code merging rather than putAll to show that we replaced existing metadata.
    Map<String, String> mergedMetadataFirstUpdate = ImmutableMap.of(
        ResourceUtil.METADATA_CURRENT_TEST_SESSIONID, "session-2",
        "user-desired-browser", "chrome",
        "user-screen", "1090x989",
        "no-start-shut-script", "1"
      );
    
    verify(computeSrv).setMetadata(INSTANCE_NAME, mergedMetadataFirstUpdate, ZONE
        , fingerprintOriginalMetadata, null);
    
    // verify now the instance's metadata fingerprint is stale.
    assertEquals(STALE_FINGERPRINT_IDENTIFIER, instance.getMetadata().getFingerprint());
    
    //2. now make second metadata update
    
    // After the first update, assume GCP updated fingerprint with following. 
    String fingerprintFirstUpdate = "lkmm44md23=";
    
    // when a fresh instance is fetched, GCP would send updated metadata fingerprint and merged
    // (original metadata + first update metadata) metadata. 
    when(computeSrv.getInstance(INSTANCE_NAME, ZONE, null)).thenReturn(
        getNewInstance(null, fingerprintFirstUpdate, null, mergedMetadataFirstUpdate));
    
    Map<String, String> metadataSecondUpdate =
        ImmutableMap.of(
              "no-start-shut-script", "0"
            );
    
    Map<String, String> mergedMetadataSecondUpdate = new HashMap<>(mergedMetadataFirstUpdate);
    mergedMetadataSecondUpdate.putAll(metadataSecondUpdate);
    
    // rather than verify that setMetadatta was invoked, we'll do a 'when' and use 'answer' so that
    // we can verify the state of 'instance' was updated. If we don't do it here, the
    // 'updateMetadata' method would set fingerprint to stale once method is returned.
    when(computeSrv.setMetadata(INSTANCE_NAME, mergedMetadataSecondUpdate, ZONE
        , fingerprintFirstUpdate, null)).then(inv -> {
          Metadata gcpMetadata = instance.getMetadata();
          // verify that instance was updated with new fingerprint and metadata
          assertEquals(fingerprintFirstUpdate, gcpMetadata.getFingerprint());
          Map<String, String> instanceMetadata = new HashMap<>();
          if (gcpMetadata.getItems() != null) {
            gcpMetadata.getItems().forEach(items -> {
              instanceMetadata.put(items.getKey(), items.getValue());
            });
          }
          assertEquals(mergedMetadataFirstUpdate, instanceMetadata);
          return new Operation().setName("operation-setmetadata");
        });
    
    Operation operation = updater.updateMetadata(instance, metadataSecondUpdate, null);
    assertEquals("operation-setmetadata", operation.getName());
    
    // verify metadata fingerprint again set to stale.
    assertEquals(STALE_FINGERPRINT_IDENTIFIER, instance.getMetadata().getFingerprint());
  }
  
  @Test
  @DisplayName("verify mixed label and metadata updates doesn't require fetch instance")
  void mixedLabelMetadataUpdateTest() throws Exception {
    ComputeService computeSrv = mock(ComputeService.class);
    FingerprintBasedUpdater updater = new FingerprintBasedUpdater(computeSrv);
    
    Map<String, String> originalLabels = ImmutableMap.of(
        "os", "win7",
        "browser1", "chrome",
        "browser2", "firefox"
      );
    String fingerprintOriginalLabels = "3ljbd3qkPjI=";
    
    Map<String, String> originalMetadata = ImmutableMap.of(
        ResourceUtil.METADATA_CURRENT_TEST_SESSIONID, "session-1",
        "user-desired-browser", "chrome",
        "user-screen", "1090x989"
      );
    String fingerprintOriginalMetadata = "3ljbd3qkPjI=";
    
    Instance instance = getNewInstance(fingerprintOriginalLabels, fingerprintOriginalMetadata
        , originalLabels, originalMetadata);
    
    // ==========================================================================================
    
    Map<String, String> labelsUpdate =
        ImmutableMap.of(
              ResourceUtil.LABEL_IS_PRODUCTION_INSTANCE, "false"
            );
    
    updater.updateLabels(instance, labelsUpdate, null);
    
    Map<String, String> mergedLabels = new HashMap<>(originalLabels);
    mergedLabels.putAll(labelsUpdate);
    
    verify(computeSrv).setLabels(INSTANCE_NAME, mergedLabels, ZONE
        , fingerprintOriginalLabels, null);
    
    // verify now the instance's label fingerprint is stale.
    assertEquals(STALE_FINGERPRINT_IDENTIFIER, instance.getLabelFingerprint());
    
    // ======================

    Map<String, String> metadataUpdate = ImmutableMap.of("no-start-shut-script", "1");
    
    updater.updateMetadata(instance, metadataUpdate, null);
    
    Map<String, String> mergedMetadata = new HashMap<>(originalMetadata);
    mergedMetadata.putAll(metadataUpdate);
    
    verify(computeSrv).setMetadata(INSTANCE_NAME, mergedMetadata, ZONE
        , fingerprintOriginalMetadata, null);
    
    // verify now the instance's metadata fingerprint is stale.
    assertEquals(STALE_FINGERPRINT_IDENTIFIER, instance.getMetadata().getFingerprint());
    
    // finally verify that getInstance call never been made.
    verify(computeSrv, times(0)).getInstance(INSTANCE_NAME, ZONE, null);
  }
  
  private Instance getNewInstance(@Nullable String labelFingerprint
      , @Nullable String metadataFingerprint
      , @Nullable Map<String, String> labels
      , @Nullable Map<String, String> metadata) {
    Metadata gcpMetata = null;
    
    if (metadata != null && metadataFingerprint != null) {
      gcpMetata = ResourceUtil.getGCPMetadata(metadata);
      gcpMetata.setFingerprint(metadataFingerprint);
    }
    
    return new Instance()
        .setName(INSTANCE_NAME)
        .setZone(ResourceTestUtil.getZoneLink(ZONE))
        .setLabels(labels)
        .setLabelFingerprint(labelFingerprint)
        .setMetadata(gcpMetata);
  }
}
