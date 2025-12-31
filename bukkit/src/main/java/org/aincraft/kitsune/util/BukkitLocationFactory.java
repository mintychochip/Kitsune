package org.aincraft.kitsune.util;

import com.google.common.base.Preconditions;
import org.aincraft.kitsune.api.LocationData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Utility class for converting between Bukkit Location and platform-agnostic LocationData.
 */
public final class BukkitLocationFactory {

    private BukkitLocationFactory() {
        // Utility class - no instantiation
    }

    /**
     * Converts a Bukkit Location to platform-agnostic LocationData.
     *
     * @param location the Bukkit location
     * @return the platform-agnostic location data
     * @throws IllegalArgumentException if location or world is null
     */
    public static LocationData toLocationData(Location location) {
        Preconditions.checkNotNull(location, "Location cannot be null");
        World world = location.getWorld();
        Preconditions.checkNotNull(world, "Location's world cannot be null");
        return LocationData.of(
            world.getName(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ()
        );
    }

    /**
     * Converts platform-agnostic LocationData to a Bukkit Location.
     *
     * @param data the platform-agnostic location data
     * @return the Bukkit location, or null if the world doesn't exist
     */
    public static Location toBukkitLocation(LocationData data) {
        Preconditions.checkNotNull(data, "LocationData cannot be null");
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return null; // World not loaded
        }
        return new Location(world, data.blockX(), data.blockY(), data.blockZ());
    }

    /**
     * Converts platform-agnostic LocationData to a Bukkit Location centered in block.
     *
     * @param data the platform-agnostic location data
     * @return the Bukkit location centered in the block, or null if the world doesn't exist
     */
    public static Location toBukkitLocationCentered(LocationData data) {
        Preconditions.checkNotNull(data, "LocationData cannot be null");
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return null; // World not loaded
        }
        return new Location(world, data.blockX() + 0.5, data.blockY() + 0.5, data.blockZ() + 0.5);
    }
}
