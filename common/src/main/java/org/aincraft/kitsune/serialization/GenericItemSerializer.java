package org.aincraft.kitsune.serialization;

import org.aincraft.kitsune.Item;
import org.aincraft.kitsune.api.serialization.ItemSerializer;
import org.aincraft.kitsune.api.serialization.TagProviderRegistry;
import org.aincraft.kitsune.api.indexing.SerializedItem;
import org.aincraft.kitsune.api.model.NestedContainerRef;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Platform-agnostic item serializer for converting items into embedding text and storage JSON.
 * Works with any platform-specific item type via the Item interface.
 */
public class GenericItemSerializer implements ItemSerializer {
    private static final Gson gson = new Gson();
    private final TagProviderRegistry tagRegistry;
    private final Function<Object, Item> itemFactory;

    public GenericItemSerializer(TagProviderRegistry tagRegistry, Function<Object, Item> itemFactory) {
        this.tagRegistry = tagRegistry;
        this.itemFactory = itemFactory;
    }

    /**
     * Format material name for embedding (e.g., BIRCH_PLANKS -> Birch Planks).
     */
    private String formatMaterialName(String rawName) {
        String[] words = rawName.toLowerCase().split("_");
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
     * Build embedding text from item context and collected tags.
     * Semantic logic is provided by TagProviderRegistry.
     */
    private String createEmbeddingText(Item item, Set<String> tags) {
        StringBuilder sb = new StringBuilder();
        String materialName = item.material();

        // Formatted material name
        sb.append(formatMaterialName(materialName));

        // Add all tags from registry
        for (String tag : tags) {
            sb.append(" #").append(tag);
        }

        return sb.toString().toLowerCase();
    }

    /**
     * Create an Item context from platform-specific item using the factory.
     */
    private Item createContext(Object item) {
        return itemFactory.apply(item);
    }

    /**
     * Create storage JSON for an item.
     */
    private String createStorageJson(Item context, int slot, List<NestedContainerRef> containerPath) {
        JsonObject itemObj = new JsonObject();
        String material = context.material();

        // Basic info
        itemObj.addProperty("name", formatMaterialName(material));
        itemObj.addProperty("material", material);
        itemObj.addProperty("amount", context.amount());
        itemObj.addProperty("slot", slot);

        // Container path for nested items - serialize as JSON array of objects
        if (containerPath != null && !containerPath.isEmpty()) {
            JsonArray pathArray = new JsonArray();
            for (NestedContainerRef ref : containerPath) {
                pathArray.add(com.google.gson.JsonParser.parseString(ref.toJson()));
            }
            itemObj.add("container_path", pathArray);
        }

        // Display name - use Item interface method
        String displayName = context.getDisplayName();
        itemObj.addProperty("display_name", displayName);

        // Custom name - use Item interface method
        String customName = context.getCustomName();
        if (customName != null) {
            itemObj.addProperty("custom_name", customName);
        }

        // Enchantments - convert Item's enchantments map to JSON
        extractEnchantmentsToJson(context, itemObj);

        // Lore - use Item interface method
        List<String> lore = context.getLore();
        if (!lore.isEmpty()) {
            JsonArray loreArray = new JsonArray();
            for (String line : lore) {
                loreArray.add(line);
            }
            itemObj.add("lore", loreArray);
        }

        // Durability - use Item interface method
        Item.DurabilityInfo durability = context.getDurability();
        if (durability != null) {
            JsonObject durabilityObj = new JsonObject();
            durabilityObj.addProperty("current", durability.current());
            durabilityObj.addProperty("max", durability.max());
            durabilityObj.addProperty("percent", durability.percent());
            itemObj.add("durability_info", durabilityObj);
        }

        // Rarity - use Item interface method
        String rarity = context.getRarity();
        if (rarity != null) {
            itemObj.addProperty("rarity", rarity);
        }

        // Unbreakable - use Item interface method
        if (context.isUnbreakable()) {
            itemObj.addProperty("unbreakable", true);
        }

        // Block vs Item - use Item interface method
        itemObj.addProperty("material_type", context.isBlock() ? "block" : "item");

        // Creative category - use Item interface method
        String creativeCategory = context.getCreativeCategory();
        if (creativeCategory != null) {
            itemObj.addProperty("creative_category", creativeCategory);
        }

        // Wrap in array for storage
        JsonArray singleItemArray = new JsonArray();
        singleItemArray.add(itemObj);
        return gson.toJson(singleItemArray);
    }

    /**
     * Check if an item should be skipped (null or air).
     */
    private boolean shouldSkip(Object item) {
        if (item == null) {
            return true;
        }
        // For items wrapped in Item interface, check display name or use type check
        // Since we can't directly check isAir without platform-specific code,
        // we defer to the caller (serialize method) to provide valid items
        return false;
    }

    /**
     * Get nested items from containers (bundles, shulkers, etc.).
     * Returns empty list if item has no nested contents.
     */
    private List<NestedItems> getNestedItems(Item context, int parentSlot) {
        List<NestedItems> nested = new ArrayList<>();

        // Bundle contents - use Item interface method
        if (context.hasBundle()) {
            List<?> bundleContents = context.getBundleContents();
            if (!bundleContents.isEmpty()) {
                nested.add(new NestedItems(
                    bundleContents,
                    "bundle",
                    getContainerInfo(context)
                ));
            }
        }

        // Container contents (shulker boxes, chests, barrels, etc.)
        if (context.hasShulkerContents()) {
            List<?> containerContents = context.getContainerContents();
            if (!containerContents.isEmpty()) {
                // Get container type from Item interface
                String containerType = context.getContainerType();
                nested.add(new NestedItems(
                    containerContents,
                    containerType,
                    getContainerInfo(context)
                ));
            }
        }

        return nested;
    }

    private void extractEnchantmentsToJson(Item context, JsonObject itemObj) {
        // Get enchantments from Item interface (combines regular and stored enchantments)
        Map<String, Integer> enchants = context.enchantments();
        if (!enchants.isEmpty()) {
            JsonArray enchantArray = new JsonArray();
            for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
                JsonObject ench = new JsonObject();
                ench.addProperty("enchantment", entry.getKey());
                ench.addProperty("level", entry.getValue());
                enchantArray.add(ench);
            }
            itemObj.add("enchantments_array", enchantArray);
        }
    }

