package org.aincraft.kitsune.cache;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Cache for item embeddings.
 * Keys are content hash codes from ItemContext.contentHash().
 * Values are embedding vectors.
 */
public interface EmbeddingCache {
    /**
     * Get cached embedding for an item hash.
     *
     * @param contentHash The content hash from ItemContext.contentHash()
     * @return The cached embedding, or empty if not cached
     */
    CompletableFuture<Optional<float[]>> get(String contentHash);

    /**
     * Store embedding for an item hash.
     *
     * @param contentHash The content hash from ItemContext.contentHash()
     * @param embedding The embedding vector to cache
     */
    CompletableFuture<Void> put(String contentHash, float[] embedding);

    /**
     * Initialize the cache.
     */
    CompletableFuture<Void> initialize();

    /**
     * Shutdown and release resources.
     */
    void shutdown();

    /**
     * Clear all cached embeddings.
     */
    CompletableFuture<Void> clear();

    /**
     * Get cache statistics.
     *
     * @return Number of cached embeddings
     */
    CompletableFuture<Long> size();
}
