package org.aincraft.kitsune.serialization.providers;

import java.util.Collection;
import org.aincraft.kitsune.Item;
import org.aincraft.kitsune.api.serialization.TagProvider;

public class VanillaBlockColorTagProvider implements TagProvider {

  private static final String[] VANILLA_COLORS = {
    "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
    "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
  };

  @Override
  public void appendTags(Collection<String> tags, Item item) {
    String material = item.material().toUpperCase();

    for (String color : VANILLA_COLORS) {
      String colorPrefix = color.toUpperCase() + "_";
      if (material.startsWith(colorPrefix)) {
        tags.add(color);
        break;
      }
    }
  }
}
