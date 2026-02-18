package org.aincraft.kitsune.serialization.providers;

import java.util.Collection;
import org.aincraft.kitsune.Item;
import org.aincraft.kitsune.api.serialization.TagProvider;

public final class VanillaBlockOpacityTagProvider implements TagProvider {

  @Override
  public void appendTags(Collection<String> tags, Item item) {
    if (item.isOccluding()) {
      tags.add("occluding");
    }

    if (item.isBlock() && !item.isOccluding()) {
      tags.add("transparent");
    }
  }
}
