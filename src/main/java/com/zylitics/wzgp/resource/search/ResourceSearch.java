package com.zylitics.wzgp.resource.search;

import java.util.Optional;

import javax.annotation.Nullable;

import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.zylitics.wzgp.model.InstanceStatus;
import com.zylitics.wzgp.resource.BuildProperty;

public interface ResourceSearch {

  /**
   * 
   * @param searchParam {@link ResourceSearchParam} object
   * @param zone zone where to search
   * @param buildProp {@link BuildProperty} object
   * @return A randomly selected {@link Instance} from the list of fetched instances given
   *         availability, else an empty {@link Optional}. The random selection process choose an
   *         index between 0 to (total-fetched-instances - 1) randomly so that near parallel
   *         requests don't find the same instance upon search. 
   * @throws Exception If there are problems searching
   */
  Optional<Instance> searchInstance(ResourceSearchParam searchParam,
                                    String zone,
                                    InstanceStatus instanceStatus,
                                    @Nullable BuildProperty buildProp) throws Exception;
  
  /**
   * 
   * @param searchParam {@link ResourceSearchParam} object
   * @param buildProp {@link BuildProperty} object
   * @return An {@link Image}, only one image is fetched and returned if available, else an empty
   *         {@link Optional}.
   * @throws Exception If there are problems searching
   */
  Optional<Image> searchImage(ResourceSearchParam searchParam
      , @Nullable BuildProperty buildProp) throws Exception;
}
