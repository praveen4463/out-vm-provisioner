package com.zylitics.wzgp.resource.util;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.util.Assert;

import com.google.api.client.util.Strings;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.Operation;

public class ResourceUtil {
  
  public static final String LABEL_LOCKED_BY_BUILD = "locked-by-build";
  public static final String LABEL_IS_DELETING = "is-deleting";
  public static final String LABEL_SOURCE_FAMILY = "source-image-family";
  public static final String LABEL_STOPPED_INSTANCE_CUSTOM_IDENTIFIER =
      "stopped-instance-custom-identifier";
  public static final String LABEL_IS_PRODUCTION_INSTANCE = "is-production-instance";
  
  public static final String METADATA_CURRENT_TEST_SESSIONID = "current-test-sessionId";

  public static boolean isOperationSuccess(Operation operation) {
    return operation.getStatus().equals("DONE")
        && (operation.getHttpErrorStatusCode() == null || (operation.getHttpErrorStatusCode() >= 200
            && operation.getHttpErrorStatusCode() < 300))
        && (operation.getError() == null || operation.getError().getErrors() == null
            || operation.getError().getErrors().size() == 0)
        && !Strings.isNullOrEmpty(operation.getTargetLink());
  }
  
  public static String nameFromUrl(String resourceAsUrl) {
    Assert.hasText(resourceAsUrl, "'resource url' can't be empty.");
    
    String[] bits = resourceAsUrl.split("/");
    return bits[bits.length - 1];
  }
  
  public static Metadata getGCPMetadata(Map<String, String> md) {
    Metadata metadata = new Metadata();
    List<Metadata.Items> metadataItems = md.entrySet().stream()
        .map(entry -> new Metadata.Items().setKey(entry.getKey()).setValue(entry.getValue()))
        .collect(Collectors.toList());
    metadata.setItems(metadataItems);
    return metadata;
  }
  
  /**
   * <p>First derives region from the zone by splitting zone from 'hyphen' and taking first two
   * items. Every GCP zone has format {region}-{zone_identifier}, for example in us-central1-f, the
   * region is 'us-central1' and zone_identifier is 'f'.<p>
   * <p>It then goes on and join word 'subnet' and 'hyphen' with the derived region. Thus for a
   * given zone us-central1-f, the resultant subnet is 'subnet-us-central1'
   * @param zone, a GCP zone
   * @return a zylitics defined subnet of format subnet-{region} 
   */
  public static String getSubnetURLFromZone(String sharedVpcProjectId, String zone) {
    Assert.hasText(zone, "'zone' can't be empty");
    
    String[] bits = zone.split("-");
    Assert.isTrue(bits.length == 3, "'zone' doesn't seems to be correct, couldn't derive"
        + " region from it, zone given: " + zone);
    
    String region = bits[0] + "-" + bits[1];
    return String.format("projects/%s/regions/%s/subnetworks/%s",
        sharedVpcProjectId,
        region,
        "subnet-" + region);
  }
}
