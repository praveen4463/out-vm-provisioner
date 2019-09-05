package com.zylitics.wzgp.resource.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.google.common.collect.ImmutableList;
import com.zylitics.wzgp.resource.service.ComputeService;
import com.zylitics.wzgp.test.dummy.DummyRequestGridCreate;
import com.zylitics.wzgp.test.util.ResourceTestUtil;

public class ResourceSearchTest {
  
  private static final ResourceSearchParam SEARCH_PARAMS =
      new DummyRequestGridCreate().get().getResourceSearchParams();

  @Test
  @DisplayName("verify image search parameters are valid")
  void searchImageTest() throws Exception {
    String imageName = "win2008-conf-1";
    ComputeService computeSrv = mock(ComputeService.class);
    when(computeSrv.listImages(buildCommonFilter(), 1L, null))
        .thenReturn(ImmutableList.of(new Image().setName(imageName)));
    Image image = ResourceSearch.Factory.getDefault().create(computeSrv, SEARCH_PARAMS)
        .searchImage().get();
    assertEquals(imageName, image.getName());
  }
  
  @Test
  @DisplayName("verify instance search parameters are valid")
  void searchStoppedInstanceTest() throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("(status = \"TERMINATED\")");
    sb.append(" AND ");
    sb.append("(labels.is-production-instance = \"true\")");
    sb.append(" AND ");
    sb.append("(labels.zl-selenium-grid = \"true\")");
    sb.append(" AND ");
    sb.append("(labels.locked-by-build = \"none\")");
    sb.append(" AND ");
    sb.append("(labels.is-deleting = \"false\")");
    sb.append(" AND ");
    sb.append(buildCommonFilter());
    String instanceName = "instance-1";
    String zone = "zone-1";
    ComputeService computeSrv = mock(ComputeService.class);
    
    when(computeSrv.listInstances(sb.toString(), 1L, zone, null))
        .thenReturn(ImmutableList.of(
            new Instance().setName(instanceName).setZone(ResourceTestUtil.getZoneLink(zone))));
    
    Instance instance = ResourceSearch.Factory.getDefault().create(computeSrv, SEARCH_PARAMS)
        .searchStoppedInstance(zone).get();
    assertEquals(instanceName, instance.getName());
  }
  
  private String buildCommonFilter() {
    StringBuilder sb = new StringBuilder();
    sb.append("(labels.platform = \"windows\")");
    sb.append(" AND ");
    sb.append(String.format("(labels.os = \"%s\")", SEARCH_PARAMS.getOS()));
    sb.append(" AND ");
    sb.append(String.format("(labels.browser1 = \"%s\")", SEARCH_PARAMS.getBrowser()));
    sb.append(" OR ");
    sb.append(String.format("(labels.browser2 = \"%s\")", SEARCH_PARAMS.getBrowser()));
    sb.append(" OR ");
    sb.append(String.format("(labels.browser3 = \"%s\")", SEARCH_PARAMS.getBrowser()));
    sb.append(" OR ");
    sb.append(String.format("(labels.browser4 = \"%s\")", SEARCH_PARAMS.getBrowser()));
    sb.append(" OR ");
    sb.append(String.format("(labels.browser5 = \"%s\")", SEARCH_PARAMS.getBrowser()));
    sb.append(" OR ");
    sb.append(String.format("(labels.browser6 = \"%s\")", SEARCH_PARAMS.getBrowser()));
    sb.append(" AND ");
    sb.append(String.format("(labels.shots = \"%s\")", SEARCH_PARAMS.isShots()));
    return sb.toString();
  }
}
