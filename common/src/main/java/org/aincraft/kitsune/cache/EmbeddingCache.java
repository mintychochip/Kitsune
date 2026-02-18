package org.aincraft.kitsune.cache;

import java.util.List;
import java.util.Map;
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
     * Get multiple cached embeddings.
     * Returns map of hash -> embedding for found entries only.
     *
     * @param contentHashes The content hashes to look up
     * @return Map of found embeddings (missing entries excluded)
     */
    CompletableFuture<Map<String, float[]>> getAll(List<String> contentHashes);

    /**
     * Store embedding for an item hash.
     *
     * @param contentHash The content hash from ItemContext.contentHash()
     * @param embedding The embedding vector to cache
     */
    CompletableFuture<Void> put(String contentHash, float[] embedding);

    /**
     * Store multiple embeddings.
     * More efficient than individual puts for bulk operations.
     *
     * @param embeddings Map of content hash to embedding
     */
    CompletableFuture<Void> putAll(Map<String, float[]> embeddings);

    /**
     * Flush any pending writes (for write-behind implementations).
     */
    CompletableFuture<Void> flush();

    /**
     * Initialize the cache.
     */
    CompletableFuture<Void> initializeAsync();

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
