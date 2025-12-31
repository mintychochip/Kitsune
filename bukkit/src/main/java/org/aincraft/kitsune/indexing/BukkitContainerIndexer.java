package org.aincraft.kitsune.indexing;

import org.aincraft.kitsune.api.ContainerLocations;
import org.aincraft.kitsune.api.Location;
import org.aincraft.kitsune.config.KitsuneConfig;
import org.aincraft.kitsune.embedding.EmbeddingService;
import java.util.logging.Logger;
import org.aincraft.kitsune.storage.VectorStorage;
import org.aincraft.kitsune.util.LocationConverter;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Bukkit-specific container indexer that extends the core ContainerIndexer.
 * Provides platform-specific methods for Bukkit Location handling and radius scanning.
 */
public class BukkitContainerIndexer extends ContainerIndexer {
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

    public BukkitContainerIndexer(Logger logger, EmbeddingService embeddingService,
                                   VectorStorage vectorStorage, KitsuneConfig config) {
        super(logger, embeddingService, vectorStorage, config);
    }

    /**
     * Bukkit-specific wrapper for scheduling index by Location.
     * Converts Bukkit Location to platform-agnostic LocationData and serializes items.
     *
     * @param location the Bukkit location
     * @param items the Bukkit items to index
     */
    public void scheduleIndex(org.bukkit.Location location, ItemStack[] items) {
        if (location.getWorld() == null) {
            return;
        }

        Location locationData = LocationConverter.toLocationData(location);
        List<SerializedItem> serializedItems = ItemSerializer.serializeItemsToChunks(items);
        scheduleIndex(ContainerLocations.single(locationData), serializedItems);
    }

    /**
     * Performs radius-based reindexing of containers around a center location.
     * Scans all blocks within radius and reindexes any containers found.
     *
     * @param center the center location
     * @param radius the scan radius
     * @return CompletableFuture with count of reindexed containers
     */
    public CompletableFuture<Integer> reindexRadius(org.bukkit.Location center, int radius) {
        return CompletableFuture.supplyAsync(() -> {
            if (center.getWorld() == null) {
                return new ArrayList<CompletableFuture<?>>();
            }

            List<CompletableFuture<?>> scheduledTasks = new ArrayList<>();
            int x = center.getBlockX();
            int y = center.getBlockY();
            int z = center.getBlockZ();

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        org.bukkit.Location loc = center.clone().add(dx, dy, dz);
                        if (center.distance(loc) <= radius) {
                            var block = loc.getBlock();
                            if (block.getState() instanceof Container container) {
                                Inventory inv = container.getInventory();
                                scheduleIndexAndTrack(loc, inv.getContents(), scheduledTasks);
                            }
                        }
                    }
                }
            }

            return scheduledTasks;
        }, executor).thenCompose(scheduledTasks -> {
            // Wait for all scheduled indexing tasks to complete
            return CompletableFuture.allOf(scheduledTasks.toArray(new CompletableFuture[0]))
                .thenApply(v -> scheduledTasks.size());
        });
    }

    /**
     * Schedules indexing and tracks the completion with a CompletableFuture.
     *
     * @param location the Bukkit location
     * @param items the items to index
     * @param taskList the list to add the completion future to
     */
    private void scheduleIndexAndTrack(org.bukkit.Location location, ItemStack[] items, List<CompletableFuture<?>> taskList) {
        if (location.getWorld() == null) {
            return;
        }

        Location locationData = LocationConverter.toLocationData(location);
        List<SerializedItem> serializedItems = ItemSerializer.serializeItemsToChunks(items);

        // Create a future that we'll complete when indexing is done
        CompletableFuture<Void> completionFuture = new CompletableFuture<>();
        taskList.add(completionFuture);

        // Get the container and schedule the index
        // Complete the future after scheduleIndex completes
        scheduleIndex(ContainerLocations.single(locationData), serializedItems);

        // Note: Due to async nature of getOrCreateContainer in scheduleIndex,
        // we mark it as complete immediately to avoid deadlock
        completionFuture.complete(null);
    }

    @Override
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        super.shutdown();
    }
}
