package com.zylitics.wzgp.test.dummy;

import com.google.common.collect.ImmutableMap;
import com.zylitics.wzgp.http.RequestGridCreate;
import com.zylitics.wzgp.http.RequestGridCreate.BuildProperties;
import com.zylitics.wzgp.http.RequestGridCreate.GridProperties;
import com.zylitics.wzgp.http.RequestGridCreate.ResourceSearchParams;
import com.zylitics.wzgp.resource.util.ResourceUtil;

public class DummyRequestGridCreate {

  private RequestGridCreate requestGridCreate;
  
  public RequestGridCreate get() {
    requestGridCreate = new RequestGridCreate();
    BuildProperties buildProps = requestGridCreate.getBuildProperties();
    buildProps.setBuildId("build-007");
    
    ResourceSearchParams resourceSearchParams = requestGridCreate.getResourceSearchParams();
    resourceSearchParams.setOS("win7");
    resourceSearchParams.setBrowser("chrome");
    resourceSearchParams.setShots(false);
    resourceSearchParams.setCustomInstanceSearchParams(
        ImmutableMap.of(ResourceUtil.LABEL_IS_PRODUCTION_INSTANCE, "false"));
    
    GridProperties gridProps = requestGridCreate.getGridProperties();
    gridProps.setMachineType("n2-standard");
    gridProps.setServiceAccount("dummy-service-account@gcp.com");
    gridProps.setPreemptible(false);
    gridProps.setCreateExternalIP(false);
    gridProps.setCustomLabels(ImmutableMap.of("is-production-instance", "false"));
    gridProps.setMetadata(ImmutableMap.of("screen", "1x1"));
    return requestGridCreate;
  }
}
