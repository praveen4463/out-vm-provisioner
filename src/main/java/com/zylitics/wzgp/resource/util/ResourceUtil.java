package com.zylitics.wzgp.resource.util;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.api.client.util.Strings;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.Operation;

public class ResourceUtil {
  
  public static final String LABEL_LOCKED_BY_BUILD = "locked-by-build";
  public static final String LABEL_IS_DELETING = "is-deleting";
  public static final String LABEL_SOURCE_FAMILY = "source-image-family";
  public static final String METADATA_CURRENT_TEST_SESSIONID = "current-test-sessionId";

  public static boolean isOperationSuccess(Operation operation) {
    if (operation.getStatus().equals("DONE")
        &&
        operation.getHttpErrorStatusCode() >= 200 && operation.getHttpErrorStatusCode() < 300
        &&
        (operation.getError() == null
        || operation.getError().getErrors() == null
        || operation.getError().getErrors().size() == 0)
        && !Strings.isNullOrEmpty(operation.getName())) {
      return true;
    }
    return false;
  }
  
  public static String getResourceNameFromUrl(String resourceAsUrl) {
    if (Strings.isNullOrEmpty(resourceAsUrl)) {
      throw new RuntimeException("The resource url can't be empty.");
    }
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
}
