package org.aincraft.kitsune.indexing;

import org.aincraft.kitsune.api.ContainerLocations;
import org.aincraft.kitsune.api.LocationData;
import org.aincraft.kitsune.config.KitsuneConfig;
import org.aincraft.kitsune.embedding.EmbeddingService;
import org.aincraft.kitsune.logging.ChestFindLogger;
import org.aincraft.kitsune.model.ContainerChunk;
import org.aincraft.kitsune.storage.VectorStorage;

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
 * Core platform-agnostic container indexing service.
 * Handles scheduling, embedding generation, and storage of container item indices.
 * Uses LocationData for all location tracking (no Bukkit dependencies).
 *
 * Platform-specific functionality like radius scanning is delegated to implementations
 * via the ContainerScanner interface.
 */
public class ContainerIndexer {
    private final ChestFindLogger logger;
    private final EmbeddingService embeddingService;
    private final VectorStorage vectorStorage;
    private final KitsuneConfig config;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private final Map<LocationData, ScheduledFuture<?>> pendingIndexes = new HashMap<>();
    private final int debounceDelayMs;

    public ContainerIndexer(ChestFindLogger logger, EmbeddingService embeddingService,
                          VectorStorage vectorStorage, KitsuneConfig config) {
        this.logger = logger;
        this.embeddingService = embeddingService;
        this.vectorStorage = vectorStorage;
        this.config = config;
        this.debounceDelayMs = config.getDebounceDelayMs();
    }

    /**
     * Schedules indexing of a multi-block or single-block container.
     * Registers position mappings first, then schedules embedding and indexing.
     *
     * @param locations the container locations (primary and all positions)
     * @param serializedItems the serialized items to index
     */
    public void scheduleIndex(ContainerLocations locations, List<SerializedItem> serializedItems) {
        LocationData primaryLocation = locations.primaryLocation();

        // Register container position mappings (async)
        vectorStorage.registerContainerPositions(locations).exceptionally(ex -> {
            logger.log(ChestFindLogger.LogLevel.WARNING,
                "Failed to register container positions at " + primaryLocation, ex);
            return null;
        });

        synchronized (pendingIndexes) {
            ScheduledFuture<?> existing = pendingIndexes.get(primaryLocation);
            if (existing != null && !existing.isDone()) {
                existing.cancel(false);
            }

            ScheduledFuture<?> future = executor.schedule(
                () -> performIndex(primaryLocation, serializedItems),
                debounceDelayMs,
                TimeUnit.MILLISECONDS
            );

            pendingIndexes.put(primaryLocation, future);
        }
    }

    /**
     * Performs the actual indexing of container items.
     * Generates embeddings and stores in vector database.
     *
     * @param locationData the location of the container
     * @param serializedItems the serialized items to index
     */
    protected void performIndex(LocationData locationData, List<SerializedItem> serializedItems) {

        if (serializedItems.isEmpty()) {
            vectorStorage.delete(locationData).exceptionally(ex -> {
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
            logger.info("Indexing chunk " + chunkIndex + " at " + locationData +
                ": embedding=\"" + embeddingText + "\"");

            // Embed the lowercase text, store original in content_text for display
            CompletableFuture<ContainerChunk> chunkFuture = embeddingService.embed(embeddingText, "RETRIEVAL_DOCUMENT")
                .thenApply(embedding -> new ContainerChunk(
                    locationData,
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
                    pendingIndexes.remove(locationData);
                }
            })
            .exceptionally(ex -> {
                logger.log(ChestFindLogger.LogLevel.WARNING, "Failed to index container at " + locationData, ex);
                return null;
            });
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
