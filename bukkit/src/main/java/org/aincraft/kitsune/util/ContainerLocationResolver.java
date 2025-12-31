package org.aincraft.chestfind.util;

import org.aincraft.chestfind.api.ContainerLocations;
import org.bukkit.block.Block;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves container locations, handling multi-block containers like double chests.
 */
public interface ContainerLocationResolver {

    /**
     * Resolve locations for a container from its inventory holder.
     * @param holder the inventory holder
     * @return container locations, or null if not a valid container
     */
    @Nullable
    ContainerLocations resolveLocations(InventoryHolder holder);

    /**
     * Resolve locations for a container from a block.
     * @param block the block that may be part of a container
     * @return container locations, or null if not a container block
     */
    @Nullable
    ContainerLocations resolveLocations(Block block);
}
