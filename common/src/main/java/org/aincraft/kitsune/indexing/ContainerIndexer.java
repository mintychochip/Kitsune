package org.aincraft.kitsune.indexing;

import org.aincraft.kitsune.api.ContainerLocations;
import org.aincraft.kitsune.Location;
import org.aincraft.kitsune.api.indexing.SerializedItem;
import org.aincraft.kitsune.api.model.ContainerPath;
import org.aincraft.kitsune.config.KitsuneConfig;
import org.aincraft.kitsune.embedding.EmbeddingService;
import org.aincraft.kitsune.model.ContainerChunk;
import org.aincraft.kitsune.storage.KitsuneStorage;
import org.aincraft.kitsune.util.ItemDataExtractor;

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
import java.util.logging.Logger;

/**
 * Core platform-agnostic container indexing service.
 * Handles scheduling, embedding generation, and storage of container item indices.
 * Uses LocationData for all location tracking (no Bukkit dependencies).
 *
 * Platform-specific functionality like radius scanning is delegated to implementations
 * via the ContainerScanner interface.
 */
public class ContainerIndexer {
    protected final Logger logger;
    protected final EmbeddingService embeddingService;
    protected final KitsuneStorage storage;
    protected final KitsuneConfig config;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private final Map<Location, ScheduledFuture<?>> pendingIndexes = new HashMap<>();
    private final int debounceDelayMs;

    public ContainerIndexer(Logger logger, EmbeddingService embeddingService,
                          KitsuneStorage storage, KitsuneConfig config) {
        this.logger = logger;
        this.embeddingService = embeddingService;
        this.storage = storage;
        this.config = config;
        this.debounceDelayMs = config.indexing().debounceDelayMs();
    }

    /**
     * Schedules indexing of a multi-block or single-block container.
     * Gets or creates the container first, then schedules embedding and indexing.
     *
     * @param locations the container locations (primary and all positions)
     * @param serializedItems the serialized items to index
     */
    public void scheduleIndex(ContainerLocations locations, List<SerializedItem> serializedItems) {
        Location primaryLocation = locations.primaryLocation();

        // Get or create container (Phase 2-3 container management)
        storage.getOrCreateContainer(locations).thenAccept(containerId -> {
            synchronized (pendingIndexes) {
                ScheduledFuture<?> existing = pendingIndexes.get(primaryLocation);
                if (existing != null && !existing.isDone()) {
                    existing.cancel(false);
                }

                ScheduledFuture<?> future = executor.schedule(
                    () -> performIndex(containerId, serializedItems),
                    debounceDelayMs,
                    TimeUnit.MILLISECONDS
                );

                pendingIndexes.put(primaryLocation, future);
            }
        }).exceptionally(ex -> {
            logger.log(Level.WARNING,
                "Failed to get or create container at " + primaryLocation, ex);
            return null;
        });
    }

