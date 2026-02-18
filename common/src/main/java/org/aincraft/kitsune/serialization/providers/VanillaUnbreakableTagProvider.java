package org.aincraft.kitsune.serialization.providers;

import java.util.Collection;
import org.aincraft.kitsune.Item;
import org.aincraft.kitsune.api.serialization.TagProvider;

public class VanillaUnbreakableTagProvider implements TagProvider {

  @Override
  public void appendTags(Collection<String> tags, Item item) {
    if (item.isUnbreakable()) {
      tags.add("unbreakable");
    }
  }
}
