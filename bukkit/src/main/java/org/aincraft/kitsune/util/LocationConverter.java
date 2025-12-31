package org.aincraft.kitsune.util;

import org.aincraft.kitsune.api.Location;
import org.bukkit.Bukkit;
import org.bukkit.World;

/**
 * Utility class for converting between Bukkit Location and platform-agnostic LocationData.
 */
public final class LocationConverter {

    private LocationConverter() {
        // Utility class - no instantiation
    }

    /**
     * Converts a Bukkit Location to platform-agnostic LocationData.
     *
     * @param location the Bukkit location
     * @return the platform-agnostic location data
     * @throws IllegalArgumentException if location or world is null
     */
    public static Location toLocationData(org.bukkit.Location location) {
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location's world cannot be null");
        }
        return Location.of(
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
    public static org.bukkit.Location toBukkitLocation(Location data) {
        if (data == null) {
            throw new IllegalArgumentException("LocationData cannot be null");
        }
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return null; // World not loaded
        }
        return new org.bukkit.Location(world, data.blockX(), data.blockY(), data.blockZ());
    }

    /**
     * Converts platform-agnostic LocationData to a Bukkit Location centered in block.
     *
     * @param data the platform-agnostic location data
     * @return the Bukkit location centered in the block, or null if the world doesn't exist
     */
    public static org.bukkit.Location toBukkitLocationCentered(Location data) {
        if (data == null) {
            throw new IllegalArgumentException("LocationData cannot be null");
        }
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return null; // World not loaded
        }
        return new org.bukkit.Location(world, data.blockX() + 0.5, data.blockY() + 0.5, data.blockZ() + 0.5);
    }
}
