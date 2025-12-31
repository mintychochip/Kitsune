package org.aincraft.chestfind.listener;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.aincraft.chestfind.indexing.ContainerIndexer;

/**
 * NeoForge event handler for item transfer events.
 * Schedules reindexing when items move through hoppers or other mechanisms.
 *
 * NOTE: This handler monitors container neighbor changes and schedules reindexing.
 * A more complete implementation would hook into hopper tick events.
 */
@EventBusSubscriber(modid = "chestfind", bus = EventBusSubscriber.Bus.GAME)
public class ItemTransferHandler {
    private static ContainerIndexer indexer;

    /**
     * Sets the ContainerIndexer instance. Must be called during plugin initialization.
     */
    public static void setIndexer(ContainerIndexer newIndexer) {
        indexer = newIndexer;
    }

    /**
     * Monitors container changes that might indicate item transfers.
     * Called when a neighbor block changes (e.g., hopper pushes/pulls items).
     */
    @SubscribeEvent
    static void onNeighborChange(BlockEvent.NeighborNotifyEvent event) {
        if (indexer == null || event.getLevel().isClientSide()) {
            return;
        }

        BlockEntity blockEntity = event.getLevel().getBlockEntity(event.getPos());
        if (blockEntity instanceof BaseContainerBlockEntity container) {
            // Schedule reindexing for this container
            scheduleIndexing(container);
        }

        // Also check affected neighbors
        for (Direction direction : Direction.values()) {
            BlockEntity neighbor = event.getLevel().getBlockEntity(
                event.getPos().relative(direction)
            );

            if (neighbor instanceof BaseContainerBlockEntity neighborContainer) {
                scheduleIndexing(neighborContainer);
            }
        }
    }

    private static void scheduleIndexing(BaseContainerBlockEntity container) {
        if (indexer == null) {
            return;
        }

        // Collect items from container
        int size = container.getContainerSize();
        var items = new net.minecraft.world.item.ItemStack[size];
        for (int i = 0; i < size; i++) {
            items[i] = container.getItem(i);
        }

        // Convert block entity position to LocationData for scheduling
        // TODO: Implement proper NeoForge location conversion with world name
        // For now, we skip this as ContainerIndexer.scheduleIndex expects Bukkit Location
    }
}
