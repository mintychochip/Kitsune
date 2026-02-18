package org.aincraft.kitsune.serialization.providers;

import org.aincraft.kitsune.Item;
import org.aincraft.kitsune.api.serialization.TagProvider;

import java.util.Collection;

/**
 * Provides tags for armor: helmets, chestplates, leggings, boots, and wearables.
 */
public class VanillaArmorTagProvider implements TagProvider {

  @Override
  public void appendTags(Collection<String> tags, Item item) {
    String upper = item.material().toUpperCase();

    // Helmets
    if (upper.endsWith("_HELMET") || upper.equals("TURTLE_HELMET")) {
      tags.add("armor");
      tags.add("helmet");
      tags.add("headgear");
      tags.add("head");
      tags.add("wearable");
      addMaterialTags(upper, tags);
      if (upper.equals("TURTLE_HELMET")) {
        tags.add("turtle");
        tags.add("waterbreathing");
      }
    }

    // Chestplates
    if (upper.endsWith("_CHESTPLATE")) {
      tags.add("armor");
      tags.add("chestplate");
      tags.add("chest");
      tags.add("body");
      tags.add("wearable");
      addMaterialTags(upper, tags);
    }

    // Leggings
    if (upper.endsWith("_LEGGINGS")) {
      tags.add("armor");
      tags.add("leggings");
      tags.add("pants");
      tags.add("legs");
      tags.add("wearable");
      addMaterialTags(upper, tags);
    }

    // Boots
    if (upper.endsWith("_BOOTS")) {
      tags.add("armor");
      tags.add("boots");
      tags.add("footwear");
      tags.add("feet");
      tags.add("wearable");
      addMaterialTags(upper, tags);
    }

    // Elytra
    if (upper.equals("ELYTRA")) {
      tags.add("armor");
      tags.add("elytra");
      tags.add("wings");
      tags.add("chest");
      tags.add("wearable");
      tags.add("flying");
      tags.add("gliding");
    }

    // Horse armor
    if (upper.contains("_HORSE_ARMOR")) {
      tags.add("armor");
      tags.add("horsearmor");
      tags.add("horse");
      tags.add("mount");
      tags.add("pet");
      if (upper.contains("IRON")) {
        tags.add("iron");
      } else if (upper.contains("GOLDEN") || upper.contains("GOLD")) {
        tags.add("gold");
      } else if (upper.contains("DIAMOND")) {
        tags.add("diamond");
      } else if (upper.contains("LEATHER")) {
        tags.add("leather");
      }
    }

    // Wolf armor
    if (upper.equals("WOLF_ARMOR")) {
      tags.add("armor");
      tags.add("wolfarmor");
      tags.add("wolf");
      tags.add("pet");
    }

    // Leather special handling (dyeable)
    if (upper.startsWith("LEATHER_")) {
      tags.add("leather");
      tags.add("dyeable");
    }

    // Chainmail
    if (upper.startsWith("CHAINMAIL_")) {
      tags.add("chainmail");
      tags.add("chain");
    }

    // Heads/skulls (wearable)
    if (upper.contains("_HEAD") || upper.contains("_SKULL") || upper.equals("PLAYER_HEAD")
        || upper.equals("DRAGON_HEAD")) {
      tags.add("head");
      tags.add("headgear");
      tags.add("wearable");
      tags.add("decorative");
      if (upper.contains("SKELETON")) {
        tags.add("skeleton");
      } else if (upper.contains("ZOMBIE")) {
        tags.add("zombie");
      } else if (upper.contains("CREEPER")) {
        tags.add("creeper");
      } else if (upper.contains("WITHER")) {
        tags.add("wither");
      } else if (upper.contains("DRAGON")) {
        tags.add("dragon");
      } else if (upper.contains("PIGLIN")) {
        tags.add("piglin");
      } else if (upper.contains("PLAYER")) {
        tags.add("player");
      }
    }

    // Carved pumpkin (wearable)
    if (upper.equals("CARVED_PUMPKIN")) {
      tags.add("head");
      tags.add("headgear");
      tags.add("wearable");
      tags.add("pumpkin");
      tags.add("enderman");
    }
  }

  private void addMaterialTags(String material, Collection<String> tags) {
    if (material.contains("NETHERITE")) {
      tags.add("netherite");
    } else if (material.contains("DIAMOND")) {
      tags.add("diamond");
    } else if (material.contains("IRON")) {
      tags.add("iron");
    } else if (material.contains("CHAINMAIL")) {
      tags.add("chainmail");
    } else if (material.contains("GOLDEN") || material.contains("GOLD")) {
      tags.add("gold");
      tags.add("golden");
    } else if (material.contains("LEATHER")) {
      tags.add("leather");
    }
  }
}
