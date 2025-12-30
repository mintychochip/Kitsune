package org.aincraft.chestfind.indexing;

import org.aincraft.chestfind.ChestFindPlugin;
import org.aincraft.chestfind.config.ChestFindConfig;
import org.aincraft.chestfind.embedding.EmbeddingService;
import org.aincraft.chestfind.model.ContainerChunk;
import org.aincraft.chestfind.model.ContainerDocument;
import org.aincraft.chestfind.storage.VectorStorage;
import org.bukkit.Location;
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
import java.util.logging.Level;

public class ContainerIndexer {
    private final ChestFindPlugin plugin;
    private final EmbeddingService embeddingService;
    private final VectorStorage vectorStorage;
    private final ChestFindConfig config;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private final Map<Location, ScheduledFuture<?>> pendingIndexes = new HashMap<>();
    private final int debounceDelayMs;

    public ContainerIndexer(ChestFindPlugin plugin, EmbeddingService embeddingService,
                          VectorStorage vectorStorage, ChestFindConfig config) {
        this.plugin = plugin;
        this.embeddingService = embeddingService;
        this.vectorStorage = vectorStorage;
        this.config = config;
        this.debounceDelayMs = config.getDebounceDelayMs();
    }

    public void scheduleIndex(Location location, ItemStack[] items) {
        if (location.getWorld() == null) {
            return;
        }

        synchronized (pendingIndexes) {
            ScheduledFuture<?> existing = pendingIndexes.get(location);
            if (existing != null && !existing.isDone()) {
                existing.cancel(false);
            }

            ScheduledFuture<?> future = executor.schedule(
                () -> performIndex(location, items),
                debounceDelayMs,
                TimeUnit.MILLISECONDS
            );

            pendingIndexes.put(location, future);
        }
    }

    private void performIndex(Location location, ItemStack[] items) {
        List<SerializedItem> serializedItems = ItemSerializer.serializeItemsToChunks(items);

        if (serializedItems.isEmpty()) {
            vectorStorage.delete(location).exceptionally(ex -> {
                plugin.getLogger().log(Level.WARNING, "Failed to delete empty container", ex);
                return null;
            });
            return;
        }

        // Create embedding for each chunk
        long timestamp = System.currentTimeMillis();
        List<CompletableFuture<ContainerChunk>> chunkFutures = new ArrayList<>();

        for (int i = 0; i < serializedItems.size(); i++) {
            final int chunkIndex = i;
            final SerializedItem serialized = serializedItems.get(i);

            // Normalize to lowercase for better embedding consistency
            String embeddingText = serialized.embeddingText().toLowerCase();

            // Log what we're embedding
            plugin.getLogger().info("Indexing chunk " + chunkIndex + " at " + location +
                ": embedding=\"" + embeddingText + "\"");

            // Embed the lowercase text, store original in content_text for display
            CompletableFuture<ContainerChunk> chunkFuture = embeddingService.embed(embeddingText, "RETRIEVAL_DOCUMENT")
                .thenApply(embedding -> new ContainerChunk(
                    location,
                    chunkIndex,
                    embeddingText,  // Store lowercase text that was embedded
                    embedding,
                    timestamp
                ));

            chunkFutures.add(chunkFuture);
        }

        // Wait for all chunks to be embedded, then index them
        CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                List<ContainerChunk> chunks = new ArrayList<>();
                for (CompletableFuture<ContainerChunk> future : chunkFutures) {
                    try {
                        chunks.add(future.get());
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Failed to get chunk embedding", e);
                    }
                }
                return chunks;
            })
            .thenCompose(chunks -> vectorStorage.indexChunks(chunks))
            .thenRun(() -> {
                synchronized (pendingIndexes) {
                    pendingIndexes.remove(location);
                }
            })
            .exceptionally(ex -> {
                plugin.getLogger().log(Level.WARNING, "Failed to index container at " + location, ex);
                return null;
            });
    }

    public CompletableFuture<Integer> reindexRadius(Location center, int radius) {
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
                        Location loc = center.clone().add(dx, dy, dz);
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

    private void scheduleIndexAndTrack(Location location, ItemStack[] items, List<ScheduledFuture<?>> taskList) {
        if (location.getWorld() == null) {
            return;
        }

        synchronized (pendingIndexes) {
            ScheduledFuture<?> existing = pendingIndexes.get(location);
            if (existing != null && !existing.isDone()) {
                existing.cancel(false);
            }

            ScheduledFuture<?> future = executor.schedule(
                () -> performIndex(location, items),
                debounceDelayMs,
                TimeUnit.MILLISECONDS
            );

            pendingIndexes.put(location, future);
            taskList.add(future);
        }
    }

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

    public void shutdown() {
        synchronized (pendingIndexes) {
            for (ScheduledFuture<?> future : pendingIndexes.values()) {
                if (future != null && !future.isDone()) {
                    future.cancel(false);
                }
            }
            pendingIndexes.clear();
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
