package org.aincraft.kitsune.embedding;

import org.aincraft.kitsune.cache.EmbeddingCache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;

/**
 * Decorator that wraps an EmbeddingService and caches embeddings.
 * Uses SHA-256 hashing to create cache keys from input text.
 */
public class CachedEmbeddingService implements EmbeddingService {

    private final EmbeddingService delegate;
    private final EmbeddingCache cache;

    /**
     * Creates a cached embedding service.
     *
     * @param delegate The underlying embedding service
     * @param cache The cache to store embeddings
     */
    public CachedEmbeddingService(EmbeddingService delegate, EmbeddingCache cache) {
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
                        return CompletableFuture.completedFuture(cachedEmbedding.get());
                    }

                    return delegate.embed(text, taskType)
                            .thenCompose(embedding ->
                                    cache.put(cacheKey, embedding)
                                            .thenApply(v -> embedding)
                            );
                });
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return cache.initialize()
                .thenCompose(v -> delegate.initialize());
    }

    @Override
    public void shutdown() {
        cache.shutdown();
        delegate.shutdown();
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
}
