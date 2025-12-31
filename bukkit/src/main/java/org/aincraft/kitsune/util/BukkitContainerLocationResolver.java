package org.aincraft.kitsune.util;

import org.aincraft.kitsune.api.ContainerLocations;
import org.aincraft.kitsune.api.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.Nullable;

/**
 * Bukkit implementation of ContainerLocationResolver.
 * Handles both single-block containers and multi-block containers like double chests.
 */
public final class BukkitContainerLocationResolver implements ContainerLocationResolver {

    @Override
    @Nullable
    public ContainerLocations resolveLocations(InventoryHolder holder) {
        if (holder == null) {
            return null;
        }

        // Handle double chest - multi-block container
        if (holder instanceof DoubleChest doubleChest) {
            return resolveDoubleChestLocations(doubleChest);
        }

        // Handle regular container blocks
        if (holder instanceof Container container) {
            Location location = LocationConverter.toLocationData(container.getLocation());
            return ContainerLocations.single(location);
        }

        return null;
    }

    @Override
    @Nullable
    public ContainerLocations resolveLocations(Block block) {
        if (block == null) {
            return null;
        }

        // Check if the block state is a container
        if (!(block.getState() instanceof Container)) {
            return null;
        }

        // Try to get inventory holder through the block
        InventoryHolder holder = (InventoryHolder) block.getState();
        if (holder != null) {
            return resolveLocations(holder);
        }

        // Fallback to single location
        Location location = LocationConverter.toLocationData(block.getLocation());
        return ContainerLocations.single(location);
    }

    /**
     * Resolves locations for a double chest, determining the primary location
     * by selecting the lexicographically smaller position.
     *
     * @param doubleChest the double chest
     * @return container locations for the double chest
     */
    private ContainerLocations resolveDoubleChestLocations(DoubleChest doubleChest) {
        InventoryHolder leftHolder = doubleChest.getLeftSide();
        InventoryHolder rightHolder = doubleChest.getRightSide();

        if (leftHolder == null || rightHolder == null) {
            return null;
        }

        // Cast to Chest to get location
        if (!(leftHolder instanceof Chest) || !(rightHolder instanceof Chest)) {
            return null;
        }

        Chest leftChest = (Chest) leftHolder;
        Chest rightChest = (Chest) rightHolder;

        Location leftLocation = LocationConverter.toLocationData(leftChest.getLocation());
        Location rightLocation = LocationConverter.toLocationData(rightChest.getLocation());

        // Determine primary location: smaller X, then smaller Z (lexicographically)
        Location primaryLocation;
        if (isLocationSmaller(leftLocation, rightLocation)) {
            primaryLocation = leftLocation;
        } else {
            primaryLocation = rightLocation;
        }

        return ContainerLocations.multi(
            primaryLocation,
            java.util.List.of(leftLocation, rightLocation)
        );
    }

    /**
     * Compares two locations to determine which is lexicographically smaller.
     * Primary: smaller X, Secondary: smaller Z, Tertiary: smaller Y
     *
     * @param loc1 first location
     * @param loc2 second location
     * @return true if loc1 is smaller than loc2
     */
    private boolean isLocationSmaller(Location loc1, Location loc2) {
        if (loc1.blockX() != loc2.blockX()) {
            return loc1.blockX() < loc2.blockX();
        }
        if (loc1.blockZ() != loc2.blockZ()) {
            return loc1.blockZ() < loc2.blockZ();
        }
        return loc1.blockY() < loc2.blockY();
    }
}
