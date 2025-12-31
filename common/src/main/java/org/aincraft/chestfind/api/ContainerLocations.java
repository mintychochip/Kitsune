package org.aincraft.chestfind.api;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Objects;

/**
 * Represents the physical locations of a container, supporting both single-block
 * containers (like hoppers) and multi-block containers (like double chests).
 * The primary location serves as the canonical position for storage and lookups.
 */
public final class ContainerLocations {
    private final LocationData primaryLocation;
    private final List<LocationData> allLocations;

    /**
     * Private constructor for creating ContainerLocations instances.
     * Use factory methods like {@link #single(LocationData)} or
     * {@link #multi(LocationData, List)} instead.
     */
    private ContainerLocations(LocationData primaryLocation, List<LocationData> allLocations) {
        this.primaryLocation = primaryLocation;
        this.allLocations = List.copyOf(allLocations);
    }

    /**
     * Creates a ContainerLocations for a single-block container.
     *
     * @param location the location of the container
     * @return a new ContainerLocations instance
     * @throws NullPointerException if location is null
     */
    public static ContainerLocations single(LocationData location) {
        Preconditions.checkNotNull(location, "Location cannot be null");
        return new ContainerLocations(location, List.of(location));
    }

    /**
     * Creates a ContainerLocations for a multi-block container.
     *
     * @param primaryLocation the canonical location for storage and lookups
     * @param allLocations all physical block positions of the container (must contain primary)
     * @return a new ContainerLocations instance
     * @throws NullPointerException if primaryLocation or allLocations is null
     * @throws IllegalArgumentException if allLocations is empty or doesn't contain primaryLocation
     */
    public static ContainerLocations multi(LocationData primaryLocation, List<LocationData> allLocations) {
        Preconditions.checkNotNull(primaryLocation, "Primary location cannot be null");
        Preconditions.checkNotNull(allLocations, "All locations cannot be null");
        Preconditions.checkArgument(!allLocations.isEmpty(), "All locations cannot be empty");
        Preconditions.checkArgument(
            allLocations.contains(primaryLocation),
            "Primary location must be contained in all locations"
        );
        return new ContainerLocations(primaryLocation, allLocations);
    }

    /**
     * Returns the canonical location used for storage and lookups.
     */
    public LocationData primaryLocation() {
        return primaryLocation;
    }

    /**
     * Returns an immutable list of all physical block positions.
     */
    public List<LocationData> allLocations() {
        return allLocations;
    }

    /**
     * Checks if this container contains the given location.
     *
     * @param loc the location to check
     * @return true if this container spans the given location, false otherwise
     */
    public boolean containsPosition(LocationData loc) {
        Preconditions.checkNotNull(loc, "Location cannot be null");
        return allLocations.contains(loc);
    }

    /**
     * Checks if this is a multi-block container.
     *
     * @return true if the container spans multiple blocks, false if it's single-block
     */
    public boolean isMultiBlock() {
        return allLocations.size() > 1;
    }

    /**
     * Returns a string representation.
     */
    @Override
    public String toString() {
        if (isMultiBlock()) {
            return String.format("ContainerLocations(primary=%s, blocks=%d)", primaryLocation, allLocations.size());
        } else {
            return String.format("ContainerLocations(single=%s)", primaryLocation);
        }
    }

    /**
     * Compares this ContainerLocations with another object.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContainerLocations that)) return false;
        return Objects.equals(primaryLocation, that.primaryLocation) &&
               Objects.equals(allLocations, that.allLocations);
    }

    /**
     * Returns a hash code for this ContainerLocations.
     */
    @Override
    public int hashCode() {
        return Objects.hash(primaryLocation, allLocations);
    }
}
