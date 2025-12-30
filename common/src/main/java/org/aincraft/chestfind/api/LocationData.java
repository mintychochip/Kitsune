package org.aincraft.chestfind.api;

/**
 * Platform-agnostic location data for containers.
 * This record represents a world location without Bukkit dependencies.
 */
public record LocationData(
    String worldName,
    int x,
    int y,
    int z
) {
    /**
     * Creates a new LocationData instance.
     * @throws IllegalArgumentException if worldName is null or blank
     */
    public LocationData {
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalArgumentException("World name cannot be null or blank");
        }
    }

    /**
     * Returns a string representation in world:x,y,z format.
     */
    @Override
    public String toString() {
        return String.format("%s:%d,%d,%d", worldName, x, y, z);
    }

    /**
     * Returns a formatted coordinate string.
     */
    public String getCoordinates() {
        return String.format("(%d, %d, %d)", x, y, z);
    }
}
