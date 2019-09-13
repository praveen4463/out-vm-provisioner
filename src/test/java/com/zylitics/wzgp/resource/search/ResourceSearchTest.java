package com.zylitics.wzgp.resource.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.compute.ComputeService;
import com.zylitics.wzgp.test.dummy.DummyRequestGridCreate;
import com.zylitics.wzgp.test.util.ResourceTestUtil;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness=Strictness.STRICT_STUBS)
public class ResourceSearchTest {
  
  private static final ResourceSearchParam SEARCH_PARAMS =
      new DummyRequestGridCreate().get().getResourceSearchParams();
  
  @Test
  @DisplayName("verify instantiating ResourceSearch with incorrect ResourceSearchParam throws")
  void instantiateWithIncorrectParams() {
    ResourceSearchParam searchParams = spy(SEARCH_PARAMS);
    when(searchParams.getBrowser()).thenReturn(null);
    assertThrows(IllegalArgumentException.class, () -> {
      ResourceSearch.Factory.getDefault().create(mock(APICoreProperties.class)
          , mock(ComputeService.class), searchParams); 
    });
  }

  @Test
  @DisplayName("verify image search parameters are valid")
  void searchImageTest() throws Exception {
    String imageName = "win2008-conf-1";
    ComputeService computeSrv = mock(ComputeService.class);
    APICoreProperties apiCoreProps = mock(APICoreProperties.class);
    ResourceSearchParam searchParams = spy(SEARCH_PARAMS);
    
    APICoreProperties.GridDefault gridDefault = mock(APICoreProperties.GridDefault.class);
    when(gridDefault.getImageSearchParams()).thenReturn(ImmutableMap.of("labels.image-id", "xyz"));
    
    when(apiCoreProps.getGridDefault()).thenReturn(gridDefault);
    
    // make sure we assume nothing is given as custom image search params. 
    when(searchParams.getCustomImageSearchParams()).thenReturn(null);
    
    String filter = "(labels.image-id = \"xyz\") AND " + getRequestFilters();
    
    when(computeSrv.listImages(filter, 1L, null))
        .thenReturn(ImmutableList.of(new Image().setName(imageName)));
    Image image = ResourceSearch.Factory.getDefault().create(apiCoreProps, computeSrv
        , searchParams).searchImage().get();
    assertEquals(imageName, image.getName());
  }
  
  @Test
  @DisplayName("verify instance search parameters are valid")
  void searchStoppedInstanceTest() throws Exception {
    String instanceName = "instance-1";
    String zone = "zone-1";
    
    ComputeService computeSrv = mock(ComputeService.class);
    APICoreProperties apiCoreProps = mock(APICoreProperties.class);
    ResourceSearchParam searchParams = spy(SEARCH_PARAMS);
    
    APICoreProperties.GridDefault gridDefault = mock(APICoreProperties.GridDefault.class);
    when(gridDefault.getInstanceSearchParams()).thenReturn(
        ImmutableMap.of("labels.is-production-instance", "true", "status", "TERMINATED"));
    
    when(apiCoreProps.getGridDefault()).thenReturn(gridDefault);
    
    // return same key to see its replacing the default one.
    when(searchParams.getCustomInstanceSearchParams()).thenReturn(
        ImmutableMap.of("labels.is-production-instance", "false"));
    
    String filter = "(labels.is-production-instance = \"false\") AND (status = \"TERMINATED\") AND "
        + getRequestFilters();
    
    when(computeSrv.listInstances(filter, 1L, zone, null))
        .thenReturn(ImmutableList.of(
            new Instance().setName(instanceName).setZone(ResourceTestUtil.getZoneLink(zone))));
    
    Instance instance = ResourceSearch.Factory.getDefault().create(apiCoreProps, computeSrv
        , searchParams).searchStoppedInstance(zone).get();
    
    assertEquals(instanceName, instance.getName());
  }
  
  private String getRequestFilters() {
    StringBuilder sb = new StringBuilder();
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
    sb.append(String.format("(labels.shots = \"%s\")", SEARCH_PARAMS.getShots()));
    return sb.toString();
  }
}
