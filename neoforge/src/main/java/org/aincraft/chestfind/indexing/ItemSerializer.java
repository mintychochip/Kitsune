package org.aincraft.chestfind.indexing;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.List;
import java.util.ArrayList;

/**
 * Static utility for serializing NeoForge ItemStacks into indexed chunks.
 * Uses NeoForge 1.20.6+ DataComponents API for item property extraction.
 */
public class ItemSerializer {
    private static final Gson gson = new Gson();

    private ItemSerializer() {
    }

    private static String formatItemName(String rawName) {
        // Convert minecraft:iron_sword -> Iron Sword for better embedding quality
        String[] words = rawName.toLowerCase().split("[_:]");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty() || word.equals("minecraft")) continue;
            if (result.length() > 0) result.append(" ");
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1));
            }
        }
        return result.toString();
    }

    /**
     * Serialize items into chunks - one item per chunk.
     * @param items Array of items to serialize
     * @return List of SerializedItem with embeddingText and storageJson
     */
    public static List<SerializedItem> serializeItemsToChunks(ItemStack[] items) {
        List<SerializedItem> chunks = new ArrayList<>();

        for (ItemStack item : items) {
            if (item == null || item.isEmpty()) {
                continue;
            }

            JsonObject itemJson = createItemJson(item);
            String embeddingText = createEmbeddingText(item, itemJson);

            JsonArray singleItemArray = new JsonArray();
            singleItemArray.add(itemJson);
            String storageJson = gson.toJson(singleItemArray);

            chunks.add(new SerializedItem(embeddingText, storageJson));
        }

        return chunks;
    }

    /**
     * Create embedding text with semantic tags.
     * Format: "iron sword #weapon #iron #sword #combat #enchanted #sharpness"
     */
    private static String createEmbeddingText(ItemStack item, JsonObject itemJson) {
        StringBuilder sb = new StringBuilder();

        // Item name
        String itemName = getItemId(item);
        sb.append(formatItemName(itemName));

        // Category tag
        if (itemJson.has("category")) {
            sb.append(" #").append(itemJson.get("category").getAsString().replace(" ", ""));
        }

        // Tool material
        if (itemJson.has("tool_material")) {
            sb.append(" #").append(itemJson.get("tool_material").getAsString());
        }

        // Tool type
        if (itemJson.has("tool_type")) {
            sb.append(" #").append(itemJson.get("tool_type").getAsString());
        }

        // Add component-based tags
        addComponentTags(sb, item, itemJson);

        // Add semantic tags
        addSemanticTags(sb, itemName, itemJson);

        return sb.toString();
    }

    private static String getItemId(ItemStack item) {
        var key = item.getItem().builtInRegistryHolder().key();
        return key != null ? key.location().toString() : item.getItem().toString();
    }

    /**
     * Add tags from DataComponents.
     */
    private static void addComponentTags(StringBuilder sb, ItemStack item, JsonObject itemJson) {
        // Enchantments
        if (item.has(DataComponents.ENCHANTMENTS)) {
            ItemEnchantments enchantments = item.get(DataComponents.ENCHANTMENTS);
            if (enchantments != null && !enchantments.isEmpty()) {
                sb.append(" #enchanted #magical");
                enchantments.entrySet().forEach(entry -> {
                    var key = entry.getKey().unwrapKey();
                    if (key.isPresent()) {
                        String enchName = key.get().location().getPath();
                        sb.append(" #").append(enchName.replace("_", ""));
                    }
                });
            }
        }

        // Stored enchantments (enchanted books)
        if (item.has(DataComponents.STORED_ENCHANTMENTS)) {
            ItemEnchantments stored = item.get(DataComponents.STORED_ENCHANTMENTS);
            if (stored != null && !stored.isEmpty()) {
                sb.append(" #enchantedbook #magical");
                stored.entrySet().forEach(entry -> {
                    var key = entry.getKey().unwrapKey();
                    if (key.isPresent()) {
                        String enchName = key.get().location().getPath();
                        sb.append(" #").append(enchName.replace("_", ""));
                    }
                });
            }
        }

        // Food
        if (item.has(DataComponents.FOOD)) {
            sb.append(" #edible #food");
        }

        // Tool
        if (item.has(DataComponents.TOOL)) {
            sb.append(" #tool");
        }

        // Damage/durability
        if (item.has(DataComponents.DAMAGE) && item.has(DataComponents.MAX_DAMAGE)) {
            Integer damage = item.get(DataComponents.DAMAGE);
            Integer maxDamage = item.get(DataComponents.MAX_DAMAGE);
            if (damage != null && maxDamage != null && maxDamage > 0) {
                if (damage > 0) {
                    sb.append(" #damaged");
                }
                double percent = (double)(maxDamage - damage) / maxDamage;
                if (percent >= 0.75) sb.append(" #excellent");
                else if (percent >= 0.5) sb.append(" #good");
                else if (percent >= 0.25) sb.append(" #fair");
                else sb.append(" #poor");
            }
        }

        // Unbreakable
        if (item.has(DataComponents.UNBREAKABLE)) {
            sb.append(" #unbreakable");
        }

        // Custom name
        if (item.has(DataComponents.CUSTOM_NAME)) {
            sb.append(" #customnamed");
        }

        // Lore
        if (item.has(DataComponents.LORE)) {
            sb.append(" #haslore");
        }

        // Potion
        if (item.has(DataComponents.POTION_CONTENTS)) {
            sb.append(" #potion");
        }
    }

    /**
     * Add semantic tags based on item name patterns.
     */
    private static void addSemanticTags(StringBuilder sb, String itemName, JsonObject itemJson) {
        String name = itemName.toLowerCase();

        // Weapon/combat
        if (name.contains("sword") || name.contains("axe") || name.contains("mace")) {
            sb.append(" #weapon #combat");
        }

        // Tools
        if (name.contains("pickaxe") || name.contains("shovel") || name.contains("hoe")) {
            sb.append(" #tool");
        }

        // Armor
        if (name.contains("helmet") || name.contains("chestplate") ||
            name.contains("leggings") || name.contains("boots")) {
            sb.append(" #armor #defense");
        }

        // Ranged
        if (name.contains("bow") || name.contains("crossbow") || name.contains("trident")) {
            sb.append(" #ranged #weapon");
        }

        // Materials
        if (name.contains("diamond")) sb.append(" #diamond");
        else if (name.contains("iron")) sb.append(" #iron");
        else if (name.contains("stone")) sb.append(" #stone");
        else if (name.contains("wood")) sb.append(" #wood");
        else if (name.contains("gold")) sb.append(" #gold");
        else if (name.contains("netherite")) sb.append(" #netherite");

        // Blocks
        if (name.contains("block")) sb.append(" #block");

        // Ores/materials
        if (name.contains("ore") || name.contains("raw_") || name.contains("ingot") ||
            name.contains("nugget") || name.contains("dust")) {
            sb.append(" #rawmaterial #ore");
        }

        // Magical
        if (name.contains("enchant") || name.contains("crystal") || name.contains("amethyst")) {
            sb.append(" #magical");
        }

        // Storage
        if (name.contains("chest") || name.contains("barrel") || name.contains("shulker")) {
            sb.append(" #storage #container");
        }

        // Fuel
        if (name.contains("coal") || name.contains("charcoal")) {
            sb.append(" #fuel");
        }
    }

    private static JsonObject createItemJson(ItemStack item) {
        JsonObject itemObj = new JsonObject();

        String itemName = getItemId(item);
        itemObj.addProperty("name", formatItemName(itemName));
        itemObj.addProperty("amount", item.getCount());

        // Extract component data
        extractComponentData(item, itemObj);

        // Add categorical metadata
        addCategoricalMetadata(itemObj, itemName);

        return itemObj;
    }

    private static void extractComponentData(ItemStack item, JsonObject itemObj) {
        // Enchantments
        if (item.has(DataComponents.ENCHANTMENTS)) {
            ItemEnchantments enchantments = item.get(DataComponents.ENCHANTMENTS);
            if (enchantments != null && !enchantments.isEmpty()) {
                JsonArray enchArray = new JsonArray();
                enchantments.entrySet().forEach(entry -> {
                    var key = entry.getKey().unwrapKey();
                    if (key.isPresent()) {
                        JsonObject ench = new JsonObject();
                        ench.addProperty("enchantment", key.get().location().toString());
                        ench.addProperty("level", entry.getIntValue());
                        enchArray.add(ench);
                    }
                });
                if (enchArray.size() > 0) {
                    itemObj.add("enchantments_array", enchArray);
                }
            }
        }

        // Stored enchantments
        if (item.has(DataComponents.STORED_ENCHANTMENTS)) {
            ItemEnchantments stored = item.get(DataComponents.STORED_ENCHANTMENTS);
            if (stored != null && !stored.isEmpty()) {
                JsonArray storedArray = new JsonArray();
                stored.entrySet().forEach(entry -> {
                    var key = entry.getKey().unwrapKey();
                    if (key.isPresent()) {
                        JsonObject ench = new JsonObject();
                        ench.addProperty("enchantment", key.get().location().toString());
                        ench.addProperty("level", entry.getIntValue());
                        storedArray.add(ench);
                    }
                });
                if (storedArray.size() > 0) {
                    itemObj.add("stored_enchantments_array", storedArray);
                }
            }
        }

        // Custom name
        if (item.has(DataComponents.CUSTOM_NAME)) {
            Component customName = item.get(DataComponents.CUSTOM_NAME);
            if (customName != null) {
                itemObj.addProperty("custom_name", customName.getString());
            }
        }

        // Lore
        if (item.has(DataComponents.LORE)) {
            ItemLore lore = item.get(DataComponents.LORE);
            if (lore != null && !lore.lines().isEmpty()) {
                JsonArray loreArray = new JsonArray();
                for (Component line : lore.lines()) {
                    loreArray.add(line.getString());
                }
                itemObj.add("lore", loreArray);
            }
        }

        // Unbreakable
        if (item.has(DataComponents.UNBREAKABLE)) {
            itemObj.addProperty("unbreakable", true);
        }

        // Damage
        if (item.has(DataComponents.DAMAGE) && item.has(DataComponents.MAX_DAMAGE)) {
            Integer damage = item.get(DataComponents.DAMAGE);
            Integer maxDamage = item.get(DataComponents.MAX_DAMAGE);
            if (damage != null && maxDamage != null && maxDamage > 0) {
                JsonObject durabilityObj = new JsonObject();
                durabilityObj.addProperty("current", damage);
                durabilityObj.addProperty("max", maxDamage);
                durabilityObj.addProperty("percent", (int)((1.0 - (double)damage/maxDamage) * 100));
                itemObj.add("durability_info", durabilityObj);
            }
        }
    }

    private static void addCategoricalMetadata(JsonObject itemObj, String itemName) {
        String name = itemName.toLowerCase();

        String category = categorizeItem(name);
        if (category != null) {
            itemObj.addProperty("category", category);
        }

        String toolType = getToolType(name);
        if (toolType != null) {
            itemObj.addProperty("tool_type", toolType);
        }

        String material = getMaterial(name);
        if (material != null) {
            itemObj.addProperty("tool_material", material);
        }
    }

    private static String categorizeItem(String name) {
        if (name.contains("helmet") || name.contains("chestplate") ||
            name.contains("leggings") || name.contains("boots")) return "armor";
        if (name.contains("sword") || name.contains("axe") || name.contains("mace") ||
            name.contains("trident") || name.contains("bow") || name.contains("crossbow")) return "weapon";
        if (name.contains("pickaxe") || name.contains("shovel") || name.contains("hoe")) return "tool";
        if (name.contains("ore") || name.contains("raw_") || name.contains("ingot") ||
            name.contains("nugget") || name.contains("dust")) return "rawmaterial";
        if (name.contains("block")) return "block";
        if (name.contains("potion") || name.contains("bottle")) return "potion";
        if (name.contains("enchant") || name.contains("crystal")) return "magical";
        return null;
    }

    private static String getToolType(String name) {
        if (name.contains("pickaxe")) return "pickaxe";
        if (name.contains("shovel")) return "shovel";
        if (name.contains("axe")) return "axe";
        if (name.contains("hoe")) return "hoe";
        if (name.contains("sword")) return "sword";
        if (name.contains("bow")) return "bow";
        if (name.contains("crossbow")) return "crossbow";
        if (name.contains("trident")) return "trident";
        return null;
    }

    private static String getMaterial(String name) {
        if (name.contains("diamond")) return "diamond";
        if (name.contains("iron")) return "iron";
        if (name.contains("stone")) return "stone";
        if (name.contains("wood")) return "wood";
        if (name.contains("gold")) return "gold";
        if (name.contains("netherite")) return "netherite";
        return null;
    }
}
