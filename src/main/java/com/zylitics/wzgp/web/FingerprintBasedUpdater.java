package com.zylitics.wzgp.web;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.Operation;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.service.ComputeService;
import com.zylitics.wzgp.resource.util.ResourceUtil;

// This updater doesn't worry about updates made to the same instance by some other process, for
// example when instance is getting started or deleted. It just manages the local updates done by
// the current process. Usually when some other process makes updates to the instance at the same
// time, the best way is to give up the current process rather than working on the same instance.
// Thus, if during start/delete, any other process tries to work on same resource, these will raise
// exception and should be handled appropriately, for example if start fails for any reason, a fresh
// instance should be created. 
/**
 * <p>This class is used to update resources that requires up-to-date fingerprint matching with GCP,
 * for example, labels, metadata, tags, network-interfaces. It keep the state within
 * {@link Instance} itself to learn whether a particular resource is out-of-sync with GCP and if so,
 * updates the Instances object with GCP before attempting an update.</p>
 * <p> During the update, it fetches the latest set of values of the resources, merges the given
 * values with it to derive a full set of values so that GCP can replace the whole set of values
 * with what currently set there. </p>
 * @author Praveen Tiwari
 *
 */
 // !!! This class mutates the state of Instance object, carefully review/test the code.
@Component
@Scope("singleton")
public class FingerprintBasedUpdater {
  
  public static final String STALE_FINGERPRINT_IDENTIFIER = "stale-fingerprint";
  
  private final ComputeService computeSrv;
  
  @Autowired
  public FingerprintBasedUpdater(ComputeService computeSrv) {
    this.computeSrv = computeSrv;
  }
  
  /**
   * Given just the labels that needs to be added/updated to instance at GCP, this method will merge
   * the given labels with the current set of labels set to instance to make a full set. Caller just
   * requires to give the labels that requires addition/updation.
   * @param instance Instance that require label add/updates.
   * @param labels Only labels that needs to be added/updated
   * @param buildProp Optional {@link BuildProperty} object
   * @return an {@link Operation} of the update process
   * @throws Exception
   */
  public Operation updateLabels(Instance instance, Map<String, String> labels
      , @Nullable BuildProperty buildProp) throws Exception {
    Assert.notNull(instance, "'instance' can't be null");
    Assert.isTrue(labels.size() > 0, "'labels' can't be empty");
    
    String zoneName = ResourceUtil.nameFromUrl(instance.getZone());
    
    if (instance.getLabelFingerprint().equals(STALE_FINGERPRINT_IDENTIFIER)) {
      // update resources for this instance. If fingerprint is stale, this means labels are stale
      // too. We'll update labels too so that we get fresh set of labels to be merged with the
      // given labels.
      updateInstance(instance, zoneName, buildProp);
    }
    
    // first put instance labels, then requested.
    Map<String, String> mergedLabels = new HashMap<>();
    if (instance.getLabels() != null) {
      mergedLabels.putAll(instance.getLabels());
    }
    mergedLabels.putAll(labels);
    
    Operation operation = computeSrv.setLabels(instance.getName(), mergedLabels, zoneName
        , instance.getLabelFingerprint(), buildProp);
    
    // set fingerprint to stale.
    instance.setLabelFingerprint(STALE_FINGERPRINT_IDENTIFIER);
    
    return operation;
  }
  
  /**
   * Given just the metadata that needs to be added/updated the instance, this method will merge the
   * given metadata with the current set of metadata set to instance to make a full set. Caller just
   * requires to give the labels that need addition/updation.
   * @param instance Instance that require label add/updates.
   * @param metadata Only metadata that needs to be added/updated
   * @param buildProp Optional {@link BuildProperty} object
   * @return an {@link Operation} of the update process
   * @throws Exception
   */
  public Operation updateMetadata(Instance instance, Map<String, String> metadata
      , @Nullable BuildProperty buildProp) throws Exception {
    Assert.notNull(instance, "'instance' can't be null");
    Assert.isTrue(metadata.size() > 0, "'metadata' can't be empty");
    
    String zoneName = ResourceUtil.nameFromUrl(instance.getZone());
    
    Metadata gcpMetadata = instance.getMetadata();
    
    if (gcpMetadata.getFingerprint().equals(STALE_FINGERPRINT_IDENTIFIER)) {
      // update resources for this instance. If fingerprint is stale, this means metadata are stale
      // too. We'll update metadata too so that we get fresh set of metadata to be merged with the
      // given metadata.
      updateInstance(instance, zoneName, buildProp);
    }
    
    // first put metadata labels, then requested.
    Map<String, String> mergedMetadata = new HashMap<>();
    if (gcpMetadata.getItems() != null) {
      gcpMetadata.getItems().forEach(items -> mergedMetadata.put(items.getKey(), items.getValue()));
    }
    mergedMetadata.putAll(metadata);
    
    Operation operation = computeSrv.setMetadata(instance.getName(), mergedMetadata, zoneName
        , gcpMetadata.getFingerprint(), buildProp);
    
    // set fingerprint to stale.
    gcpMetadata.setFingerprint(STALE_FINGERPRINT_IDENTIFIER);
    
    return operation;
  }
  
  private void updateInstance(Instance instance, String zoneName
      , @Nullable BuildProperty buildProp) throws Exception {
    Instance fresh = computeSrv.getInstance(instance.getName(), zoneName, buildProp);
    
    // set labels and its fingerprint
    instance.setLabelFingerprint(fresh.getLabelFingerprint());
    instance.setLabels(fresh.getLabels());
    
    // set metadata object that contains both metadata map and fingerprint
    Metadata gcpMetadata = instance.getMetadata();
    if (gcpMetadata != null) {
      gcpMetadata.setFingerprint(fresh.getMetadata().getFingerprint());
      gcpMetadata.setItems(fresh.getMetadata().getItems());
    }
    
    // TODO: write more set methods if we support updating tags, network-interfaces
  }
}
