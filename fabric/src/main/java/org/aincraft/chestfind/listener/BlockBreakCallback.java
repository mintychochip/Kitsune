package org.aincraft.chestfind.listener;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.world.ServerWorld;
import org.aincraft.chestfind.platform.FabricLocationFactory;
import org.aincraft.chestfind.storage.VectorStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles block break events to remove containers from the index.
 */
public class BlockBreakCallback {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChestFind");

    private BlockBreakCallback() {
    }

    /**
     * Register the block break callback.
     */
    public static void register(VectorStorage vectorStorage) {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerWorld serverWorld)) {
                return;
            }

            // Check if the broken block was a container
            if (blockEntity instanceof Inventory) {
                var locationData = FabricLocationFactory.toLocationData(serverWorld, pos);
                vectorStorage.deleteByLocation(locationData).exceptionally(ex -> {
                    LOGGER.warn("Failed to delete container from index at {}", pos, ex);
                    return null;
                });
                LOGGER.debug("Removed container from index at {}", pos);
            }
        });
    }
}
