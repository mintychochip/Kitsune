package org.aincraft.kitsune;

import java.util.Optional;

public interface Block {

  String getType();

  String getWorld();

  Optional<Inventory> getInventory();

  boolean isAir();
}
