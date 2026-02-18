package org.aincraft.kitsune;

import java.util.Set;

public class ItemSerializer {

  private static String formatMaterialName(String rawName) {
    String[] words = rawName.toLowerCase().split("_");
    StringBuilder result = new StringBuilder();
    for (String word : words) {
      if (!result.isEmpty()) {
        result.append(" ");
      }
      if (!word.isEmpty()) {
        result.append(Character.toUpperCase(word.charAt(0)))
            .append(word.substring(1));
      }
    }
    return result.toString();
  }

  private static String createEmbeddingText(Item item, Set<String> tags) {
    StringBuilder sb = new StringBuilder();
    String materialName = item.material();

    // Formatted material name
    sb.append(formatMaterialName(materialName));

    // Add all tags from registry
    for (String tag : tags) {
      sb.append(" #").append(tag);
    }

    return sb.toString().toLowerCase();
  }
}
