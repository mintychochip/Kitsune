package org.aincraft.kitsune.serialization.providers;

import java.util.Collection;
import java.util.Map;
import org.aincraft.kitsune.Item;
import org.aincraft.kitsune.api.serialization.TagProvider;

public class VanillaEnchantmentTagProvider implements TagProvider {

  @Override
  public void appendTags(Collection<String> tags, Item item) {
    Map<String, Integer> enchantments = item.enchantments();

    if (!enchantments.isEmpty()) {
      tags.add("enchanted");
    }

    for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
      String key = entry.getKey();
      Integer level = entry.getValue();

      // Add enchantment key as tag (e.g., "sharpness")
      tags.add(key);

      // Add enchantment level tag (e.g., "sharpness_5")
      tags.add(key + "_" + level);
    }
  }
}
