package org.aincraft.kitsune;

import org.aincraft.kitsune.serialization.BukkitItem;
import org.bukkit.inventory.ItemStack;

public record BukkitInventory(org.bukkit.inventory.Inventory inventory) implements Inventory {

  @Override
  public int getSize() {
    return inventory.getSize();
  }

  @Override
  public Item getItem(int slot) {
    ItemStack bukkitItem = inventory.getItem(slot);
    if (bukkitItem == null) {
      return null;
    }
    return new BukkitItem(bukkitItem);
  }
}
