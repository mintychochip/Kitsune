package org.aincraft.kitsune.serialization.providers;

import java.util.Collection;
import org.aincraft.kitsune.Item;
import org.aincraft.kitsune.api.serialization.TagProvider;

public class VanillaRedstoneTagProvider implements TagProvider {

  @Override
  public void appendTags(Collection<String> tags, Item item) {
    String material = item.material().toUpperCase();

    if (material.contains("REDSTONE")) {
      tags.add("redstone");
    }
    if (material.contains("POWERED")) {
      tags.add("powered");
    }
    if (material.contains("COMPARATOR")) {
      tags.add("comparator");
    }
    if (material.contains("REPEATER")) {
      tags.add("repeater");
    }
  }
}
