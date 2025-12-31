package org.aincraft.chestfind.listener;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.aincraft.chestfind.api.LocationData;
import org.aincraft.chestfind.storage.VectorStorage;

/**
 * NeoForge event handler for block break events.
 * Removes container from vector storage index when broken.
 */
@EventBusSubscriber(modid = "chestfind", bus = EventBusSubscriber.Bus.GAME)
public class BlockBreakHandler {
    private static VectorStorage vectorStorage;

    /**
     * Sets the VectorStorage instance. Must be called during plugin initialization.
     */
    public static void setVectorStorage(VectorStorage storage) {
        vectorStorage = storage;
    }

    @SubscribeEvent
    static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (vectorStorage == null || event.getLevel().isClientSide()) {
            return;
        }

        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockEntity blockEntity = event.getLevel().getBlockEntity(event.getPos());
        if (!(blockEntity instanceof BaseContainerBlockEntity)) {
            return;
        }

        // Convert block entity to LocationData using helper
        LocationData location = NeoForgeLocationConverter.toLocationData(blockEntity, serverLevel);

        // Remove from vector storage index
        vectorStorage.delete(location)
            .exceptionally(ex -> {
                // Log error if needed - avoid sending to player since this is server-side only
                System.err.println("Failed to remove container from index at " + location + ": " + ex.getMessage());
                return null;
            });
    }
}
