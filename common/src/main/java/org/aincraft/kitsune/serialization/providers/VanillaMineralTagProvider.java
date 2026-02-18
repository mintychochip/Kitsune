package org.aincraft.kitsune.serialization.providers;

import org.aincraft.kitsune.Item;
import org.aincraft.kitsune.api.serialization.TagProvider;

import java.util.Collection;

/**
 * Provides tags for minerals: ores, ingots, gems, raw materials, nuggets.
 */
public class VanillaMineralTagProvider implements TagProvider {

    @Override
    public void appendTags(Collection<String> tags, Item item) {
        String upper = item.material().toUpperCase();

        // Ores
        if (upper.endsWith("_ORE") || upper.equals("NETHER_GOLD_ORE") || upper.equals("ANCIENT_DEBRIS")) {
            tags.add("ore");
            tags.add("mineral");
            tags.add("mineable");
        }

        // Raw materials
        if (upper.startsWith("RAW_")) {
            tags.add("raw");
            tags.add("mineral");
            tags.add("smeltable");
        }

        // Ingots
        if (upper.endsWith("_INGOT") || upper.equals("NETHERITE_INGOT") || upper.equals("COPPER_INGOT")) {
            tags.add("ingot");
            tags.add("mineral");
            tags.add("metal");
            tags.add("refined");
        }

        // Nuggets
        if (upper.endsWith("_NUGGET")) {
            tags.add("nugget");
            tags.add("mineral");
            tags.add("metal");
        }

        // Gems
        if (upper.equals("DIAMOND") || upper.equals("EMERALD") || upper.equals("AMETHYST_SHARD")
                || upper.equals("LAPIS_LAZULI") || upper.equals("PRISMARINE_SHARD")
                || upper.equals("PRISMARINE_CRYSTALS") || upper.equals("QUARTZ")
                || upper.equals("NETHER_QUARTZ")) {
            tags.add("gem");
            tags.add("mineral");
            tags.add("precious");
        }

        // Specific minerals
        if (upper.contains("DIAMOND")) {
            tags.add("diamond");
            tags.add("mineral");
        }
        if (upper.contains("EMERALD")) {
            tags.add("emerald");
            tags.add("mineral");
        }
        if (upper.contains("GOLD") || upper.contains("GOLDEN")) {
            tags.add("gold");
            tags.add("mineral");
        }
        if (upper.contains("IRON")) {
            tags.add("iron");
            tags.add("mineral");
        }
        if (upper.contains("COPPER")) {
            tags.add("copper");
            tags.add("mineral");
        }
        if (upper.contains("NETHERITE")) {
            tags.add("netherite");
            tags.add("mineral");
        }
        if (upper.contains("LAPIS")) {
            tags.add("lapis");
            tags.add("mineral");
        }
        if (upper.contains("REDSTONE")) {
            tags.add("redstone");
            tags.add("mineral");
        }
        if (upper.contains("QUARTZ")) {
            tags.add("quartz");
            tags.add("mineral");
        }
        if (upper.contains("AMETHYST")) {
            tags.add("amethyst");
            tags.add("mineral");
        }
        if (upper.contains("COAL") && !upper.contains("CHARCOAL")) {
            tags.add("coal");
            tags.add("mineral");
        }

        // Blocks of minerals
        if (upper.endsWith("_BLOCK") && (upper.contains("DIAMOND") || upper.contains("EMERALD")
                || upper.contains("GOLD") || upper.contains("IRON") || upper.contains("COPPER")
                || upper.contains("NETHERITE") || upper.contains("LAPIS") || upper.contains("REDSTONE")
                || upper.contains("COAL") || upper.contains("AMETHYST") || upper.contains("QUARTZ")
                || upper.contains("RAW_"))) {
            tags.add("mineralblock");
            tags.add("storage");
        }
    }
}
