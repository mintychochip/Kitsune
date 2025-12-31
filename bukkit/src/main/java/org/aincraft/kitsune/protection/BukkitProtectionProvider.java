package org.aincraft.kitsune.protection;

import java.util.UUID;
import org.aincraft.kitsune.api.LocationData;
import org.aincraft.kitsune.util.LocationConverter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Abstract Bukkit adapter for ProtectionProvider.
 * Handles conversion from platform-agnostic types to Bukkit types.
 */
public abstract class BukkitProtectionProvider
    implements org.aincraft.kitsune.protection.ProtectionProvider {

    @Override
    public final boolean canAccess(UUID playerId, LocationData location) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            // Player not online - cannot verify access
            return false;
        }

        Location bukkitLocation = LocationConverter.toBukkitLocation(location);
        if (bukkitLocation == null) {
            // World not loaded - allow access by default
            return true;
        }

        return canAccessBukkit(player, bukkitLocation);
    }

    /**
     * Checks if a player can access a container at the given Bukkit location.
     *
     * @param player   The Bukkit player
     * @param location The Bukkit location
     * @return true if access is allowed
     */
    protected abstract boolean canAccessBukkit(Player player, Location location);
}
