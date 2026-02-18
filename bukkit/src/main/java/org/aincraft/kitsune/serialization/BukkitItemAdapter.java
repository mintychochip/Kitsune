package org.aincraft.kitsune.serialization;

import org.aincraft.kitsune.Item;
import org.bukkit.inventory.ItemStack;

/**
 * Bukkit adapter for item serialization.
 * Adapts ItemStack to the Item interface.
 */
public class BukkitItemAdapter implements ItemAdapter<ItemStack> {

    @Override
    public Item toItem(ItemStack item) {
        return new BukkitItem(item);
    }

    @Override
    public boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}
