package com.zylitics.wzgp.resource.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.api.services.compute.model.Image;

/**
 * Merge image and server defined labels, image defined labels take precedence. Following are
 * the customizations required to build valid set of labels for new grid instance:
 * 1. There are few labels specific to image, we'll exclude them.
 * 2. We'll put some labels known only at runtime for the grid, such as source-image-family
 * 3. We'll customize some labels based on the specific inputs to the api.
 */
public class BuildGridLabels {
  
  private final Image image;
  private final Map<String, String> defaultLabels;
  private final Map<String, String> customLabels;
  private final Set<String> imageSpecificLabelKeys;

  public BuildGridLabels(Image image
      , Map<String, String> defaultLabels
      , Map<String, String> customLabels
      , Set<String> imageSpecificLabelKeys) {
    this.image = image;
    this.defaultLabels = defaultLabels;
    this.customLabels = customLabels;
    this.imageSpecificLabelKeys = imageSpecificLabelKeys;
  }
  
  public Map<String, String> build() {
    // first put default labels specified by server
    Map<String, String> mergedLabels = new HashMap<>();
    mergedLabels.putAll(defaultLabels);
    
    // put after getting labels from image and filter image specific keys.
    Map<String, String> gridLabelsFromImage = image.getLabels().entrySet().stream()
        .filter(entry -> !imageSpecificLabelKeys.contains(entry.getKey()))
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    mergedLabels.putAll(gridLabelsFromImage);
    
    // put in custom labels so that it overrides any matching entry.
    mergedLabels.putAll(customLabels);
    
    // put in runtime specific labels
    mergedLabels.put("source-image-family", image.getFamily());
    
    return mergedLabels;
  }
}
