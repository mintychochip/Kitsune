package org.aincraft.kitsune.embedding;

import org.aincraft.kitsune.Platform;
import org.aincraft.kitsune.cache.EmbeddingCache;

import java.util.ArrayList;
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
        String key = toCacheKey(text);

        return cache.get(key)
                .thenCompose(cachedEmbedding -> {
                    if (cachedEmbedding.isPresent()) {
                        platform.getLogger().info("Embedding cache hit for: " + truncate(text, 50) + "...");
                        return CompletableFuture.completedFuture(cachedEmbedding.get());
                    }

                    platform.getLogger().info("Generating new embedding for: " + truncate(text, 50) + "...");
                    return delegate.embed(text, taskType)
                            .thenCompose(embedding ->
                                    cache.put(key, embedding)
                                            .thenApply(v -> embedding)
                            );
                });
    }

    @Override
    public CompletableFuture<List<float[]>> embedBatch(List<String> texts, String taskType) {
        if (texts == null || texts.isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        // Build cache keys
        List<String> cacheKeys = texts.stream()
                .map(CachedEmbeddingService::toCacheKey)
                .collect(Collectors.toList());

        // Batch cache lookup
        return cache.getAll(cacheKeys)
                .thenCompose(cachedMap -> {
                    // Separate hits and misses
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

                    // If all cached, return immediately
                    if (missTexts.isEmpty()) {
                        return CompletableFuture.completedFuture(toList(results));
                    }

                    // Embed missing texts in batch
                    return delegate.embedBatch(missTexts, taskType)
                            .thenCompose(missedEmbeddings -> {
                                // Build batch put map
                                Map<String, float[]> toCache = new HashMap<>(missedEmbeddings.size());
                                for (int i = 0; i < missedEmbeddings.size(); i++) {
                                    int originalIndex = missIndices.get(i);
                                    float[] embedding = missedEmbeddings.get(i);
                                    results[originalIndex] = embedding;
                                    toCache.put(cacheKeys.get(originalIndex), embedding);
                                }

                                // Batch cache put
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
        cache.flush().join(); // Ensure all writes flushed
        cache.shutdown();
        delegate.shutdown();
    }

    public CompletableFuture<Void> clearCache() {
        return cache.clear();
    }

    public CompletableFuture<Void> flushCache() {
        return cache.flush();
    }

    private static String toCacheKey(String text) {
        return Integer.toHexString(text.hashCode()) + ":" + text.length();
    }

    private static String truncate(String s, int maxLen) {
        return s.substring(0, Math.min(maxLen, s.length()));
    }

    private static List<float[]> toList(float[][] array) {
        List<float[]> list = new ArrayList<>(array.length);
        for (float[] f : array) {
            list.add(f);
        }
        return list;
    }

    @Override
    public int getDimension() {
        return delegate.getDimension();
    }
}
