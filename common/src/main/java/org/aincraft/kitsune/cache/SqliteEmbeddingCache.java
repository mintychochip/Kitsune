package org.aincraft.kitsune.cache;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite-backed embedding cache with in-memory LRU layer.
 * Caches embeddings by content hash to avoid redundant API calls.
 */
public class SqliteEmbeddingCache implements EmbeddingCache {
    private final Logger logger;
    private final String dbPath;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<String, float[]> memoryCache = new ConcurrentHashMap<>();
    private final int maxMemoryCacheSize;
    private HikariDataSource dataSource;

    public SqliteEmbeddingCache(Logger logger, String dbPath) {
        this(logger, dbPath, 1000);
    }

    public SqliteEmbeddingCache(Logger logger, String dbPath, int maxMemoryCacheSize) {
        this.logger = Preconditions.checkNotNull(logger, "logger cannot be null");
        this.dbPath = Preconditions.checkNotNull(dbPath, "dbPath cannot be null");
        this.maxMemoryCacheSize = maxMemoryCacheSize;
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                try {
                    Class.forName("org.sqlite.JDBC");
                } catch (ClassNotFoundException e) {
                    logger.warning("SQLite driver not found in classpath");
                }

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

        // Check memory cache first
        float[] cached = memoryCache.get(contentHash);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }

        // Check SQLite
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
                        // Populate memory cache
                        putInMemoryCache(contentHash, embedding);
                        return Optional.of(embedding);
                    }
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to get cached embedding", e);
            }
            return Optional.empty();
        }, executor);
    }

    @Override
    public CompletableFuture<Void> put(String contentHash, float[] embedding) {
        Preconditions.checkNotNull(contentHash, "contentHash cannot be null");
        Preconditions.checkNotNull(embedding, "embedding cannot be null");

        // Add to memory cache
        putInMemoryCache(contentHash, embedding);

        // Persist to SQLite
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
        memoryCache.clear();
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.createStatement().execute("DELETE FROM embedding_cache");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to clear embedding cache", e);
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
        executor.shutdown();
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private void putInMemoryCache(String contentHash, float[] embedding) {
        // Simple eviction: clear half when full
        if (memoryCache.size() >= maxMemoryCacheSize) {
            int toRemove = maxMemoryCacheSize / 2;
            var iterator = memoryCache.keySet().iterator();
            while (iterator.hasNext() && toRemove > 0) {
                iterator.next();
                iterator.remove();
                toRemove--;
            }
        }
        memoryCache.put(contentHash, embedding);
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
}
