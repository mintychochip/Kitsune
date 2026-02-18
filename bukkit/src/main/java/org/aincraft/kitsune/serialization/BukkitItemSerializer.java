package org.aincraft.kitsune.serialization;

import org.aincraft.kitsune.api.serialization.ItemSerializer;
import org.aincraft.kitsune.api.serialization.TagProviderRegistry;
import org.aincraft.kitsune.api.indexing.SerializedItem;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bukkit/Paper adapter for GenericItemSerializer.
 * Provides Bukkit-specific convenience methods and ItemStack support.
 */
public class BukkitItemSerializer extends GenericItemSerializer {
    public BukkitItemSerializer(TagProviderRegistry tagRegistry) {
        super(tagRegistry, obj -> new BukkitItem((ItemStack) obj));
    }

    /**
     * Convenience method for serializing ItemStack arrays (Bukkit inventory format).
     */
    public List<SerializedItem> serializeItemsToChunks(ItemStack[] items) {
        if (items == null || items.length == 0) {
            return Collections.emptyList();
        }

        List<ItemStack> itemList = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                itemList.add(item);
            }
        }
        return serialize(itemList);
    }

    /**
     * Convenience method for serializing ItemStack arrays with tree structure.
     */
    public ContainerNode serializeItemsToChunksTree(ItemStack[] items) {
        if (items == null || items.length == 0) {
            return new ContainerNode("inventory", null, null, 0, Collections.emptyList());
        }

        List<ItemStack> itemList = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                itemList.add(item);
            }
        }
        return serializeTree(itemList);
    }
}
