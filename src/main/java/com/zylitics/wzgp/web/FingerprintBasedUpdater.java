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
import com.google.common.collect.ImmutableMap;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.compute.ComputeService;
import com.zylitics.wzgp.resource.util.ResourceUtil;

/**
 * <p>This class is used to update resources that requires up-to-date fingerprint matching with GCP,
 * for example, labels, metadata, tags, network-interfaces. It doesn't keep any state of fingerprint
 * and simply fetches fresh every time which is the safest way to resolve any future issues and
 * guaranteed latest fingerprint value.</p>
 * <p> During the update, it fetches the latest set of values of the resources, merges the given
 * values with it to derive a full set of values so that GCP can replace the whole set of values
 * with what currently set there. </p>
 * @author Praveen Tiwari
 *
 */
@Component
@Scope("singleton")
public class FingerprintBasedUpdater {
  
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
    instance = computeSrv.getInstance(instance.getName(), zoneName, buildProp);
    
    // first put instance labels, then requested.
    Map<String, String> mergedLabels = new HashMap<>();
    if (instance.getLabels() != null) {
      mergedLabels.putAll(instance.getLabels());
    }
    mergedLabels.putAll(labels);
    
    Operation operation = computeSrv.setLabels(instance.getName(), mergedLabels, zoneName
        , instance.getLabelFingerprint(), buildProp);
    
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
    instance = computeSrv.getInstance(instance.getName(), zoneName, buildProp);
    Metadata gcpMetadata = instance.getMetadata();
    
    // first put instance metadata, then requested.
    Map<String, String> mergedMetadata = new HashMap<>();
    if (gcpMetadata.getItems() != null) {
      gcpMetadata.getItems().forEach(items -> mergedMetadata.put(items.getKey(), items.getValue()));
    }
    mergedMetadata.putAll(metadata);
    
    Operation operation = computeSrv.setMetadata(instance.getName(), mergedMetadata, zoneName
        , gcpMetadata.getFingerprint(), buildProp);
    
    return operation;
  }
  
  public Operation deleteAllMetadata(Instance instance, @Nullable BuildProperty buildProp)
      throws Exception {
    Assert.notNull(instance, "'instance' can't be null");
    
    String zoneName = ResourceUtil.nameFromUrl(instance.getZone());
    instance = computeSrv.getInstance(instance.getName(), zoneName, buildProp);
    Metadata gcpMetadata = instance.getMetadata();
    
    Map<String, String> emptyMetadata = ImmutableMap.of();
    
    Operation operation = computeSrv.setMetadata(instance.getName(), emptyMetadata, zoneName
        , gcpMetadata.getFingerprint(), buildProp);
    
    return operation;
  }
}
