package org.aincraft.kitsune.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Preconditions;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Two-tier embedding cache with Caffeine L1 (in-memory) + SQLite L2 (persistent).
 */
public class LayeredEmbeddingCache implements EmbeddingCache {
    private final Logger logger;
    private final String dbPath;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final com.github.benmanes.caffeine.cache.Cache<String, float[]> l1Cache;
    private final int maxMemorySize;
    private HikariDataSource dataSource;

    public LayeredEmbeddingCache(Logger logger, String dbPath, int maxMemorySize) {
        this.logger = Preconditions.checkNotNull(logger, "logger cannot be null");
        this.dbPath = Preconditions.checkNotNull(dbPath, "dbPath cannot be null");
        this.maxMemorySize = maxMemorySize;
        this.l1Cache = Caffeine.newBuilder()
            .maximumSize(maxMemorySize)
            .build();
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                Class.forName("org.sqlite.JDBC");

                Path dbFile = Paths.get(dbPath);
                Files.createDirectories(dbFile.getParent());

                HikariConfig config = new HikariConfig();
                config.setJdbcUrl("jdbc:sqlite:" + dbFile.toAbsolutePath());
                config.setDriverClassName("org.sqlite.JDBC");
                config.setMaximumPoolSize(1);
                config.setLeakDetectionThreshold(30000);

                dataSource = new HikariDataSource(config);

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
                }

                logger.info("Embedding cache initialized at " + dbFile.toAbsolutePath());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to initialize embedding cache", e);
                throw new RuntimeException("Embedding cache initialization failed", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<float[]>> get(String contentHash) {
        Preconditions.checkNotNull(contentHash, "contentHash cannot be null");

        // Check L1 first
        float[] cached = l1Cache.getIfPresent(contentHash);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }

        // Check L2
        return CompletableFuture.supplyAsync(() -> {
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
        }, executor);
    }

    @Override
    public CompletableFuture<Void> put(String contentHash, float[] embedding) {
        Preconditions.checkNotNull(contentHash, "contentHash cannot be null");
        Preconditions.checkNotNull(embedding, "embedding cannot be null");

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
                logger.log(Level.WARNING, "Failed to get L2 cache size", e);
            }
            return 0L;
        }, executor);
    }

    /**
     * Get statistics for monitoring cache performance.
     *
     * @return Stats containing L1 and L2 sizes
     */
    public CacheStats getStats() {
        long l1Size = l1Cache.estimatedSize();
        long l2Size = 0L;
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.createStatement().executeQuery(
                 "SELECT COUNT(*) FROM embedding_cache"
             )) {
            if (rs.next()) {
                l2Size = rs.getLong(1);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to get L2 cache size for stats", e);
        }
        return new CacheStats(l1Size, l2Size, maxMemorySize);
    }

    @Override
    public void shutdown() {
        executor.shutdown();
        l1Cache.invalidateAll();
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private byte[] serializeEmbedding(float[] embedding) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(embedding.length * 4);
        DataOutputStream dos = new DataOutputStream(baos);
        for (float v : embedding) {
            dos.writeFloat(v);
        }
        return baos.toByteArray();
    }

    private float[] deserializeEmbedding(byte[] bytes) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes));
        float[] embedding = new float[bytes.length / 4];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = dis.readFloat();
        }
        return embedding;
    }

    /**
     * Statistics for cache performance monitoring.
     */
    public static class CacheStats {
        public final long l1Size;
        public final long l2Size;
        public final long l1MaxSize;

        public CacheStats(long l1Size, long l2Size, long l1MaxSize) {
            this.l1Size = l1Size;
            this.l2Size = l2Size;
            this.l1MaxSize = l1MaxSize;
        }

        @Override
        public String toString() {
            return String.format(
                "CacheStats{L1: %d/%d, L2: %d, L1_hit_rate: %.1f%%}",
                l1Size, l1MaxSize, l2Size,
                (l1MaxSize > 0 ? (100.0 * l1Size / l1MaxSize) : 0.0)
            );
        }
    }
}