    /**
     * Performs the actual indexing of container items.
     * Generates embeddings and stores in vector database using the container UUID.
     *
     * @param containerId the UUID of the container (from container management)
     * @param serializedItems the serialized items to index
     */
    protected void performIndex(java.util.UUID containerId, List<SerializedItem> serializedItems) {
        // Find and remove the future from pendingIndexes to prevent memory leak
        synchronized (pendingIndexes) {
            pendingIndexes.values().removeIf(ScheduledFuture::isDone);
        }

        if (serializedItems.isEmpty()) {
            storage.deleteContainer(containerId).exceptionally(ex -> {
                logger.log(Level.WARNING, "Failed to delete empty container", ex);
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

            // Log what we're embedding (DIAGNOSTIC: Full embedding text)
            logger.info("=== DIAGNOSTIC: Chunk " + chunkIndex + " ===");
            logger.info("Container: " + containerId);
            logger.info("Embedding text: \"" + embeddingText + "\"");
            logger.info("Embedding text length: " + embeddingText.length() + " chars, " + embeddingText.split("\\s+").length + " tokens");

            // Extract container path from JSON
            ContainerPath containerPath = ItemDataExtractor.extractContainerPath(serialized.storageJson(), logger);

            // Embed the lowercase text, store JSON with display_name for retrieval
            CompletableFuture<ContainerChunk> chunkFuture = embeddingService.embed(embeddingText, "RETRIEVAL_DOCUMENT")
                .thenApply(embedding -> {
                    // DIAGNOSTIC: Log embedding vector values
                    logEmbeddingDiagnostics(chunkIndex, embeddingText, embedding);
                    return new ContainerChunk(
                        containerId,
                        chunkIndex,
                        serialized.storageJson(),  // Store JSON with display_name for display
                        embedding,
                        timestamp,
                        containerPath  // Add this parameter
                    );
                });

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
                        logger.log(Level.WARNING, "Failed to get chunk embedding", e);
                    }
                }
                return chunks;
            })
            .thenCompose(chunks -> storage.indexChunks(containerId, chunks))
            .exceptionally(ex -> {
                logger.log(Level.WARNING, "Failed to index container " + containerId, ex);
                return null;
            });
    }

    /**
     * Logs diagnostic information about embeddings to help identify issues.
     * Logs the first 5 embedding vector values and overall statistics.
     *
     * @param chunkIndex index of the chunk being embedded
     * @param embeddingText the text that was embedded
     * @param embedding the resulting embedding vector
     */
    protected void logEmbeddingDiagnostics(int chunkIndex, String embeddingText, float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            logger.warning("DIAGNOSTIC: Chunk " + chunkIndex + " has null or empty embedding!");
            return;
        }

        // Log first 5 values
        StringBuilder firstValues = new StringBuilder();
        int limit = Math.min(5, embedding.length);
        for (int i = 0; i < limit; i++) {
            if (i > 0) firstValues.append(", ");
            firstValues.append(String.format("%.6f", embedding[i]));
        }
        logger.info("First 5 embedding values: [" + firstValues + "]");

        // Calculate and log embedding statistics
        float min = embedding[0];
        float max = embedding[0];
        float sum = 0;
        float sumSquares = 0;

        for (float v : embedding) {
            min = Math.min(min, v);
            max = Math.max(max, v);
            sum += v;
            sumSquares += v * v;
        }

        float mean = sum / embedding.length;
        float variance = (sumSquares / embedding.length) - (mean * mean);
        float stdDev = (float) Math.sqrt(Math.max(0, variance));
        float magnitude = (float) Math.sqrt(sumSquares);

        logger.info("Embedding stats: dim=" + embedding.length +
            ", min=" + String.format("%.6f", min) +
            ", max=" + String.format("%.6f", max) +
            ", mean=" + String.format("%.6f", mean) +
            ", stdDev=" + String.format("%.6f", stdDev) +
            ", magnitude=" + String.format("%.6f", magnitude));

        // Compare with previous embedding if available
        if (previousEmbeddingText != null && previousEmbedding != null) {
            float similarity = calculateCosineSimilarity(embedding, previousEmbedding);
            logger.info("Similarity to previous item: " + String.format("%.4f", similarity) +
                " (prev: \"" + previousEmbeddingText + "\")");
        }

        // Store for next comparison
        previousEmbeddingText = embeddingText;
        previousEmbedding = embedding.clone();
    }

    /**
     * Calculates cosine similarity between two embedding vectors.
     */
    protected float calculateCosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1.length != vec2.length) {
            return 0;
        }

        float dotProduct = 0;
        float mag1 = 0;
        float mag2 = 0;

        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            mag1 += vec1[i] * vec1[i];
            mag2 += vec2[i] * vec2[i];
        }

        mag1 = (float) Math.sqrt(mag1);
        mag2 = (float) Math.sqrt(mag2);

        if (mag1 == 0 || mag2 == 0) {
            return 0;
        }

        return dotProduct / (mag1 * mag2);
    }

    // Storage for comparing consecutive embeddings
    private String previousEmbeddingText = null;
    private float[] previousEmbedding = null;

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
