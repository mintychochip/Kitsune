package org.aincraft.kitsune.serialization;

import java.util.List;
import org.aincraft.kitsune.Inventory;
import org.aincraft.kitsune.Item;
import org.bukkit.inventory.ItemStack;

/**
 * Inventory wrapper for item container contents (shulker boxes, bundles).
 * Provides read-only access to items stored in DataComponent containers.
 */
public class ItemContainerInventory implements Inventory {

  private final List<ItemStack> items;
  private final int size;

  public ItemContainerInventory(List<ItemStack> items) {
    this.items = items;
    this.size = items.size();
  }

  @Override
  public int getSize() {
    return size;
  }

  @Override
  public Item getItem(int slot) {
    if (slot < 0 || slot >= size) {
      return null;
    }
    ItemStack bukkitItem = items.get(slot);
    if (bukkitItem == null || bukkitItem.getType().isAir()) {
      return null;
    }
    return new BukkitItem(bukkitItem);
  }
}
