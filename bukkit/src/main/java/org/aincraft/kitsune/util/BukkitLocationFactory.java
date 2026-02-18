package org.aincraft.kitsune.util;

import com.google.common.base.Preconditions;
import org.aincraft.kitsune.BukkitLocation;
import org.aincraft.kitsune.Location;
import org.bukkit.Bukkit;
import org.bukkit.World;

/**
 * Utility class for converting between Bukkit Location and platform-agnostic LocationData.
 */
public final class BukkitLocationFactory {

    private BukkitLocationFactory() {
        // Utility class - no instantiation
    }

    /**
     * Converts a Bukkit Location to BukkitLocation with block access.
     *
     * @param location the Bukkit location
     * @return the BukkitLocation wrapper
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
        return new BukkitLocation(location);
    }

    /**
     * Converts platform-agnostic LocationData to a Bukkit Location.
     * Throws an exception if the world is not loaded.
     *
     * @param data the platform-agnostic location data
     * @return the Bukkit location
     * @throws IllegalArgumentException if the world is not loaded
     */
    public static org.bukkit.Location toBukkitLocation(Location data) {
        Preconditions.checkNotNull(data, "LocationData cannot be null");
        World world = Bukkit.getWorld(data.getWorld().getName());
        Preconditions.checkArgument(world != null, "World is not yet loaded");
        return new org.bukkit.Location(world, data.blockX(), data.blockY(), data.blockZ());
    }

    /**
     * Converts platform-agnostic LocationData to a Bukkit Location.
     * Returns null if the world is not loaded.
     *
     * @param data the platform-agnostic location data
     * @return the Bukkit location, or null if the world doesn't exist
     * @throws IllegalArgumentException if location data is null
     */
    public static org.bukkit.Location toBukkitLocationOrNull(Location data) {
        if (data == null) {
            throw new IllegalArgumentException("LocationData cannot be null");
        }
        World world = Bukkit.getWorld(data.getWorld().getName());
        if (world == null) {
            return null; // World not loaded
        }
        return new org.bukkit.Location(world, data.blockX(), data.blockY(), data.blockZ());
    }
}
