package org.aincraft.kitsune.serialization.providers;

import org.aincraft.kitsune.Item;
import org.aincraft.kitsune.api.serialization.TagProvider;

import java.util.Collection;

/**
 * Provides tags for tools: pickaxes, shovels, hoes, shears, etc.
 */
public final class VanillaToolTagProvider implements TagProvider {

    @Override
    public void appendTags(Collection<String> tags, Item item) {
        String upper = item.material().toUpperCase();

        // Pickaxes
        if (upper.endsWith("_PICKAXE")) {
            tags.add("tool");
            tags.add("pickaxe");
            tags.add("mining");
            tags.add("breaking");
            addMaterialTags(upper, tags);
        }

        // Shovels
        if (upper.endsWith("_SHOVEL")) {
            tags.add("tool");
            tags.add("shovel");
            tags.add("digging");
            tags.add("breaking");
            addMaterialTags(upper, tags);
        }

        // Hoes
        if (upper.endsWith("_HOE")) {
            tags.add("tool");
            tags.add("hoe");
            tags.add("farming");
            tags.add("tilling");
            addMaterialTags(upper, tags);
        }

        // Axes (also tools)
        if (upper.endsWith("_AXE") && !upper.contains("PICKAXE")) {
            tags.add("tool");
            tags.add("axe");
            tags.add("woodcutting");
            tags.add("chopping");
            tags.add("breaking");
            addMaterialTags(upper, tags);
        }

        // Shears
        if (upper.equals("SHEARS")) {
            tags.add("tool");
            tags.add("shears");
            tags.add("shearing");
            tags.add("harvesting");
        }

        // Flint and steel
        if (upper.equals("FLINT_AND_STEEL")) {
            tags.add("tool");
            tags.add("flintandsteel");
            tags.add("fire");
            tags.add("igniter");
        }

        // Fishing rod
        if (upper.equals("FISHING_ROD")) {
            tags.add("tool");
            tags.add("fishingrod");
            tags.add("fishing");
            tags.add("catching");
        }

        // Carrot/warped fungus on a stick
        if (upper.equals("CARROT_ON_A_STICK") || upper.equals("WARPED_FUNGUS_ON_A_STICK")) {
            tags.add("tool");
            tags.add("riding");
            tags.add("control");
        }

        // Lead
        if (upper.equals("LEAD")) {
            tags.add("tool");
            tags.add("lead");
            tags.add("leash");
            tags.add("mob");
        }

        // Name tag
        if (upper.equals("NAME_TAG")) {
            tags.add("tool");
            tags.add("nametag");
            tags.add("naming");
            tags.add("mob");
        }

        // Brush
        if (upper.equals("BRUSH")) {
            tags.add("tool");
            tags.add("brush");
            tags.add("archaeology");
            tags.add("excavation");
        }

        // Spyglass
        if (upper.equals("SPYGLASS")) {
            tags.add("tool");
            tags.add("spyglass");
            tags.add("zoom");
            tags.add("scouting");
        }

        // Compass
        if (upper.equals("COMPASS") || upper.equals("RECOVERY_COMPASS")) {
            tags.add("tool");
            tags.add("compass");
            tags.add("navigation");
            if (upper.equals("RECOVERY_COMPASS")) {
                tags.add("recovery");
                tags.add("death");
            }
        }

        // Clock
        if (upper.equals("CLOCK")) {
            tags.add("tool");
            tags.add("clock");
            tags.add("time");
        }

        // Map
        if (upper.contains("MAP")) {
            tags.add("tool");
            tags.add("map");
            tags.add("navigation");
            if (upper.equals("FILLED_MAP")) {
                tags.add("filled");
            }
        }

        // Bucket variants
        if (upper.contains("BUCKET")) {
            tags.add("tool");
            tags.add("bucket");
            tags.add("container");
            if (upper.equals("WATER_BUCKET")) {
                tags.add("water");
            } else if (upper.equals("LAVA_BUCKET")) {
                tags.add("lava");
            } else if (upper.equals("MILK_BUCKET")) {
                tags.add("milk");
            } else if (upper.equals("POWDER_SNOW_BUCKET")) {
                tags.add("powdersnow");
            } else if (upper.contains("FISH_BUCKET") || upper.equals("AXOLOTL_BUCKET") || upper.equals("TADPOLE_BUCKET")) {
                tags.add("mobbucket");
                tags.add("mob");
            }
        }

        // Bone meal (farming tool)
        if (upper.equals("BONE_MEAL")) {
            tags.add("tool");
            tags.add("bonemeal");
            tags.add("farming");
            tags.add("growth");
        }

        // Writable/written books
        if (upper.equals("WRITABLE_BOOK") || upper.equals("WRITTEN_BOOK")) {
            tags.add("tool");
            tags.add("book");
            tags.add("writing");
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
