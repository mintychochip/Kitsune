package org.aincraft.kitsune.indexing;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemRarity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemSerializer {
    private static final Gson gson = new Gson();

    private ItemSerializer() {
    }

    private static String formatItemName(String rawName) {
        // Convert BIRCH_PLANKS -> Birch Planks for better embedding quality
        String[] words = rawName.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
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
     * Returns both plain text (for embedding) and JSON (for storage).
     * Also extracts nested items from bundles and shulker boxes.
     * @param items Array of items to serialize
     * @return List of SerializedItem with embeddingText and storageJson
     */
    public static List<SerializedItem> serializeItemsToChunks(ItemStack[] items) {
        List<SerializedItem> chunks = new ArrayList<>();
        serializeItemsRecursive(items, chunks, new JsonArray());
        return chunks;
    }

    /**
     * Recursively serialize items, including nested container contents.
     * @param items Items to serialize
     * @param chunks Output list
     * @param containerPath JSON array tracking the nesting path
     */
    private static void serializeItemsRecursive(ItemStack[] items, List<SerializedItem> chunks, JsonArray containerPath) {
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }

            JsonObject itemJson = createItemJson(item);
            String embeddingText = createEmbeddingText(item, itemJson);

            // Add container path to metadata if nested
            if (containerPath.size() > 0) {
                itemJson.add("container_path", containerPath.deepCopy());
            }

            // JSON for storage
            JsonArray singleItemArray = new JsonArray();
            singleItemArray.add(itemJson);
            String storageJson = gson.toJson(singleItemArray);

            chunks.add(new SerializedItem(embeddingText, storageJson));

            // Extract nested items from bundles
            if (item.hasData(DataComponentTypes.BUNDLE_CONTENTS)) {
                var bundleContents = item.getData(DataComponentTypes.BUNDLE_CONTENTS);
                if (bundleContents != null) {
                    List<ItemStack> nestedItems = bundleContents.contents();
                    if (!nestedItems.isEmpty()) {
                        JsonArray newPath = containerPath.deepCopy();
                        newPath.add(buildContainerPathEntry(item, "bundle"));
                        serializeItemsRecursive(nestedItems.toArray(new ItemStack[0]), chunks, newPath);
                    }
                }
            }

            // Extract nested items from shulker boxes / containers
            if (item.hasData(DataComponentTypes.CONTAINER)) {
                var containerContents = item.getData(DataComponentTypes.CONTAINER);
                if (containerContents != null) {
                    List<ItemStack> nestedItems = containerContents.contents();
                    if (!nestedItems.isEmpty()) {
                        JsonArray newPath = containerPath.deepCopy();
                        newPath.add(buildContainerPathEntry(item, "shulker_box"));
                        serializeItemsRecursive(nestedItems.toArray(new ItemStack[0]), chunks, newPath);
                    }
                }
            }
        }
    }

    /**
     * Build a container path entry for nested item tracking.
     */
    private static JsonObject buildContainerPathEntry(ItemStack container, String containerType) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", containerType);

        // Add color for shulkers
        String materialName = container.getType().name();
        if (materialName.contains("SHULKER_BOX") && !materialName.equals("SHULKER_BOX")) {
            String color = materialName.replace("_SHULKER_BOX", "").toLowerCase();
            entry.addProperty("color", color);
        }

        // Add custom name if present
        if (container.hasData(DataComponentTypes.CUSTOM_NAME)) {
            Component customName = container.getData(DataComponentTypes.CUSTOM_NAME);
            if (customName != null) {
                String name = PlainTextComponentSerializer.plainText().serialize(customName);
                entry.addProperty("name", name);
            }
        }

        return entry;
    }

    /**
     * Create simplified plain text for embedding.
     * Format: "iron sword #weapon #iron #sword #combat #enchanted #sharpness"
     * Tags help with semantic matching and filtering.
     */
    private static String createEmbeddingText(ItemStack item, JsonObject itemJson) {
        StringBuilder sb = new StringBuilder();

        // Item name
        sb.append(formatItemName(item.getType().name()));

        // Custom category (e.g., "weapon", "tool", "food", "block")
        if (itemJson.has("category")) {
            sb.append(" #").append(itemJson.get("category").getAsString().replace(" ", ""));
        }

        // Tool material (e.g., "diamond", "iron", "netherite")
        if (itemJson.has("tool_material")) {
            sb.append(" #").append(itemJson.get("tool_material").getAsString());
        }

        // Tool type (e.g., "sword", "pickaxe", "axe")
        if (itemJson.has("tool_type")) {
            sb.append(" #").append(itemJson.get("tool_type").getAsString());
        }

        // Add DataComponent tags (NEW)
        addDataComponentTags(sb, itemJson);

        // Add semantic tags based on item name and properties
        addSemanticTags(sb, item.getType().name(), itemJson);

        return sb.toString();
    }

    /**
     * Add tags from DataComponents extracted into itemJson.
     */
    private static void addDataComponentTags(StringBuilder sb, JsonObject itemJson) {
        // Enchantments (from enchantments_array)
        if (itemJson.has("enchantments_array")) {
            sb.append(" #enchanted #magical");
            JsonArray enchants = itemJson.getAsJsonArray("enchantments_array");
            for (int i = 0; i < enchants.size(); i++) {
                JsonObject ench = enchants.get(i).getAsJsonObject();
                sb.append(" #").append(ench.get("enchantment").getAsString().replace("_", ""));
                sb.append(" #level").append(ench.get("level").getAsInt());
            }
        }

        // Stored enchantments (enchanted books)
        if (itemJson.has("stored_enchantments_array")) {
            sb.append(" #enchantedbook #magical");
            JsonArray stored = itemJson.getAsJsonArray("stored_enchantments_array");
            for (int i = 0; i < stored.size(); i++) {
                JsonObject ench = stored.get(i).getAsJsonObject();
                sb.append(" #").append(ench.get("enchantment").getAsString().replace("_", ""));
                sb.append(" #level").append(ench.get("level").getAsInt());
            }
        }

        // Food properties
        if (itemJson.has("food_properties")) {
            JsonObject food = itemJson.getAsJsonObject("food_properties");
            sb.append(" #").append(food.get("category").getAsString().replace("_", "")).append("food");
            if (food.has("can_always_eat") && food.get("can_always_eat").getAsBoolean()) {
                sb.append(" #alwaysedible");
            }
        }

        // Tool properties
        if (itemJson.has("tool_properties")) {
            JsonObject tool = itemJson.getAsJsonObject("tool_properties");
            sb.append(" #").append(tool.get("speed_category").getAsString().replace("_", "")).append("mining");
        }

        // Durability
        if (itemJson.has("durability_info")) {
            JsonObject durability = itemJson.getAsJsonObject("durability_info");
            sb.append(" #").append(durability.get("category").getAsString().replace("_", "")).append("condition");
            if (durability.get("current").getAsInt() > 0) {
                sb.append(" #damaged");
            }
        }

        // Rarity
        if (itemJson.has("rarity")) {
            sb.append(" #").append(itemJson.get("rarity").getAsString());
        }

        // Modifiers
        if (itemJson.has("unbreakable")) {
            sb.append(" #unbreakable");
        }

        if (itemJson.has("stack_category")) {
            sb.append(" #").append(itemJson.get("stack_category").getAsString());
        }

        // Custom name
        if (itemJson.has("custom_name")) {
            sb.append(" #customnamed");
        }

        // Lore
        if (itemJson.has("lore")) {
            sb.append(" #haslore");
        }

        // Appearance
        if (itemJson.has("dyed_color")) {
            sb.append(" #dyed");
        }

        if (itemJson.has("trim")) {
            sb.append(" #trimmed");
        }

        // Specialized components
        if (itemJson.has("potion_contents")) {
            sb.append(" #potion");
        }

        if (itemJson.has("bundle_contents")) {
            sb.append(" #bundle");
        }

        if (itemJson.has("charged_projectiles")) {
            sb.append(" #loaded");
        }

        if (itemJson.has("container")) {
            sb.append(" #shulker");
        }

        if (itemJson.has("writable_book")) {
            sb.append(" #book");
        }

        if (itemJson.has("written_book")) {
            sb.append(" #signedbook");
        }
    }

    /**
     * Add semantic tags based on Paper 1.21 item components and properties.
     */
    private static void addSemanticTags(StringBuilder sb, String materialName, JsonObject itemJson) {
        // Add properties from the JSON (Paper API properties)
        if (itemJson.has("properties")) {
            JsonObject props = itemJson.getAsJsonObject("properties");

            // Edible component (food items) - only add #edible since #food is from category
            if (props.has("edible") && props.get("edible").getAsBoolean()) {
                sb.append(" #edible");
            }

            // Fuel component (burnable items)
            if (props.has("fuel") && props.get("fuel").getAsBoolean()) {
                sb.append(" #fuel");
            }

            // Flammable component
            if (props.has("flammable") && props.get("flammable").getAsBoolean()) {
                sb.append(" #flammable");
            }

            // Gravity component (falling blocks)
            if (props.has("gravity") && props.get("gravity").getAsBoolean()) {
                sb.append(" #gravity #falling");
            }

            // Record component (music discs)
            if (props.has("record") && props.get("record").getAsBoolean()) {
                sb.append(" #music #record");
            }

            // Liquid component
            if (props.has("liquid") && props.get("liquid").getAsBoolean()) {
                sb.append(" #liquid");
            }
        }

        // Material type
        if (itemJson.has("material_type")) {
            String matType = itemJson.get("material_type").getAsString();
            sb.append(" #").append(matType); // #block or #item
        }
    }

    private static JsonObject createItemJson(ItemStack item) {
        JsonObject itemObj = new JsonObject();
        Material material = item.getType();

        // Add formatted item name and amount
        itemObj.addProperty("name", formatItemName(material.name()));
        itemObj.addProperty("amount", item.getAmount());

        // Extract ALL data components using Paper 1.21.4+ API
        extractDataComponents(item, itemObj);

        // Add categorical metadata for distinct embeddings
        addCategoricalMetadata(itemObj, material);

        return itemObj;
    }

    /**
     * Add categorical metadata to distinguish items for better embeddings.
     */
    private static void addCategoricalMetadata(JsonObject itemObj, Material material) {
        String materialName = material.name();

        // Official creative category (what shows in tooltips)
        var creativeCategory = material.getCreativeCategory();
        if (creativeCategory != null) {
            itemObj.addProperty("creative_category", creativeCategory.name().toLowerCase().replace("_", " "));
        }

        // Custom category for additional context
        String itemCategory = categorizeItemType(materialName, material);
        if (itemCategory != null) {
            itemObj.addProperty("category", itemCategory);
        }

        // Material properties for embedding distinction
        addMaterialProperties(itemObj, material);

        // Tool type specification if applicable
        addToolTypeMetadata(itemObj, materialName);

        // Block vs Item distinction
        if (material.isBlock()) {
            itemObj.addProperty("material_type", "block");
        } else {
            itemObj.addProperty("material_type", "item");
        }

        // Functional properties for better categorization
        addFunctionalProperties(itemObj, material);
    }

    /**
     * Categorize items into primary types for embedding distinction.
     */
    private static String categorizeItemType(String materialName, Material material) {
        if (materialName.contains("HELMET") || materialName.contains("CHESTPLATE") ||
            materialName.contains("LEGGINGS") || materialName.contains("BOOTS")) {
            return "armor";
        }

        if (materialName.contains("SWORD") || materialName.contains("AXE") ||
            materialName.contains("TRIDENT") || materialName.contains("BOW") ||
            materialName.contains("CROSSBOW")) {
            return "weapon";
        }

        if (materialName.contains("PICKAXE") || materialName.contains("SHOVEL") ||
            materialName.contains("HOE") || materialName.contains("SPADE")) {
            return "tool";
        }

        if (material.isEdible()) {
            return "food";
        }

        if (material.isBlock() && !material.isAir() && !materialName.contains("ORE")) {
            return "block";
        }

        if (materialName.contains("ORE") || materialName.contains("RAW_") ||
            materialName.contains("INGOT") || materialName.contains("NUGGET") ||
            materialName.contains("DUST") || materialName.contains("POWDER")) {
            return "rawmaterial";
        }

        if (materialName.contains("ENCHANT") || materialName.contains("CRYSTAL") ||
            materialName.contains("SHARD") || materialName.contains("ESSENCE")) {
            return "magical";
        }

        if (materialName.contains("STAINED") || materialName.contains("DYED") ||
            materialName.contains("CONCRETE") || materialName.contains("WOOL")) {
            return "decorative";
        }

        if (materialName.contains("NOTE_BLOCK") || materialName.contains("JUKEBOX") ||
            materialName.contains("TORCH") || materialName.contains("LANTERN") ||
            materialName.contains("LIGHT")) {
            return "lightmusic";
        }

        return null;
    }

    private static void addMaterialProperties(JsonObject itemObj, Material material) {
        JsonObject properties = new JsonObject();
        boolean hasProperties = false;

        if (material.isFlammable()) {
            properties.addProperty("flammable", true);
            hasProperties = true;
        }

        if (material.hasGravity()) {
            properties.addProperty("gravity", true);
            hasProperties = true;
        }

        if (material.isEdible()) {
            properties.addProperty("edible", true);
            hasProperties = true;
        }

        if (material.isRecord()) {
            properties.addProperty("record", true);
            hasProperties = true;
        }

        if (material.isFuel()) {
            properties.addProperty("fuel", true);
            hasProperties = true;
        }

        if (material.name().contains("WATER") || material.name().contains("LAVA")) {
            properties.addProperty("liquid", true);
            hasProperties = true;
        }

        if (hasProperties) {
            itemObj.add("properties", properties);
        }
    }

    private static void addToolTypeMetadata(JsonObject itemObj, String materialName) {
        String toolType = null;

        if (materialName.contains("PICKAXE")) {
            toolType = "pickaxe";
        } else if (materialName.contains("SHOVEL") || materialName.contains("SPADE")) {
            toolType = "shovel";
        } else if (materialName.contains("AXE")) {
            toolType = "axe";
        } else if (materialName.contains("HOE")) {
            toolType = "hoe";
        } else if (materialName.contains("SWORD")) {
            toolType = "sword";
        } else if (materialName.contains("BOW")) {
            toolType = "bow";
        } else if (materialName.contains("CROSSBOW")) {
            toolType = "crossbow";
        } else if (materialName.contains("TRIDENT")) {
            toolType = "trident";
        }

        if (toolType != null) {
            itemObj.addProperty("tool_type", toolType);
        }

        if (materialName.contains("DIAMOND")) {
            itemObj.addProperty("tool_material", "diamond");
        } else if (materialName.contains("IRON")) {
            itemObj.addProperty("tool_material", "iron");
        } else if (materialName.contains("STONE")) {
            itemObj.addProperty("tool_material", "stone");
        } else if (materialName.contains("WOODEN") || materialName.contains("WOOD")) {
            itemObj.addProperty("tool_material", "wood");
        } else if (materialName.contains("GOLDEN") || materialName.contains("GOLD")) {
            itemObj.addProperty("tool_material", "gold");
        } else if (materialName.contains("NETHERITE")) {
            itemObj.addProperty("tool_material", "netherite");
        }
    }

    private static void addFunctionalProperties(JsonObject itemObj, Material material) {
        String materialName = material.name();

        if (materialName.contains("REDSTONE") || materialName.contains("COMPARATOR") ||
            materialName.contains("REPEATER") || materialName.contains("BUTTON") ||
            materialName.contains("LEVER")) {
            itemObj.addProperty("function", "redstone");
        }

        if (materialName.contains("POTION") || materialName.contains("BOTTLE") ||
            materialName.contains("CAULDRON") || materialName.contains("BREWING") ||
            materialName.contains("DRAGON_BREATH")) {
            itemObj.addProperty("function", "brewing");
        }

        if (materialName.contains("TNT") || materialName.contains("CREEPER") ||
            materialName.contains("POWDER")) {
            itemObj.addProperty("function", "explosive");
        }

        if (materialName.contains("BUCKET") || materialName.contains("MINECART") ||
            materialName.contains("BOAT") || materialName.contains("SADDLE")) {
            itemObj.addProperty("function", "utility");
        }

        if (materialName.contains("CRAFTING") || materialName.contains("FURNACE") ||
            materialName.contains("SMOKER") || materialName.contains("BLAST") ||
            materialName.contains("CAULDRON") || materialName.contains("ANVIL")) {
            itemObj.addProperty("function", "crafting");
        }

        if (materialName.contains("MINECART") || materialName.contains("BOAT") ||
            materialName.contains("ELYTRA")) {
            itemObj.addProperty("function", "transport");
        }

        if (materialName.contains("SHIELD") || materialName.contains("HELMET") ||
            materialName.contains("CHESTPLATE")) {
            itemObj.addProperty("function", "defense");
        }
    }

    // ==================== DATA COMPONENT EXTRACTORS ====================

    private static void extractDataComponents(ItemStack item, JsonObject itemObj) {
        extractEnchantments(item, itemObj);
        extractNames(item, itemObj);
        extractLore(item, itemObj);
        extractFoodProperties(item, itemObj);
        extractToolProperties(item, itemObj);
        extractDurability(item, itemObj);
        extractRarity(item, itemObj);
        extractModifiers(item, itemObj);
        extractAppearance(item, itemObj);
        extractSpecializedComponents(item, itemObj);
    }

    private static void extractEnchantments(ItemStack item, JsonObject itemObj) {
        if (item.hasData(DataComponentTypes.ENCHANTMENTS)) {
            ItemEnchantments enchants = item.getData(DataComponentTypes.ENCHANTMENTS);
            JsonArray enchantArray = new JsonArray();
            for (Map.Entry<Enchantment, Integer> entry : enchants.enchantments().entrySet()) {
                JsonObject ench = new JsonObject();
                ench.addProperty("enchantment", entry.getKey().getKey().getKey());
                ench.addProperty("level", entry.getValue());
                enchantArray.add(ench);
            }
            if (enchantArray.size() > 0) {
                itemObj.add("enchantments_array", enchantArray);
            }
        }

        if (item.hasData(DataComponentTypes.STORED_ENCHANTMENTS)) {
            ItemEnchantments stored = item.getData(DataComponentTypes.STORED_ENCHANTMENTS);
            JsonArray enchantArray = new JsonArray();
            for (Map.Entry<Enchantment, Integer> entry : stored.enchantments().entrySet()) {
                JsonObject ench = new JsonObject();
                ench.addProperty("enchantment", entry.getKey().getKey().getKey());
                ench.addProperty("level", entry.getValue());
                enchantArray.add(ench);
            }
            if (enchantArray.size() > 0) {
                itemObj.add("stored_enchantments_array", enchantArray);
            }
        }
    }

    private static void extractNames(ItemStack item, JsonObject itemObj) {
        MiniMessage miniMessage = MiniMessage.miniMessage();

        // Store material for display
        itemObj.addProperty("material", item.getType().name());

        if (item.hasData(DataComponentTypes.CUSTOM_NAME)) {
            Component customName = item.getData(DataComponentTypes.CUSTOM_NAME);
            String plainText = PlainTextComponentSerializer.plainText().serialize(customName);
            itemObj.addProperty("custom_name", plainText);
            // Store MiniMessage format for display
            itemObj.addProperty("display_name", miniMessage.serialize(customName));
        } else if (item.hasData(DataComponentTypes.ITEM_NAME)) {
            Component itemName = item.getData(DataComponentTypes.ITEM_NAME);
            String plainText = PlainTextComponentSerializer.plainText().serialize(itemName);
            itemObj.addProperty("item_name", plainText);
            // Store MiniMessage format for display
            itemObj.addProperty("display_name", miniMessage.serialize(itemName));
        } else {
            // Default to formatted material name
            itemObj.addProperty("display_name", formatItemName(item.getType().name()));
        }
    }

    private static void extractLore(ItemStack item, JsonObject itemObj) {
        if (item.hasData(DataComponentTypes.LORE)) {
            ItemLore lore = item.getData(DataComponentTypes.LORE);
            JsonArray loreArray = new JsonArray();
            for (Component line : lore.lines()) {
                loreArray.add(PlainTextComponentSerializer.plainText().serialize(line));
            }
            if (loreArray.size() > 0) {
                itemObj.add("lore", loreArray);
            }
        }
    }

    private static void extractFoodProperties(ItemStack item, JsonObject itemObj) {
        if (item.hasData(DataComponentTypes.FOOD)) {
            FoodProperties food = item.getData(DataComponentTypes.FOOD);
            JsonObject foodObj = new JsonObject();
            foodObj.addProperty("nutrition", food.nutrition());
            foodObj.addProperty("saturation", food.saturation());
            foodObj.addProperty("can_always_eat", food.canAlwaysEat());
            foodObj.addProperty("category", categorizeNutrition(food.nutrition()));
            itemObj.add("food_properties", foodObj);
        }
    }

    private static void extractToolProperties(ItemStack item, JsonObject itemObj) {
        if (item.hasData(DataComponentTypes.TOOL)) {
            Tool tool = item.getData(DataComponentTypes.TOOL);
            JsonObject toolObj = new JsonObject();
            toolObj.addProperty("default_mining_speed", tool.defaultMiningSpeed());
            toolObj.addProperty("damage_per_block", tool.damagePerBlock());
            toolObj.addProperty("speed_category", categorizeMiningSpeed(tool.defaultMiningSpeed()));
            itemObj.add("tool_properties", toolObj);
        }
    }

    private static void extractDurability(ItemStack item, JsonObject itemObj) {
        if (item.hasData(DataComponentTypes.MAX_DAMAGE)) {
            int maxDamage = item.getData(DataComponentTypes.MAX_DAMAGE);
            int damage = item.getDataOrDefault(DataComponentTypes.DAMAGE, 0);

            JsonObject durabilityObj = new JsonObject();
            durabilityObj.addProperty("current", damage);
            durabilityObj.addProperty("max", maxDamage);
            durabilityObj.addProperty("percent", (int)((1.0 - (double)damage/maxDamage) * 100));
            durabilityObj.addProperty("category", categorizeDurability(damage, maxDamage));
            itemObj.add("durability_info", durabilityObj);
        }
    }

    private static void extractRarity(ItemStack item, JsonObject itemObj) {
        if (item.hasData(DataComponentTypes.RARITY)) {
            ItemRarity rarity = item.getData(DataComponentTypes.RARITY);
            itemObj.addProperty("rarity", rarity.name().toLowerCase());
        }
    }

    private static void extractModifiers(ItemStack item, JsonObject itemObj) {
        if (item.hasData(DataComponentTypes.UNBREAKABLE)) {
            itemObj.addProperty("unbreakable", true);
        }

        if (item.hasData(DataComponentTypes.REPAIR_COST)) {
            int repairCost = item.getData(DataComponentTypes.REPAIR_COST);
            itemObj.addProperty("repair_cost", repairCost);
        }

        if (item.hasData(DataComponentTypes.MAX_STACK_SIZE)) {
            int maxStack = item.getData(DataComponentTypes.MAX_STACK_SIZE);
            itemObj.addProperty("max_stack_size", maxStack);
            itemObj.addProperty("stack_category", categorizeStackSize(maxStack));
        }
    }

    private static void extractAppearance(ItemStack item, JsonObject itemObj) {
        if (item.hasData(DataComponentTypes.DYED_COLOR)) {
            DyedItemColor dyedColor = item.getData(DataComponentTypes.DYED_COLOR);
            itemObj.addProperty("dyed_color", dyedColor.color().asRGB());
        }

        if (item.hasData(DataComponentTypes.TRIM)) {
            itemObj.addProperty("trim", true);
        }
    }

    private static void extractSpecializedComponents(ItemStack item, JsonObject itemObj) {
        if (item.hasData(DataComponentTypes.POTION_CONTENTS)) {
            itemObj.addProperty("potion_contents", true);
        }

        if (item.hasData(DataComponentTypes.BUNDLE_CONTENTS)) {
            itemObj.addProperty("bundle_contents", true);
        }

        if (item.hasData(DataComponentTypes.CHARGED_PROJECTILES)) {
            itemObj.addProperty("charged_projectiles", true);
        }

        if (item.hasData(DataComponentTypes.CONTAINER)) {
            itemObj.addProperty("container", true);
        }

        if (item.hasData(DataComponentTypes.WRITABLE_BOOK_CONTENT)) {
            itemObj.addProperty("writable_book", true);
        }

        if (item.hasData(DataComponentTypes.WRITTEN_BOOK_CONTENT)) {
            itemObj.addProperty("written_book", true);
        }
    }

    // ==================== CATEGORIZATION HELPERS ====================

    private static String categorizeNutrition(int nutrition) {
        if (nutrition <= 2) return "light";
        if (nutrition <= 4) return "moderate";
        if (nutrition <= 8) return "filling";
        return "veryfilling";
    }

    private static String categorizeDurability(int damage, int maxDamage) {
        if (maxDamage == 0) return "perfect";
        double percent = (double)(maxDamage - damage) / maxDamage;
        if (percent >= 0.75) return "excellent";
        if (percent >= 0.50) return "good";
        if (percent >= 0.25) return "fair";
        return "poor";
    }

    private static String categorizeMiningSpeed(float speed) {
        if (speed >= 12.0) return "veryfast";
        if (speed >= 8.0) return "fast";
        if (speed >= 4.0) return "moderate";
        return "slow";
    }

    private static String categorizeStackSize(int stackSize) {
        if (stackSize == 1) return "nonstackable";
        if (stackSize <= 16) return "lowstack";
        if (stackSize <= 64) return "normalstack";
        return "highstack";
    }
}
