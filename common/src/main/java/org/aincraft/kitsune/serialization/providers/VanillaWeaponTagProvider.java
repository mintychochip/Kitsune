package org.aincraft.kitsune.serialization.providers;

import org.aincraft.kitsune.Item;
import org.aincraft.kitsune.api.serialization.TagProvider;

import java.util.Collection;

/**
 * Provides tags for weapons: swords, bows, crossbows, tridents, etc.
 */
public final class VanillaWeaponTagProvider implements TagProvider {

    @Override
    public void appendTags(Collection<String> tags, Item item) {
        String upper = item.material().toUpperCase();

        // Swords
        if (upper.endsWith("_SWORD")) {
            tags.add("weapon");
            tags.add("sword");
            tags.add("melee");
            tags.add("combat");
            addMaterialTags(upper, tags);
        }

        // Axes (also weapons)
        if (upper.endsWith("_AXE") && !upper.contains("PICKAXE")) {
            tags.add("weapon");
            tags.add("axe");
            tags.add("melee");
            tags.add("combat");
            addMaterialTags(upper, tags);
        }

        // Bows
        if (upper.equals("BOW")) {
            tags.add("weapon");
            tags.add("bow");
            tags.add("ranged");
            tags.add("projectile");
            tags.add("combat");
        }

        // Crossbows
        if (upper.equals("CROSSBOW")) {
            tags.add("weapon");
            tags.add("crossbow");
            tags.add("ranged");
            tags.add("projectile");
            tags.add("combat");
        }

        // Trident
        if (upper.equals("TRIDENT")) {
            tags.add("weapon");
            tags.add("trident");
            tags.add("melee");
            tags.add("ranged");
            tags.add("throwable");
            tags.add("combat");
        }

        // Mace (1.21+)
        if (upper.equals("MACE")) {
            tags.add("weapon");
            tags.add("mace");
            tags.add("melee");
            tags.add("combat");
            tags.add("heavyhitter");
        }

        // Arrows
        if (upper.contains("ARROW")) {
            tags.add("ammo");
            tags.add("ammunition");
            tags.add("projectile");
            tags.add("combat");
            if (upper.equals("SPECTRAL_ARROW")) {
                tags.add("spectral");
            }
            if (upper.equals("TIPPED_ARROW")) {
                tags.add("tipped");
                tags.add("potion");
            }
        }

        // Firework (crossbow ammo)
        if (upper.equals("FIREWORK_ROCKET")) {
            tags.add("ammo");
            tags.add("firework");
            tags.add("projectile");
            tags.add("elytra");
        }

        // Snowballs, eggs, ender pearls (throwables)
        if (upper.equals("SNOWBALL") || upper.equals("EGG")) {
            tags.add("weapon");
            tags.add("throwable");
            tags.add("projectile");
        }

        if (upper.equals("ENDER_PEARL")) {
            tags.add("throwable");
            tags.add("teleport");
            tags.add("projectile");
        }

        // Wind charge
        if (upper.equals("WIND_CHARGE")) {
            tags.add("weapon");
            tags.add("throwable");
            tags.add("projectile");
            tags.add("knockback");
        }

        // Shield
        if (upper.equals("SHIELD")) {
            tags.add("weapon");
            tags.add("shield");
            tags.add("defense");
            tags.add("combat");
            tags.add("blocking");
        }
    }

    private void addMaterialTags(String material, Collection<String> tags) {
        if (material.contains("NETHERITE")) {
            tags.add("netherite");
        } else if (material.contains("DIAMOND")) {
            tags.add("diamond");
        } else if (material.contains("IRON")) {
            tags.add("iron");
        } else if (material.contains("GOLDEN") || material.contains("GOLD")) {
            tags.add("gold");
            tags.add("golden");
        } else if (material.contains("STONE")) {
            tags.add("stone");
        } else if (material.contains("WOODEN") || material.contains("WOOD")) {
            tags.add("wood");
            tags.add("wooden");
        }
    }
}
