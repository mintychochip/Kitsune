package org.aincraft.kitsune.serialization.providers;

import java.util.Collection;
import org.aincraft.kitsune.Item;
import org.aincraft.kitsune.api.serialization.TagProvider;

public final class VanillaBlockGravityTagProvider implements TagProvider {

  @Override
  public void appendTags(Collection<String> tags, Item item) {
    if (item.hasGravity()) {
      tags.add("gravity");
      tags.add("falling");
    }
  }
}
