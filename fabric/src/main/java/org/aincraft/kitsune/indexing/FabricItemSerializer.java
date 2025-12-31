package org.aincraft.kitsune.indexing;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import org.aincraft.kitsune.api.ItemTagProviderRegistry;
import org.aincraft.kitsune.model.ContainerPath;
import org.aincraft.kitsune.model.NestedContainerRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Fabric-specific item serializer for MC 1.20.1.
 * Converts ItemStacks to embedding text and storage JSON.
 */
public class FabricItemSerializer {
    private static final Gson GSON = new Gson();
    private static final int MAX_NESTING_DEPTH = 10;

    private final ItemTagProviderRegistry tagRegistry;

    public FabricItemSerializer(ItemTagProviderRegistry tagRegistry) {
        this.tagRegistry = tagRegistry;
    }

    /**
     * Format an item name from registry ID.
     * Converts "minecraft:birch_planks" -> "Birch Planks"
     */
    private static String formatItemName(String registryId) {
        // Remove namespace
        String name = registryId.contains(":") ? registryId.split(":")[1] : registryId;

        // Convert snake_case to Title Case
        String[] words = name.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(" ");
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1));
            }
        }
        return result.toString();
    }

    /**
     * Serialize items into chunks for embedding.
     */
    public List<SerializedItem> serializeItemsToChunks(ItemStack[] items) {
        return serializeItemsToChunks(items, ContainerPath.ROOT, 0);
    }

    private List<SerializedItem> serializeItemsToChunks(ItemStack[] items, ContainerPath path, int depth) {
        List<SerializedItem> chunks = new ArrayList<>();

        if (depth > MAX_NESTING_DEPTH) {
            return chunks;
        }

        for (ItemStack item : items) {
            if (item == null || item.isEmpty()) {
                continue;
            }

            FabricIndexableItem indexableItem = new FabricIndexableItem(item);
            JsonObject itemJson = createItemJson(item, indexableItem);

            // Create embedding text
            String embeddingText = createEmbeddingText(item, itemJson, indexableItem);

            // Create storage JSON
            JsonArray singleItemArray = new JsonArray();
            singleItemArray.add(itemJson);
            String storageJson = GSON.toJson(singleItemArray);

            // Compute content hash for caching
            String contentHash = indexableItem.contentHashCode();

            chunks.add(new SerializedItem(embeddingText, storageJson));

            // Handle nested containers (shulker boxes, bundles)
            List<ItemStack> nestedItems = extractNestedItems(item);
            if (!nestedItems.isEmpty()) {
                String containerType = getItemId(item);
                String containerColor = null;
                // Extract color from shulker boxes (e.g., "minecraft:red_shulker_box" -> "red")
                if (containerType.contains("shulker_box")) {
                    String[] parts = containerType.split("_");
                    if (parts.length >= 2 && !parts[0].equals("minecraft")) {
                        containerColor = parts[0];
                    } else if (containerType.contains("red_")) {
                        containerColor = "red";
                    }
                }
                String customName = item.hasCustomName() ? item.getName().getString() : null;
                NestedContainerRef ref = new NestedContainerRef(containerType, containerColor, customName, 0);
                ContainerPath nestedPath = path.push(ref);
                chunks.addAll(serializeItemsToChunks(
                        nestedItems.toArray(new ItemStack[0]),
                        nestedPath,
                        depth + 1
                ));
            }
        }

        return chunks;
    }

    private String createEmbeddingText(ItemStack item, JsonObject itemJson, FabricIndexableItem indexableItem) {
        StringBuilder sb = new StringBuilder();

        // Item name
        sb.append(formatItemName(getItemId(item)));

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

        // Enchantments
        var enchants = indexableItem.getEnchantments();
        if (!enchants.isEmpty()) {
            sb.append(" #enchanted #magical");
            for (var entry : enchants.entrySet()) {
                String enchName = entry.getKey();
                if (enchName.contains(":")) {
                    enchName = enchName.split(":")[1];
                }
                sb.append(" #").append(enchName.replace("_", ""));
                sb.append(" #level").append(entry.getValue());
            }
        }

        // Custom name
        if (item.hasCustomName()) {
            sb.append(" #customnamed");
        }

        // Collect custom tags from providers
        if (tagRegistry != null) {
            Set<String> customTags = tagRegistry.collectTags(indexableItem);
            for (String tag : customTags) {
                sb.append(" #").append(tag.replace(" ", ""));
            }
        }

        // Add semantic tags
        addSemanticTags(sb, getItemId(item));

        return sb.toString();
    }

    private void addSemanticTags(StringBuilder sb, String itemId) {
        String name = itemId.contains(":") ? itemId.split(":")[1] : itemId;
        name = name.toUpperCase();

        // Armor
        if (name.contains("HELMET") || name.contains("CHESTPLATE") ||
                name.contains("LEGGINGS") || name.contains("BOOTS")) {
            sb.append(" #armor #protection");
        }

        // Weapons
        if (name.contains("SWORD")) sb.append(" #weapon #melee #combat");
        if (name.contains("BOW")) sb.append(" #weapon #ranged #combat");
        if (name.contains("CROSSBOW")) sb.append(" #weapon #ranged #combat");
        if (name.contains("TRIDENT")) sb.append(" #weapon #melee #ranged #combat");

        // Tools
        if (name.contains("PICKAXE")) sb.append(" #tool #mining");
        if (name.contains("SHOVEL")) sb.append(" #tool #digging");
        if (name.contains("AXE") && !name.contains("PICKAXE")) sb.append(" #tool #chopping");
        if (name.contains("HOE")) sb.append(" #tool #farming");

        // Materials
        if (name.contains("DIAMOND")) sb.append(" #diamond #valuable");
        if (name.contains("IRON")) sb.append(" #iron");
        if (name.contains("GOLD")) sb.append(" #gold #valuable");
        if (name.contains("NETHERITE")) sb.append(" #netherite #valuable");
        if (name.contains("EMERALD")) sb.append(" #emerald #valuable");

        // Ores/Raw materials
        if (name.contains("ORE")) sb.append(" #ore #mining");
        if (name.contains("RAW_")) sb.append(" #rawmaterial");
        if (name.contains("INGOT")) sb.append(" #ingot #smelted");

        // Food
        if (name.contains("APPLE") || name.contains("BREAD") || name.contains("BEEF") ||
                name.contains("PORK") || name.contains("CHICKEN") || name.contains("FISH") ||
                name.contains("CARROT") || name.contains("POTATO") || name.contains("MELON") ||
                name.contains("COOKIE") || name.contains("CAKE") || name.contains("PIE")) {
            sb.append(" #food #edible");
        }

        // Storage
        if (name.contains("SHULKER")) sb.append(" #storage #shulker #container");
        if (name.contains("CHEST")) sb.append(" #storage #chest #container");
        if (name.contains("BARREL")) sb.append(" #storage #barrel #container");
        if (name.contains("BUNDLE")) sb.append(" #storage #bundle");
    }

    private JsonObject createItemJson(ItemStack item, FabricIndexableItem indexableItem) {
        JsonObject itemObj = new JsonObject();

        String itemId = getItemId(item);
        itemObj.addProperty("name", formatItemName(itemId));
        itemObj.addProperty("id", itemId);
        itemObj.addProperty("amount", item.getCount());

        // Add enchantments
        var enchants = indexableItem.getEnchantments();
        if (!enchants.isEmpty()) {
            JsonArray enchantArray = new JsonArray();
            for (var entry : enchants.entrySet()) {
                JsonObject ench = new JsonObject();
                ench.addProperty("enchantment", entry.getKey());
                ench.addProperty("level", entry.getValue());
                enchantArray.add(ench);
            }
            itemObj.add("enchantments", enchantArray);
        }

        // Custom name
        if (item.hasCustomName()) {
            Text name = item.getName();
            itemObj.addProperty("custom_name", name.getString());
        }

        // Lore
        var lore = indexableItem.lore();
        if (!lore.isEmpty()) {
            JsonArray loreArray = new JsonArray();
            for (var line : lore) {
                loreArray.add(PlainTextComponentSerializer.plainText().serialize(line));
            }
            itemObj.add("lore", loreArray);
        }

        // Add categorical metadata
        addCategoricalMetadata(itemObj, itemId);

        return itemObj;
    }

    private void addCategoricalMetadata(JsonObject itemObj, String itemId) {
        String name = itemId.contains(":") ? itemId.split(":")[1] : itemId;
        name = name.toUpperCase();

        // Category
        String category = categorizeItem(name);
        if (category != null) {
            itemObj.addProperty("category", category);
        }

        // Tool type and material
        addToolMetadata(itemObj, name);
    }

    private String categorizeItem(String name) {
        if (name.contains("HELMET") || name.contains("CHESTPLATE") ||
                name.contains("LEGGINGS") || name.contains("BOOTS")) {
            return "armor";
        }
        if (name.contains("SWORD") || name.contains("BOW") ||
                name.contains("CROSSBOW") || name.contains("TRIDENT")) {
            return "weapon";
        }
        if (name.contains("PICKAXE") || name.contains("SHOVEL") ||
                name.contains("AXE") || name.contains("HOE")) {
            return "tool";
        }
        if (name.contains("ORE") || name.contains("RAW_") ||
                name.contains("INGOT") || name.contains("NUGGET")) {
            return "rawmaterial";
        }
        return null;
    }

    private void addToolMetadata(JsonObject itemObj, String name) {
        // Tool type
        if (name.contains("PICKAXE")) itemObj.addProperty("tool_type", "pickaxe");
        else if (name.contains("SHOVEL")) itemObj.addProperty("tool_type", "shovel");
        else if (name.contains("AXE")) itemObj.addProperty("tool_type", "axe");
        else if (name.contains("HOE")) itemObj.addProperty("tool_type", "hoe");
        else if (name.contains("SWORD")) itemObj.addProperty("tool_type", "sword");

        // Tool material
        if (name.contains("DIAMOND")) itemObj.addProperty("tool_material", "diamond");
        else if (name.contains("IRON")) itemObj.addProperty("tool_material", "iron");
        else if (name.contains("STONE")) itemObj.addProperty("tool_material", "stone");
        else if (name.contains("WOODEN") || name.contains("WOOD")) itemObj.addProperty("tool_material", "wood");
        else if (name.contains("GOLDEN") || name.contains("GOLD")) itemObj.addProperty("tool_material", "gold");
        else if (name.contains("NETHERITE")) itemObj.addProperty("tool_material", "netherite");
    }

    private String getItemId(ItemStack item) {
        return Registries.ITEM.getId(item.getItem()).toString();
    }

    /**
     * Extract items from nested containers (shulker boxes, bundles).
     */
    private List<ItemStack> extractNestedItems(ItemStack item) {
        List<ItemStack> nested = new ArrayList<>();

        NbtCompound nbt = item.getNbt();
        if (nbt == null) {
            return nested;
        }

        // Shulker box contents
        if (nbt.contains("BlockEntityTag")) {
            NbtCompound blockEntity = nbt.getCompound("BlockEntityTag");
            if (blockEntity.contains("Items")) {
                NbtList items = blockEntity.getList("Items", 10); // 10 = Compound tag
                for (int i = 0; i < items.size(); i++) {
                    NbtCompound itemNbt = items.getCompound(i);
                    ItemStack nestedItem = ItemStack.fromNbt(itemNbt);
                    if (!nestedItem.isEmpty()) {
                        nested.add(nestedItem);
                    }
                }
            }
        }

        // Bundle contents (1.20.1 format)
        if (nbt.contains("Items")) {
            NbtList items = nbt.getList("Items", 10);
            for (int i = 0; i < items.size(); i++) {
                NbtCompound itemNbt = items.getCompound(i);
                ItemStack nestedItem = ItemStack.fromNbt(itemNbt);
                if (!nestedItem.isEmpty()) {
                    nested.add(nestedItem);
                }
            }
        }

        return nested;
    }
}
