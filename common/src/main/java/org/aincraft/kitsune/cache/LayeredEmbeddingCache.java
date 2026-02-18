package org.aincraft.kitsune.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Two-tier embedding cache with Caffeine L1 (in-memory) + SQLite L2 (persistent).
 * Uses shared DataSource from ContainerStorage.
 */
public final class LayeredEmbeddingCache implements EmbeddingCache {
    private static final int DEFAULT_MAX_L1_SIZE = 10000;
    private final Logger logger;
    private final DataSource dataSource;
    private final com.github.benmanes.caffeine.cache.Cache<String, float[]> l1Cache;
    private final ExecutorService executor;

    public LayeredEmbeddingCache(DataSource dataSource, Logger logger) {
        this(dataSource, logger, DEFAULT_MAX_L1_SIZE);
    }

    public LayeredEmbeddingCache(DataSource dataSource, Logger logger, int maxL1Size) {
        this.dataSource = dataSource;
        this.logger = logger;
        this.l1Cache = Caffeine.newBuilder()
            .maximumSize(maxL1Size)
            .build();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "embedding-cache-executor");
            t.setDaemon(true);
            return t;
        });
    }

    public void initialize() {
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS embedding_cache (
                    content_hash TEXT PRIMARY KEY,
                    embedding BLOB NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """);
            conn.createStatement().execute(
                "CREATE INDEX IF NOT EXISTS idx_cache_created ON embedding_cache(created_at)"
            );
            logger.info("Embedding cache initialized");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize embedding cache", e);
            throw new RuntimeException("Embedding cache initialization failed", e);
        }
    }

    @Override
    public CompletableFuture<Void> initializeAsync() {
        return CompletableFuture.runAsync(this::initialize, executor);
    }

    @Override
    public CompletableFuture<Optional<float[]>> get(String contentHash) {
        // Check L1 first (sync)
        float[] cached = l1Cache.getIfPresent(contentHash);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }

        // Check L2 (async)
        return CompletableFuture.supplyAsync(() -> getFromL2(contentHash), executor);
    }

    private Optional<float[]> getFromL2(String contentHash) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT embedding FROM embedding_cache WHERE content_hash = ?"
             )) {
            stmt.setString(1, contentHash);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    byte[] blob = rs.getBytes("embedding");
                    float[] embedding = deserializeEmbedding(blob);
                    l1Cache.put(contentHash, embedding);
                    return Optional.of(embedding);
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to get embedding from cache", e);
        }
        return Optional.empty();
    }

    @Override
    public CompletableFuture<Void> put(String contentHash, float[] embedding) {
        l1Cache.put(contentHash, embedding);
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "INSERT OR REPLACE INTO embedding_cache (content_hash, embedding, created_at) VALUES (?, ?, ?)"
                 )) {
                stmt.setString(1, contentHash);
                stmt.setBytes(2, serializeEmbedding(embedding));
                stmt.setLong(3, System.currentTimeMillis());
                stmt.executeUpdate();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to cache embedding", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> clear() {
        l1Cache.invalidateAll();
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.createStatement().execute("DELETE FROM embedding_cache");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to clear cache", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Long> size() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 ResultSet rs = conn.createStatement().executeQuery(
                     "SELECT COUNT(*) FROM embedding_cache"
                 )) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to get cache size", e);
            }
            return 0L;
        }, executor);
    }

    @Override
    public void shutdown() {
        l1Cache.invalidateAll();
        executor.shutdown();
        // DataSource lifecycle managed externally
    }

    private byte[] serializeEmbedding(float[] embedding) {
        ByteBuffer buffer = ByteBuffer.allocate(embedding.length * Float.BYTES)
            .order(ByteOrder.LITTLE_ENDIAN);
        buffer.asFloatBuffer().put(embedding);
        return buffer.array();
    }

    private float[] deserializeEmbedding(byte[] bytes) {
        float[] embedding = new float[bytes.length / Float.BYTES];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer().get(embedding);
        return embedding;
    }
}
