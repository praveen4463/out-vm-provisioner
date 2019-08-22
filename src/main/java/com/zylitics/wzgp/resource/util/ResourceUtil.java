package com.zylitics.wzgp.resource.util;

import com.google.api.client.util.Strings;
import com.google.api.services.compute.model.Operation;

public class ResourceUtil {
  
  public static final String LABEL_LOCKED_BY_BUILD = "locked-by-build";
  public static final String LABEL_IS_DELETING = "is-deleting";
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
}
