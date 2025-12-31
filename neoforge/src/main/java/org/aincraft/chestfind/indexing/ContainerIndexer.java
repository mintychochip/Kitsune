package org.aincraft.chestfind.indexing;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import org.aincraft.chestfind.api.LocationData;
import org.aincraft.chestfind.config.ChestFindConfig;
import org.aincraft.chestfind.embedding.EmbeddingService;
import org.aincraft.chestfind.logging.ChestFindLogger;
import org.aincraft.chestfind.model.ContainerChunk;
import org.aincraft.chestfind.storage.VectorStorage;
import org.aincraft.chestfind.util.LocationConverter;

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
 * Manages debounced indexing of container contents.
 * Prevents excessive indexing during rapid item changes by batching updates.
 */
public class ContainerIndexer {
    private final ChestFindLogger logger;
    private final EmbeddingService embeddingService;
    private final VectorStorage vectorStorage;
    private final ChestFindConfig config;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private final Map<String, ScheduledFuture<?>> pendingIndexes = new HashMap<>();
    private final int debounceDelayMs;

    public ContainerIndexer(ChestFindLogger logger, EmbeddingService embeddingService,
                          VectorStorage vectorStorage, ChestFindConfig config) {
        this.logger = logger;
        this.embeddingService = embeddingService;
        this.vectorStorage = vectorStorage;
        this.config = config;
        this.debounceDelayMs = config.getDebounceDelayMs();
    }

    /**
     * Schedule indexing of a container. Cancels any pending index for this location.
     *
     * @param pos the block position
     * @param level the level/world
     * @param items the container's items
     */
    public void scheduleIndex(BlockPos pos, Level level, ItemStack[] items) {
        if (level == null) {
            return;
        }

        LocationData location = LocationConverter.toLocationData(pos, level);
        String locationKey = location.toString();

        synchronized (pendingIndexes) {
            ScheduledFuture<?> existing = pendingIndexes.get(locationKey);
            if (existing != null && !existing.isDone()) {
                existing.cancel(false);
            }

            ScheduledFuture<?> future = executor.schedule(
                () -> performIndex(location, items),
                debounceDelayMs,
                TimeUnit.MILLISECONDS
            );

            pendingIndexes.put(locationKey, future);
        }
    }

    /**
     * Perform the actual indexing operation.
     */
    private void performIndex(LocationData location, ItemStack[] items) {
        List<SerializedItem> serializedItems = ItemSerializer.serializeItemsToChunks(items);

        if (serializedItems.isEmpty()) {
            // Delete empty container from index
            vectorStorage.delete(location).exceptionally(ex -> {
                logger.log(ChestFindLogger.LogLevel.WARNING, "Failed to delete empty container", ex);
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
            logger.info("Indexing chunk " + chunkIndex + " at " + location +
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
                        logger.log(ChestFindLogger.LogLevel.WARNING, "Failed to get chunk embedding", e);
                    }
                }
                return chunks;
            })
            .thenCompose(chunks -> vectorStorage.indexChunks(chunks))
            .thenRun(() -> {
                synchronized (pendingIndexes) {
                    pendingIndexes.remove(location.toString());
                }
            })
            .exceptionally(ex -> {
                logger.log(ChestFindLogger.LogLevel.WARNING, "Failed to index container at " + location, ex);
                return null;
            });
    }

    /**
     * Reindex all containers within a given radius from a center location.
     * This is a placeholder implementation that returns 0 as the method requires
     * access to loaded chunks and block entities which are server-side only.
     *
     * @param centerLocation the center location
     * @param radius the radius in blocks
     * @return CompletableFuture with count of reindexed containers
     */
    public CompletableFuture<Integer> reindexRadius(LocationData centerLocation, int radius) {
        // TODO: Implement full reindexRadius that:
        // 1. Iterates through blocks in radius
        // 2. Finds all container block entities
        // 3. Reindexes their contents
        // For now, return 0 as a placeholder
        return CompletableFuture.completedFuture(0);
    }

    /**
     * Shutdown the indexer and cancel pending operations.
     */
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
            Thread.currentThread().interrupt();
        }
    }
}
