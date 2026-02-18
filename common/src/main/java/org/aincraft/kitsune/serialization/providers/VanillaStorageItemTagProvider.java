package org.aincraft.kitsune.serialization.providers;

import java.util.Collection;
import org.aincraft.kitsune.Item;
import org.aincraft.kitsune.api.serialization.TagProvider;

/**
 * Provides tags for storage items: bundles, shulker boxes, chests, barrels, and other
 * container items.
 */
public final class VanillaStorageItemTagProvider implements TagProvider {

  @Override
  public void appendTags(Collection<String> tags, Item item) {
    String upper = item.material().toUpperCase();

    // Bundle (check both by data component and material type)
    if (item.hasBundle()) {
      tags.add("bundle");
      tags.add("storage");
      tags.add("container");
    }

    // Shulker box (check both by data component and material name)
    if (item.hasShulkerContents()) {
      tags.add("shulkerbox");
      tags.add("storage");
      tags.add("container");
    }

    if (upper.contains("SHULKER_BOX")) {
      tags.add("shulkerbox");
      tags.add("storage");
      tags.add("container");
    }

    // Chest
    if (upper.contains("CHEST")) {
      tags.add("chest");
      tags.add("storage");
    }

    // Barrel
    if (upper.contains("BARREL")) {
      tags.add("barrel");
      tags.add("storage");
    }

    // Bundle material (redundant but explicit)
    if (upper.equals("BUNDLE")) {
      tags.add("bundle");
      tags.add("storage");
      tags.add("container");
    }
  }
}
