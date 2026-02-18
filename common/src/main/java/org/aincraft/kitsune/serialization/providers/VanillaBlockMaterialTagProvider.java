package org.aincraft.kitsune.serialization.providers;

import org.aincraft.kitsune.Item;
import org.aincraft.kitsune.api.serialization.TagProvider;

import java.util.Collection;

/**
 * Provides tags for block material types (glass, wool, wood, stone, etc).
 */
public final class VanillaBlockMaterialTagProvider implements TagProvider {

  @Override
  public void appendTags(Collection<String> tags, Item item) {
    String upper = item.material().toUpperCase();

    // Glass
    if (upper.contains("GLASS")) {
      tags.add("glass");
      if (upper.contains("PANE")) {
        tags.add("pane");
      }
    }

    // Wool
    if (upper.contains("WOOL")) {
      tags.add("wool");
      tags.add("soft");
    }

    // Concrete
    if (upper.contains("CONCRETE")) {
      tags.add("concrete");
      if (upper.contains("POWDER")) {
        tags.add("powder");
      }
    }

    // Terracotta
    if (upper.contains("TERRACOTTA")) {
      tags.add("terracotta");
      tags.add("clay");
      if (upper.contains("GLAZED")) {
        tags.add("glazed");
      }
    }

    // Candles
    if (upper.contains("CANDLE")) {
      tags.add("candle");
      tags.add("lightsource");
    }

    // Carpet
    if (upper.contains("CARPET")) {
      tags.add("carpet");
      tags.add("flooring");
    }

    // Beds
    if (upper.contains("BED") && !upper.contains("BEDROCK")) {
      tags.add("bed");
      tags.add("furniture");
    }

    // Banners
    if (upper.contains("BANNER")) {
      tags.add("banner");
      tags.add("decorative");
    }

    // Shulker boxes
    if (upper.contains("SHULKER")) {
      tags.add("shulker");
      tags.add("storage");
      tags.add("container");
    }

    // Wood types
    if (upper.contains("OAK") && !upper.contains("DARK_OAK")) {
      tags.add("oak");
      tags.add("wood");
    }
    if (upper.contains("SPRUCE")) {
      tags.add("spruce");
      tags.add("wood");
    }
    if (upper.contains("BIRCH")) {
      tags.add("birch");
      tags.add("wood");
    }
    if (upper.contains("JUNGLE")) {
      tags.add("jungle");
      tags.add("wood");
    }
    if (upper.contains("ACACIA")) {
      tags.add("acacia");
      tags.add("wood");
    }
    if (upper.contains("DARK_OAK")) {
      tags.add("darkoak");
      tags.add("wood");
    }
    if (upper.contains("MANGROVE")) {
      tags.add("mangrove");
      tags.add("wood");
    }
    if (upper.contains("CHERRY")) {
      tags.add("cherry");
      tags.add("wood");
    }
    if (upper.contains("BAMBOO")) {
      tags.add("bamboo");
      tags.add("wood");
    }
    if (upper.contains("CRIMSON")) {
      tags.add("crimson");
      tags.add("netherstem");
    }
    if (upper.contains("WARPED")) {
      tags.add("warped");
      tags.add("netherstem");
    }

    // Stone variants
    if (upper.contains("STONE") && !upper.contains("REDSTONE") && !upper.contains("GLOWSTONE")
        && !upper.contains("SANDSTONE")) {
      tags.add("stone");
    }
    if (upper.contains("COBBLESTONE")) {
      tags.add("cobblestone");
      tags.add("cobble");
    }
    if (upper.contains("DEEPSLATE")) {
      tags.add("deepslate");
    }
    if (upper.contains("BRICK")) {
      tags.add("brick");
    }
    if (upper.contains("SANDSTONE")) {
      tags.add("sandstone");
    }

    // Ores
    if (upper.contains("_ORE")) {
      tags.add("ore");
      tags.add("mineable");
    }
  }
}
