package org.aincraft.kitsune;

import java.util.Optional;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;

public record BukkitBlock(org.bukkit.block.Block block) implements Block {

  @Override
  public String getType() {
    return block.getType().toString();
  }

  @Override
  public String getWorld() {
    return block.getWorld().toString();
  }

  @Override
  public Optional<Inventory> getInventory() {
    BlockState state = block.getState();
    if (!(state instanceof Container container)) {
      return Optional.empty();
    }
    return Optional.of(new BukkitInventory(container.getInventory()));
  }

  @Override
  public boolean isAir() {
    return block.getType().isAir();
  }
}
