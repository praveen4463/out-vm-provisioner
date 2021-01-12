package com.zylitics.wzgp.resource.search;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

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
class ResourceSearchImplTest {
  
  private static final ResourceSearchParam SEARCH_PARAMS =
      new DummyRequestGridCreate().get().getResourceSearchParams();

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
    Image image = new ResourceSearchImpl(apiCoreProps, computeSrv)
        .searchImage(searchParams, null).orElse(null);
    assertNotNull(image);
    assertEquals(imageName, image.getName());
  }
  
  @Test
  @DisplayName("verify instance search parameters are valid")
  void searchStoppedInstanceTest() throws Exception {
    // The search program is designed to return a random instance from the search result so that
    // instance that is in index 0 isn't always returned. This aids the re-attempt logic at
    // 'start handler' so that requests those fail to acquire a stopped instance can re-attempt and
    // get a distinct instance to try between re-attempts.
    // We try to verify that the search behaves the same way. We'll return a certain number of
    // instances from search (defined as maxInstancesInSearch) and hit the 'search' a few times to
    // see the same instance is not returned until a defined (maxTimesInvokeSearch) number of times
    // elapse. This will prove our program is reliable enough.
    int maxInstancesInSearch = 3;
    int maxTimesInvokeSearch = 5;
    String zone = "zone-1";
    String zoneURL = ResourceTestUtil.getZoneLink(zone);
    
    List<Instance> instances = new ArrayList<>();
    for (int i = 1; i <= maxInstancesInSearch; i++) {
      instances.add(new Instance().setName("instance-" + i).setZone(zoneURL));
    }
    
    ComputeService computeSrv = mock(ComputeService.class);
    APICoreProperties apiCoreProps = mock(APICoreProperties.class);
    ResourceSearchParam searchParams = spy(SEARCH_PARAMS);
    
    APICoreProperties.GridDefault gridDefault = mock(APICoreProperties.GridDefault.class);
    
    // let's set max stopped instance in search to something and try returning that many.
    when(gridDefault.getMaxStoppedInstanceInSearch()).thenReturn(maxInstancesInSearch);
    
    when(gridDefault.getInstanceSearchParams()).thenReturn(
        ImmutableMap.of("labels.is-production-instance", "true", "status", "TERMINATED"));
    
    when(apiCoreProps.getGridDefault()).thenReturn(gridDefault);
    
    // return same key to see its replacing the default one.
    when(searchParams.getCustomInstanceSearchParams()).thenReturn(
        ImmutableMap.of("labels.is-production-instance", "false"));
    
    String filter = "(labels.is-production-instance = \"false\") AND (status = \"TERMINATED\") AND "
        + getRequestFilters();
    
    when(computeSrv.listInstances(filter, maxInstancesInSearch, zone, null))
        .thenReturn(ImmutableList.copyOf(instances));
    
    ResourceSearch search = new ResourceSearchImpl(apiCoreProps, computeSrv);
    
    // we'll verify that multiple calls to find stopped instance will get a different instance
    // and not the same. A different instance may not be returned everytime as we're using random
    // number but if we try a few times, a different instance must return.
    // lets invoke search times and see if we get a different instance.
    String lastInstanceName = null;
    boolean success = false;
    for (int i = 0; i < maxTimesInvokeSearch; i++) {
      Instance instance = search.searchStoppedInstance(searchParams, zone, null).orElse(null);
      assertNotNull(instance);
      if (lastInstanceName != null && !lastInstanceName.equals(instance.getName())) {
        success = true;
        break;
      }
      lastInstanceName = instance.getName();
    }
    assertTrue(success);
  }
  
  private String getRequestFilters() {
    return String.format("(labels.os = \"%s\")", SEARCH_PARAMS.getOS()) +
        " AND " +
        String.format("(labels.browser1 = \"%s\")", SEARCH_PARAMS.getBrowser()) +
        " OR " +
        String.format("(labels.browser2 = \"%s\")", SEARCH_PARAMS.getBrowser()) +
        " OR " +
        String.format("(labels.browser3 = \"%s\")", SEARCH_PARAMS.getBrowser()) +
        " OR " +
        String.format("(labels.browser4 = \"%s\")", SEARCH_PARAMS.getBrowser()) +
        " OR " +
        String.format("(labels.browser5 = \"%s\")", SEARCH_PARAMS.getBrowser()) +
        " OR " +
        String.format("(labels.browser6 = \"%s\")", SEARCH_PARAMS.getBrowser()) +
        " AND " +
        String.format("(labels.shots = \"%s\")", SEARCH_PARAMS.isShots());
  }
}