    private String getContainerInfo(Item context) {
        StringBuilder info = new StringBuilder();
        String materialName = context.material();

        // Add color for shulkers
        if (materialName.contains("SHULKER_BOX") && !materialName.equals("SHULKER_BOX")) {
            String color = materialName.replace("_SHULKER_BOX", "").toLowerCase();
            info.append(color).append(" ");
        }

        // Add custom name if present - use Item interface method
        String customName = context.getCustomName();
        if (customName != null) {
            info.append("\"").append(customName).append("\"");
        }

        return info.toString().trim();
    }

    @Override
    public List<SerializedItem> serialize(List<?> items) {
        List<SerializedItem> results = new ArrayList<>();
        serializeRecursive(items, results, 0, new ArrayList<>());
        return results;
    }

    private void serializeRecursive(List<?> items, List<SerializedItem> results, int depth, List<NestedContainerRef> containerPath) {
        for (int slot = 0; slot < items.size(); slot++) {
            Object item = items.get(slot);
            if (shouldSkip(item)) {
                continue;
            }

            Item context = createContext(item);
            Set<String> tags = tagRegistry.collectTags(context);
            String embeddingText = createEmbeddingText(context, tags);
            String storageJson = createStorageJson(context, slot, containerPath);

            results.add(new SerializedItem(embeddingText, storageJson));

            // Handle nested items (bundles, shulkers, chests, etc.)
            // Allow deep nesting since creative mode middle-click can nest infinitely
            if (depth < 10) { // Limit nesting depth to prevent infinite loops
                for (NestedItems nested : getNestedItems(context, slot)) {
                    // Build new path: current path + this container with slot index
                    List<NestedContainerRef> nestedPath = new ArrayList<>(containerPath);

                    // Parse container info for color and custom name
                    String color = null;
                    String customName = null;
                    String containerInfo = nested.containerInfo;
                    if (containerInfo != null && !containerInfo.isEmpty()) {
                        // Check if it starts with a quoted custom name
                        if (containerInfo.contains("\"")) {
                            int firstQuote = containerInfo.indexOf('"');
                            int lastQuote = containerInfo.lastIndexOf('"');
                            if (firstQuote < lastQuote) {
                                customName = containerInfo.substring(firstQuote + 1, lastQuote);
                            }
                            // Color is before the quote
                            String beforeQuote = containerInfo.substring(0, firstQuote).trim();
                            if (!beforeQuote.isEmpty()) {
                                color = beforeQuote;
                            }
                        } else {
                            // Just color, no custom name
                            color = containerInfo;
                        }
                    }

                    NestedContainerRef ref = new NestedContainerRef(
                        nested.containerType,
                        color,
                        customName,
                        slot  // Slot of the container in parent inventory
                    );
                    nestedPath.add(ref);

                    serializeRecursive(nested.items, results, depth + 1, nestedPath);
                }
            }
        }
    }

    /**
     * Record for nested items with container path info.
     */
    public static class NestedItems {
        public final List<?> items;
        public final String containerType;
        public final String containerInfo;

        public NestedItems(List<?> items, String containerType, String containerInfo) {
            this.items = items;
            this.containerType = containerType;
            this.containerInfo = containerInfo;
        }
    }
}
