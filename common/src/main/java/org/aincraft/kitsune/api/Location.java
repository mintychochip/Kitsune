package org.aincraft.kitsune.api;

import com.google.common.base.Preconditions;
import java.util.Objects;

/**
 * Platform-agnostic location data for containers.
 * This class represents a world location without Bukkit dependencies.
 * Instances are created via factory methods for consistent validation.
 */
public final class Location {
    private final String worldName;
    private final int blockX;
    private final int blockY;
    private final int blockZ;

    /**
     * Private constructor for creating LocationData instances.
     * Use factory methods like {@link #of(String, int, int, int)} instead.
     */
    private Location(String worldName, int blockX, int blockY, int blockZ) {
        this.worldName = worldName;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
    }

    /**
     * Factory method to create a LocationData instance.
     *
     * @param worldName the name of the world
     * @param blockX    the X coordinate
     * @param blockY    the Y coordinate
     * @param blockZ    the Z coordinate
     * @return a new LocationData instance
     * @throws NullPointerException if worldName is null
     * @throws IllegalArgumentException if worldName is blank
     */
    public static Location of(String worldName, int blockX, int blockY, int blockZ) {
        Preconditions.checkNotNull(worldName, "World name cannot be null");
        Preconditions.checkArgument(!worldName.isBlank(), "World name cannot be blank");
        return new Location(worldName, blockX, blockY, blockZ);
    }

    /**
     * Returns the world name.
     */
    public String worldName() {
        return worldName;
    }

    /**
     * Returns the X coordinate.
     */
    public int blockX() {
        return blockX;
    }

    /**
     * Returns the Y coordinate.
     */
    public int blockY() {
        return blockY;
    }

    /**
     * Returns the Z coordinate.
     */
    public int blockZ() {
        return blockZ;
    }

    /**
     * Returns a string representation in world:x,y,z format.
     */
    @Override
    public String toString() {
        return String.format("%s:%d,%d,%d", worldName, blockX, blockY, blockZ);
    }

    /**
     * Returns a formatted coordinate string.
     */
    public String getCoordinates() {
        return String.format("(%d, %d, %d)", blockX, blockY, blockZ);
    }

    /**
     * Compares this LocationData with another object.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Location that)) return false;
        return blockX == that.blockX && blockY == that.blockY && blockZ == that.blockZ &&
               Objects.equals(worldName, that.worldName);
    }

    /**
     * Returns a hash code for this LocationData.
     */
    @Override
    public int hashCode() {
        return Objects.hash(worldName, blockX, blockY, blockZ);
    }
}
