package org.aincraft.kitsune.indexing;

import org.aincraft.kitsune.api.ContainerLocations;
import org.aincraft.kitsune.Location;
import org.aincraft.kitsune.api.indexing.SerializedItem;
import org.aincraft.kitsune.config.KitsuneConfig;
import org.aincraft.kitsune.embedding.EmbeddingService;
import java.util.logging.Logger;
import org.aincraft.kitsune.serialization.BukkitItemSerializer;
import org.aincraft.kitsune.storage.KitsuneStorage;
import org.aincraft.kitsune.util.BukkitLocationFactory;
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
    private final BukkitItemSerializer itemSerializer;

    public BukkitContainerIndexer(Logger logger, EmbeddingService embeddingService,
                                   KitsuneStorage storage, KitsuneConfig config,
                                   BukkitItemSerializer itemSerializer) {
        super(logger, embeddingService, storage, config);
        this.itemSerializer = itemSerializer;
    }

    /**
     * Bukkit-specific wrapper for scheduling index by ContainerLocations.
     * Properly handles multi-block containers like double chests.
     *
     * @param locations the container locations (supports multi-block)
     * @param items the Bukkit items to index
     */
    public void scheduleIndex(ContainerLocations locations, ItemStack[] items) {
        List<SerializedItem> serializedItems = itemSerializer.serializeItemsToChunks(items);
        scheduleIndex(locations, serializedItems);
    }

    /**
     * Bukkit-specific wrapper for scheduling index by single Location.
     * For single-block containers only. Use scheduleIndex(ContainerLocations, ItemStack[])
     * for multi-block containers like double chests.
     *
     * @param location the Bukkit location
     * @param items the Bukkit items to index
     */
    public void scheduleIndex(org.bukkit.Location location, ItemStack[] items) {
        if (location.getWorld() == null) {
            return;
        }

        Location locationData = BukkitLocationFactory.toLocationData(location);
        List<SerializedItem> serializedItems = itemSerializer.serializeItemsToChunks(items);
        scheduleIndex(ContainerLocations.single(locationData), serializedItems);
    }

    /**
     * Performs radius-based reindexing of containers around a center location.
     * Uses R-tree spatial index for O(log n) lookup instead of O(n^3) block iteration.
     *
     * @param center the center location
     * @param radius the scan radius
     * @return CompletableFuture with count of reindexed containers
     */
    public CompletableFuture<Integer> reindexRadius(org.bukkit.Location center, int radius) {
        if (center.getWorld() == null) {
            return CompletableFuture.completedFuture(0);
        }

        String worldName = center.getWorld().getName();
        int x = center.getBlockX();
        int y = center.getBlockY();
        int z = center.getBlockZ();

        // Use R-tree spatial index to find containers
        return storage.getContainersInRadius(worldName, x, y, z, radius)
            .thenCompose(containerIds -> {
                if (containerIds.isEmpty()) {
                    return CompletableFuture.completedFuture(0);
                }

                // For each container, get its location and reindex
                List<CompletableFuture<?>> tasks = new ArrayList<>();

                for (java.util.UUID containerId : containerIds) {
                    CompletableFuture<?> task = storage.getPrimaryLocationForContainer(containerId)
                        .thenAccept(locOpt -> {
                            locOpt.ifPresent(loc -> {
                                // Must get block on main thread
                                org.bukkit.Location bukkitLoc = BukkitLocationFactory.toBukkitLocation(loc);
                                if (bukkitLoc != null && bukkitLoc.getWorld() != null) {
                                    // Verify still in radius (R-tree uses bounding box, need exact check)
                                    if (center.distance(bukkitLoc) <= radius) {
                                        var block = bukkitLoc.getBlock();
                                        if (block.getState() instanceof Container container) {
                                            Inventory inv = container.getInventory();
                                            scheduleIndex(bukkitLoc, inv.getContents());
                                        }
                                    }
                                }
                            });
                        });
                    tasks.add(task);
                }

                return CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]))
                    .thenApply(v -> containerIds.size());
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

        Location locationData = BukkitLocationFactory.toLocationData(location);
        List<SerializedItem> serializedItems = itemSerializer.serializeItemsToChunks(items);

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
