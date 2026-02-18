package org.aincraft.kitsune.serialization;

import org.aincraft.kitsune.Item;
import org.aincraft.kitsune.api.serialization.ItemSerializer;
import org.aincraft.kitsune.api.serialization.TagProviderRegistry;
import org.aincraft.kitsune.api.indexing.SerializedItem;
import org.aincraft.kitsune.api.model.NestedContainerRef;
import org.aincraft.kitsune.api.model.ContainerNode;
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
        ContainerNode root = serializeTree(items);
        return flattenTree(root);
    }

    @Override
    public ContainerNode serializeTree(List<?> items) {
        List<SerializedItem> itemsList = new ArrayList<>();
        List<ContainerNode> children = new ArrayList<>();

        // Build the tree structure
        buildTree(items, itemsList, children, 0, new ArrayList<>());

        // Create root container (top-level items as children of implicit root)
        return new ContainerNode("inventory", null, null, 0, children, itemsList);
    }

    /**
     * Builds the container tree structure recursively.
     */
    private void buildTree(List<?> items,
                          List<SerializedItem> itemsAtLevel,
                          List<ContainerNode> childrenAtLevel,
                          int depth,
                          List<NestedContainerRef> containerPath) {

        // Group items and nested containers
        List<NestedContainer> nestedContainers = new ArrayList<>();

        for (int slot = 0; slot < items.size(); slot++) {
            Object item = items.get(slot);
            if (shouldSkip(item)) {
                continue;
            }

            Item context = createContext(item);

            // Check if this item is a container with nested items
            List<NestedItems> nestedItemsList = getNestedItems(context, slot);
            if (!nestedItemsList.isEmpty()) {
                // This is a container - add to nested containers list
                nestedContainers.add(new NestedContainer(item, slot, nestedItemsList));
            } else {
                // Regular item - serialize it
                Set<String> tags = tagRegistry.collectTags(context);
                String embeddingText = createEmbeddingText(context, tags);
                String storageJson = createStorageJson(context, slot, containerPath);

                itemsAtLevel.add(new SerializedItem(embeddingText, storageJson));
            }
        }

        // Process nested containers and build children
        for (NestedContainer nested : nestedContainers) {
            // Create the container node
            Item context = createContext(nested.item);
            String color = null;
            String customName = null;

            // Parse container info
            String containerInfo = nested.nestedItems.containerInfo;
            if (containerInfo != null && !containerInfo.isEmpty()) {
                if (containerInfo.contains("\"")) {
                    int firstQuote = containerInfo.indexOf('"');
                    int lastQuote = containerInfo.lastIndexOf('"');
                    if (firstQuote < lastQuote) {
                        customName = containerInfo.substring(firstQuote + 1, lastQuote);
                    }
                    String beforeQuote = containerInfo.substring(0, firstQuote).trim();
                    if (!beforeQuote.isEmpty()) {
                        color = beforeQuote;
                    }
                } else {
                    color = containerInfo;
                }
            }

            // Create container node with empty items and children
            ContainerNode containerNode = new ContainerNode(
                nested.nestedItems.containerType,
                color,
                customName,
                nested.slot,
                Collections.emptyList(),  // will be filled by recursive call
                Collections.emptyList()    // items in this container
            );

            // Recursively build children for this container
            List<SerializedItem> childItems = new ArrayList<>();
            List<ContainerNode> childContainers = new ArrayList<>();

            NestedContainerRef ref = new NestedContainerRef(
                nested.nestedItems.containerType,
                color,
                customName,
                nested.slot
            );
            List<NestedContainerRef> childPath = new ArrayList<>(containerPath);
            childPath.add(ref);

            buildTree(nested.nestedItems.items, childItems, childContainers, depth + 1, childPath);

            // Update the container node with its items and children
            containerNode = new ContainerNode(
                nested.nestedItems.containerType,
                color,
                customName,
                nested.slot,
                childContainers,
                childItems
            );

            childrenAtLevel.add(containerNode);
        }
    }

    /**
     * Helper class for nested containers during tree building.
     */
    private static class NestedContainer {
        final Object item;
        final int slot;
        final List<NestedItems> nestedItems;

        NestedContainer(Object item, int slot, List<NestedItems> nestedItems) {
            this.item = item;
            this.slot = slot;
            this.nestedItems = nestedItems;
        }
    }

    /**
     * Flattens a ContainerNode tree into a flat list of SerializedItems.
     * This method maintains backward compatibility with the original interface.
     */
    private List<SerializedItem> flattenTree(ContainerNode root) {
        List<SerializedItem> results = new ArrayList<>();
        for (ContainerNode child : root.getChildren()) {
            results.addAll(child.flattenWithPaths().stream()
                .map(ContainerNode.ItemWithPath::getItem)
                .toList());
        }
        return results;
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
