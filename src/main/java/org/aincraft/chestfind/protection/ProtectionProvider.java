package org.aincraft.chestfind.protection;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface ProtectionProvider {
    /**
     * Checks if a player can access a container at the given location.
     * @param player The player to check
     * @param location The container location
     * @return true if the player can access it, false otherwise
     */
    boolean canAccess(Player player, Location location);
}
