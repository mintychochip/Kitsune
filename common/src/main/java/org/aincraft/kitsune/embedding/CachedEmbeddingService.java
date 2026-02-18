package org.aincraft.kitsune.embedding;

import org.aincraft.kitsune.Platform;
import org.aincraft.kitsune.cache.EmbeddingCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Decorator that wraps an EmbeddingService and caches embeddings.
 * Uses batch cache operations for improved throughput.
 */
public final class CachedEmbeddingService implements EmbeddingService {

    private final Platform platform;
    private final EmbeddingService delegate;
    private final EmbeddingCache cache;

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
        long key = toCacheKey(text);

        return cache.get(key)
                .thenCompose(cachedEmbedding -> {
                    if (cachedEmbedding.isPresent()) {
                        float[] embedding = cachedEmbedding.get();
                        // Validate the cached embedding
                        if (embedding != null && embedding.length > 0) {
                            platform.getLogger().info("Embedding cache hit for: " + truncate(text, 50) + "...");
                            return CompletableFuture.completedFuture(embedding);
                        } else {
                            platform.getLogger().warning("Cached embedding was null or empty for: " + truncate(text, 50) + ", regenerating...");
                        }
                    }

                    platform.getLogger().info("Generating new embedding for: " + truncate(text, 50) + "...");
                    return delegate.embed(text, taskType)
                            .thenCompose(embedding -> {
                                if (embedding == null || embedding.length == 0) {
                                    platform.getLogger().warning("Delegate returned null/empty embedding for: " + truncate(text, 50));
                                    return CompletableFuture.completedFuture(embedding);
                                }
                                return cache.put(key, embedding)
                                        .thenApply(v -> embedding);
                            });
                });
    }

    @Override
    public CompletableFuture<List<float[]>> embedBatch(List<String> texts, String taskType) {
        if (texts == null || texts.isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        // Build cache keys
        List<Long> cacheKeys = texts.stream()
                .mapToLong(CachedEmbeddingService::toCacheKey)
                .boxed()
                .collect(Collectors.toList());

        // Batch cache lookup
        return cache.getAll(cacheKeys)
                .thenCompose(cachedMap -> {
                    List<Integer> missIndices = new ArrayList<>();
                    List<String> missTexts = new ArrayList<>();
                    float[][] results = new float[texts.size()][];

                    for (int i = 0; i < texts.size(); i++) {
                        float[] cached = cachedMap.get(cacheKeys.get(i));
                        if (cached != null) {
                            results[i] = cached;
                        } else {
                            missIndices.add(i);
                            missTexts.add(texts.get(i));
                        }
                    }

                    int cacheHits = texts.size() - missIndices.size();
                    platform.getLogger().info("Batch cache: " + cacheHits + " hits, " + missTexts.size() + " misses");

                    if (missTexts.isEmpty()) {
                        return CompletableFuture.completedFuture(toList(results));
                    }

                    return delegate.embedBatch(missTexts, taskType)
                            .thenCompose(missedEmbeddings -> {
                                Map<Long, float[]> toCache = new HashMap<>(missedEmbeddings.size());
                                for (int i = 0; i < missedEmbeddings.size(); i++) {
                                    int originalIndex = missIndices.get(i);
                                    float[] embedding = missedEmbeddings.get(i);
                                    results[originalIndex] = embedding;
                                    toCache.put(cacheKeys.get(originalIndex), embedding);
                                }

                                return cache.putAll(toCache)
                                        .thenApply(v -> toList(results));
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
        cache.flush().join();
        cache.shutdown();
        delegate.shutdown();
    }

    public CompletableFuture<Void> clearCache() {
        return cache.clear();
    }

    public CompletableFuture<Void> flushCache() {
        return cache.flush();
    }

    /**
     * Fast cache key: packs hashCode + length into a long.
     * No string allocation, just primitive operations.
     */
    private static long toCacheKey(String text) {
        return ((long) text.hashCode() << 32) | text.length();
    }

    private static String truncate(String s, int maxLen) {
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    private static List<float[]> toList(float[][] array) {
        List<float[]> list = new ArrayList<>(array.length);
        Collections.addAll(list, array);
        return list;
    }

    @Override
    public int getDimension() {
        return delegate.getDimension();
    }
}
