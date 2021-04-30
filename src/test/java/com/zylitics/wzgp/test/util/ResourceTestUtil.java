package com.zylitics.wzgp.test.util;

import com.zylitics.wzgp.test.dummy.DummyAPICoreProperties;

public class ResourceTestUtil {

  public static String getOperationTargetLink(String resourceName, String zone) {
    DummyAPICoreProperties apiProps = new DummyAPICoreProperties();
    return String.format("%s/%s/zones/%s/instances/%s", apiProps.getGceApiUrl()
        , apiProps.getResourceProjectId(), zone, resourceName);
  }
  
  public static String getZoneLink(String zone) {
    DummyAPICoreProperties apiProps = new DummyAPICoreProperties();
    return String.format("%s/%s/zones/%s", apiProps.getGceApiUrl(), apiProps.getResourceProjectId(), zone);
  }
}
