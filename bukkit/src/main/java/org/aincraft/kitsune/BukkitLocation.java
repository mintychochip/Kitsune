package org.aincraft.kitsune;

public record BukkitLocation(org.bukkit.Location location) implements Location {

  @Override
  public World getWorld() {
    return new BukkitWorld(location.getWorld());
  }

  @Override
  public int blockX() {
    return location.blockX();
  }

  @Override
  public int blockY() {
    return location.blockY();
  }

  @Override
  public int blockZ() {
    return location.blockZ();
  }

  @Override
  public Block getBlock() {
    return new BukkitBlock(location.getBlock());
  }
}
