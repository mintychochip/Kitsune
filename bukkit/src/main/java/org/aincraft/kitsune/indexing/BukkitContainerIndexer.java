package org.aincraft.kitsune.indexing;

import org.aincraft.kitsune.api.ContainerLocations;
import org.aincraft.kitsune.api.Location;
import org.aincraft.kitsune.config.KitsuneConfig;
import org.aincraft.kitsune.embedding.EmbeddingService;
import org.aincraft.kitsune.logging.ChestFindLogger;
import org.aincraft.kitsune.storage.VectorStorage;
import org.aincraft.kitsune.util.LocationConverter;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Bukkit-specific container indexer that extends the core ContainerIndexer.
 * Provides platform-specific methods for Bukkit Location handling and radius scanning.
 */
public class BukkitContainerIndexer extends ContainerIndexer {
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private final Map<org.bukkit.Location, ScheduledFuture<?>> pendingLocationIndexes = new HashMap<>();
    private final int debounceDelayMs;

    public BukkitContainerIndexer(ChestFindLogger logger, EmbeddingService embeddingService,
                                   VectorStorage vectorStorage, KitsuneConfig config) {
        super(logger, embeddingService, vectorStorage, config);
        this.debounceDelayMs = config.getDebounceDelayMs();
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
                return new ArrayList<ScheduledFuture<?>>();
            }

            List<ScheduledFuture<?>> scheduledTasks = new ArrayList<>();
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
            CompletableFuture<?>[] taskFutures = scheduledTasks.stream()
                .map(this::toCompletableFuture)
                .toArray(CompletableFuture[]::new);

            return CompletableFuture.allOf(taskFutures)
                .thenApply(v -> scheduledTasks.size());
        });
    }

    /**
     * Schedules indexing and tracks the scheduled future for a Bukkit location.
     *
     * @param location the Bukkit location
     * @param items the items to index
     * @param taskList the list to add the scheduled future to
     */
    private void scheduleIndexAndTrack(org.bukkit.Location location, ItemStack[] items, List<ScheduledFuture<?>> taskList) {
        if (location.getWorld() == null) {
            return;
        }

        synchronized (pendingLocationIndexes) {
            ScheduledFuture<?> existing = pendingLocationIndexes.get(location);
            if (existing != null && !existing.isDone()) {
                existing.cancel(false);
            }

            ScheduledFuture<?> future = executor.schedule(
                () -> performIndex(location, items),
                debounceDelayMs,
                TimeUnit.MILLISECONDS
            );

            pendingLocationIndexes.put(location, future);
            taskList.add(future);
        }
    }

    /**
     * Performs indexing from a Bukkit Location by converting to LocationData.
     *
     * @param location the Bukkit location
     * @param items the Bukkit items to index
     */
    private void performIndex(org.bukkit.Location location, ItemStack[] items) {
        Location locationData = LocationConverter.toLocationData(location);
        List<SerializedItem> serializedItems = ItemSerializer.serializeItemsToChunks(items);
        performIndex(locationData, serializedItems);
    }

    /**
     * Converts a ScheduledFuture to a CompletableFuture for composition.
     *
     * @param scheduledFuture the scheduled future
     * @return a completable future
     */
    private CompletableFuture<Void> toCompletableFuture(ScheduledFuture<?> scheduledFuture) {
        CompletableFuture<Void> cf = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                scheduledFuture.get();
                cf.complete(null);
            } catch (Exception e) {
                cf.completeExceptionally(e);
            }
        });
        return cf;
    }

    @Override
    public void shutdown() {
        synchronized (pendingLocationIndexes) {
            for (ScheduledFuture<?> future : pendingLocationIndexes.values()) {
                if (future != null && !future.isDone()) {
                    future.cancel(false);
                }
            }
            pendingLocationIndexes.clear();
        }
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
