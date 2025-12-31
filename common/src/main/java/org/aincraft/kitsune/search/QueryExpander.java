package org.aincraft.kitsune.search;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class QueryExpander {

    // Material expansions - map broad terms to specific items
    private static final Map<String, List<String>> MATERIAL_EXPANSIONS = new HashMap<>() {{
        put("diamond", List.of("diamond pickaxe", "diamond sword", "diamond axe", "diamond shovel",
                               "diamond hoe", "diamond helmet", "diamond chestplate", "diamond leggings",
                               "diamond boots", "diamond ore", "diamond block", "deepslate diamond ore"));
        put("iron", List.of("iron pickaxe", "iron sword", "iron axe", "iron shovel", "iron hoe",
                           "iron helmet", "iron chestplate", "iron leggings", "iron boots",
                           "iron ore", "iron ingot", "iron block", "raw iron", "deepslate iron ore"));
        put("gold", List.of("gold pickaxe", "gold sword", "gold axe", "gold shovel", "gold hoe",
                           "gold helmet", "gold chestplate", "gold leggings", "gold boots",
                           "gold ore", "gold ingot", "gold block", "raw gold", "deepslate gold ore"));
        put("netherite", List.of("netherite pickaxe", "netherite sword", "netherite axe", "netherite shovel",
                                "netherite hoe", "netherite helmet", "netherite chestplate", "netherite leggings",
                                "netherite boots", "netherite ingot", "netherite scrap"));
        put("stone", List.of("stone pickaxe", "stone sword", "stone axe", "stone shovel", "stone hoe",
                            "cobblestone", "stone", "andesite", "diorite", "granite"));
        put("wood", List.of("wooden pickaxe", "wooden sword", "wooden axe", "wooden shovel", "wooden hoe",
                           "oak log", "birch log", "spruce log", "jungle log", "acacia log", "dark oak log",
                           "oak planks", "birch planks", "spruce planks"));
    }};

    // Category expansions - map categories to item types
    private static final Map<String, List<String>> CATEGORY_EXPANSIONS = new HashMap<>() {{
        put("tools", List.of("pickaxe", "axe", "shovel", "hoe", "sword"));
        put("armor", List.of("helmet", "chestplate", "leggings", "boots"));
        put("weapons", List.of("sword", "bow", "crossbow", "trident", "axe"));
        put("food", List.of("bread", "cooked beef", "cooked porkchop", "apple", "golden apple",
                           "carrot", "potato", "baked potato"));
        put("ores", List.of("iron ore", "gold ore", "diamond ore", "emerald ore", "coal ore",
                           "copper ore", "lapis ore", "redstone ore"));
        put("blocks", List.of("stone", "dirt", "cobblestone", "planks", "log", "wool"));
        put("redstone", List.of("redstone", "repeater", "comparator", "piston", "observer",
                               "hopper", "dropper", "dispenser"));
    }};

    // Synonyms - alternative names for same items
    private static final Map<String, List<String>> SYNONYMS = new HashMap<>() {{
        put("pick", List.of("pickaxe"));
        put("sword", List.of("blade"));
        put("helmet", List.of("cap", "hat"));
        put("chestplate", List.of("tunic", "chest armor"));
        put("leggings", List.of("pants", "leg armor"));
        put("boots", List.of("shoes"));
    }};

    /**
     * Expand a user query with related terms, variations, and synonyms.
     * Returns the original query plus expanded terms.
     */
    public static String expand(String query) {
        Set<String> expandedTerms = new LinkedHashSet<>();
        String queryLower = query.toLowerCase().trim();

        // Always include original query
        expandedTerms.add(queryLower);

        // DON'T expand if query is already specific (contains multiple words or specific item names)
        if (queryLower.split("\\s+").length > 1 || isSpecificItem(queryLower)) {
            return queryLower; // Return original without expansion
        }

        // Handle plurals - "diamonds" -> "diamond"
        String singular = singularize(queryLower);
        if (!singular.equals(queryLower)) {
            expandedTerms.add(singular);
        }

        // Split query into tokens
        String[] tokens = queryLower.split("\\s+");

        for (String token : tokens) {
            // Material expansions
            if (MATERIAL_EXPANSIONS.containsKey(token)) {
                expandedTerms.addAll(MATERIAL_EXPANSIONS.get(token));
            }

            // Category expansions
            if (CATEGORY_EXPANSIONS.containsKey(token)) {
                expandedTerms.addAll(CATEGORY_EXPANSIONS.get(token));
            }

            // Synonyms
            if (SYNONYMS.containsKey(token)) {
                expandedTerms.addAll(SYNONYMS.get(token));
            }
        }

        // Join all expanded terms
        return String.join(" ", expandedTerms);
    }

    /**
     * Simple singularization - remove trailing 's' for common cases.
     */
    private static String singularize(String word) {
        if (word.endsWith("ies")) {
            return word.substring(0, word.length() - 3) + "y"; // berries -> berry
        }
        if (word.endsWith("es")) {
            return word.substring(0, word.length() - 2); // axes -> axe
        }
        if (word.endsWith("s") && !word.endsWith("ss")) {
            return word.substring(0, word.length() - 1); // diamonds -> diamond
        }
        return word;
    }

    /**
     * Check if query is a specific item that shouldn't be expanded.
     */
    private static boolean isSpecificItem(String query) {
        // Specific suffixes that indicate precise items
        return query.contains("pickaxe") || query.contains("sword") || query.contains("axe") ||
               query.contains("shovel") || query.contains("hoe") || query.contains("helmet") ||
               query.contains("chestplate") || query.contains("leggings") || query.contains("boots") ||
               query.contains("ingot") || query.contains("ore") || query.contains("block") ||
               query.contains("planks") || query.contains("log") || query.contains("book");
    }
}
