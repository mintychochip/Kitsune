package org.aincraft.kitsune.embedding;

import org.aincraft.kitsune.Platform;
import org.aincraft.kitsune.cache.EmbeddingCache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Decorator that wraps an EmbeddingService and caches embeddings.
 * Uses SHA-256 hashing to create cache keys from input text.
 */
public class CachedEmbeddingService implements EmbeddingService {

    private final Platform platform;
    private final EmbeddingService delegate;
    private final EmbeddingCache cache;

    /**
     * Creates a cached embedding service.
     *
     * @param platform The platform for logging
     * @param delegate The underlying embedding service
     * @param cache The cache to store embeddings
     */
    public CachedEmbeddingService(Platform platform, EmbeddingService delegate, EmbeddingCache cache) {
        this.platform = platform;
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public CompletableFuture<float[]> embed(String text) {
        return embed(text, null);
    }

    @Override
    public CompletableFuture<float[]> embed(String text, String taskType) {
        String cacheKey = hashText(text);

        return cache.get(cacheKey)
                .thenCompose(cachedEmbedding -> {
                    if (cachedEmbedding.isPresent()) {
                        platform.getLogger().info("Embedding cache hit for: " + text.substring(0, Math.min(50, text.length())) + "...");
                        return CompletableFuture.completedFuture(cachedEmbedding.get());
                    }

                    platform.getLogger().info("Generating new embedding for: " + text.substring(0, Math.min(50, text.length())) + "...");
                    return delegate.embed(text, taskType)
                            .thenCompose(embedding ->
                                    cache.put(cacheKey, embedding)
                                            .thenApply(v -> embedding)
                            );
                });
    }

    @Override
    public CompletableFuture<List<float[]>> embedBatch(List<String> texts, String taskType) {
        if (texts == null || texts.isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        // Check cache for all texts first
        List<String> cacheKeys = new ArrayList<>();
        for (String text : texts) {
            cacheKeys.add(hashText(text));
        }

        // Fetch cache entries for all texts
        List<CompletableFuture<Optional<float[]>>> cacheFutures = new ArrayList<>();
        for (String key : cacheKeys) {
            cacheFutures.add(cache.get(key));
        }

        // Wait for all cache lookups to complete
        return CompletableFuture.allOf(cacheFutures.toArray(new CompletableFuture[0]))
                .thenCompose(v -> {
                    // Separate hits and misses
                    List<Integer> missIndices = new ArrayList<>();
                    List<String> missTexts = new ArrayList<>();
                    float[][] results = new float[texts.size()][];
                    int cacheHits = 0;

                    for (int i = 0; i < texts.size(); i++) {
                        Optional<float[]> cached = cacheFutures.get(i).join();
                        if (cached.isPresent()) {
                            results[i] = cached.get();
                            cacheHits++;
                        } else {
                            missIndices.add(i);
                            missTexts.add(texts.get(i));
                        }
                    }

                    platform.getLogger().info("Batch cache: " + cacheHits + " hits, " + missTexts.size() + " misses out of " + texts.size());

                    // If all cached, return immediately
                    if (missTexts.isEmpty()) {
                        List<float[]> cachedResults = new ArrayList<>();
                        for (float[] result : results) {
                            cachedResults.add(result);
                        }
                        return CompletableFuture.completedFuture(cachedResults);
                    }

                    // Embed missing texts in batch
                    return delegate.embedBatch(missTexts, taskType)
                            .thenCompose(missedEmbeddings -> {
                                // Store missing embeddings in cache
                                List<CompletableFuture<Void>> putFutures = new ArrayList<>();
                                for (int i = 0; i < missedEmbeddings.size(); i++) {
                                    int originalIndex = missIndices.get(i);
                                    float[] embedding = missedEmbeddings.get(i);
                                    results[originalIndex] = embedding;
                                    putFutures.add(cache.put(cacheKeys.get(originalIndex), embedding));
                                }

                                // Wait for all cache puts to complete, then return results
                                return CompletableFuture.allOf(putFutures.toArray(new CompletableFuture[0]))
                                        .thenApply(v2 -> {
                                            List<float[]> finalResults = new ArrayList<>();
                                            for (float[] result : results) {
                                                finalResults.add(result);
                                            }
                                            return finalResults;
                                        });
                            });
                });
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return cache.initializeAsync()
                .thenCompose(v -> delegate.initialize());
    }

    @Override
    public void shutdown() {
        cache.shutdown();
        delegate.shutdown();
    }

    /**
     * Clear all cached embeddings.
     * Used by purge operations to reset the cache.
     *
     * @return CompletableFuture that completes when cache is cleared
     */
    public CompletableFuture<Void> clearCache() {
        return cache.clear();
    }

    /**
     * Hashes text using SHA-256 and returns hex string.
     *
     * @param text The text to hash
     * @return SHA-256 hash as hex string
     */
    private String hashText(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Converts bytes to hex string representation.
     *
     * @param bytes The bytes to convert
     * @return Hex string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Override
    public int getDimension() {
        return delegate.getDimension();
    }
}
