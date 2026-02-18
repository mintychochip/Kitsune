package org.aincraft.kitsune.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Two-tier embedding cache with Caffeine L1 (in-memory) + SQLite L2 (persistent).
 * Uses shared DataSource from ContainerStorage.
 *
 * Optimizations:
 * - ByteBuffer pooling to reduce GC pressure
 * - Write-behind buffer with batch flushing
 * - Batch operations for bulk efficiency
 *
 * Note: Connections are acquired per-operation and released back to the pool
 * to avoid exhausting the shared HikariCP pool.
 */
public final class LayeredEmbeddingCache implements EmbeddingCache {
    private static final int DEFAULT_MAX_L1_SIZE = 10000;
    private static final int BUFFER_POOL_SIZE = 16;
    private static final int INITIAL_BUFFER_CAPACITY = 4096; // 1024 floats
    private static final int WRITE_BEHIND_BATCH_SIZE = 100;
    private static final long WRITE_BEHIND_FLUSH_MS = 1000; // 1 second

    private final Logger logger;
    private final DataSource dataSource;
    private final com.github.benmanes.caffeine.cache.Cache<String, float[]> l1Cache;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;

    private final Deque<ByteBuffer> bufferPool = new ArrayDeque<>(BUFFER_POOL_SIZE);

    // Write-behind buffer
    private final ConcurrentLinkedQueue<WriteEntry> writeBuffer = new ConcurrentLinkedQueue<>();
    private final AtomicInteger writeBufferSize = new AtomicInteger(0);
    private final AtomicBoolean flushPending = new AtomicBoolean(false);
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

    private record WriteEntry(String hash, byte[] data, long timestamp) {}

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
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "embedding-cache-scheduler");
            t.setDaemon(true);
            return t;
        });
        startWriteBehindFlusher();
    }

    private void startWriteBehindFlusher() {
        scheduler.scheduleWithFixedDelay(() -> {
            if (writeBufferSize.get() > 0 && flushPending.compareAndSet(false, true)) {
                flushInternal().whenComplete((v, e) -> flushPending.set(false));
            }
        }, WRITE_BEHIND_FLUSH_MS, WRITE_BEHIND_FLUSH_MS, TimeUnit.MILLISECONDS);
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

    // === Read Operations ===

    @Override
    public CompletableFuture<Optional<float[]>> get(String contentHash) {
        float[] cached = l1Cache.getIfPresent(contentHash);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }
        return CompletableFuture.supplyAsync(() -> getFromL2(contentHash), executor);
    }

    @Override
    public CompletableFuture<Map<String, float[]>> getAll(List<String> contentHashes) {
        Map<String, float[]> result = new HashMap<>();
        List<String> misses = new ArrayList<>();

        // Check L1 first
        for (String hash : contentHashes) {
            float[] cached = l1Cache.getIfPresent(hash);
            if (cached != null) {
                result.put(hash, cached);
            } else {
                misses.add(hash);
            }
        }

        if (misses.isEmpty()) {
            return CompletableFuture.completedFuture(result);
        }

        // Batch fetch from L2
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, float[]> fromL2 = getBatchFromL2(misses);
                result.putAll(fromL2);
                return result;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed batch get from cache", e);
                return result; // Return partial results
            }
        }, executor);
    }

    private Optional<float[]> getFromL2(String contentHash) {
        String sql = "SELECT embedding FROM embedding_cache WHERE content_hash = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, contentHash);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    byte[] blob = rs.getBytes("embedding");
                    float[] embedding = deserializeEmbedding(blob);
                    l1Cache.put(contentHash, embedding);
                    return Optional.of(embedding);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to get embedding from cache", e);
        }
        return Optional.empty();
    }

    private Map<String, float[]> getBatchFromL2(List<String> hashes) throws SQLException {
        Map<String, float[]> result = new HashMap<>();
        if (hashes.isEmpty()) return result;

        // Build IN clause with placeholders
        String placeholders = String.join(",", hashes.stream().map(h -> "?").toList());
        String sql = "SELECT content_hash, embedding FROM embedding_cache WHERE content_hash IN (" + placeholders + ")";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < hashes.size(); i++) {
                stmt.setString(i + 1, hashes.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String hash = rs.getString("content_hash");
                    byte[] blob = rs.getBytes("embedding");
                    float[] embedding = deserializeEmbedding(blob);
                    result.put(hash, embedding);
                    l1Cache.put(hash, embedding);
                }
            }
        }
        return result;
    }

    // === Write Operations ===

    @Override
    public CompletableFuture<Void> put(String contentHash, float[] embedding) {
        l1Cache.put(contentHash, embedding);
        byte[] serialized = serializeEmbedding(embedding);
        long timestamp = System.currentTimeMillis();

        writeBuffer.offer(new WriteEntry(contentHash, serialized, timestamp));
        int size = writeBufferSize.incrementAndGet();

        // Trigger flush if batch size reached
        if (size >= WRITE_BEHIND_BATCH_SIZE && flushPending.compareAndSet(false, true)) {
            return CompletableFuture.runAsync(() -> {
                flushInternal().join();
                flushPending.set(false);
            }, executor);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> putAll(Map<String, float[]> embeddings) {
        // Add all to L1 immediately
        l1Cache.putAll(embeddings);

        // Buffer all writes
        long timestamp = System.currentTimeMillis();
        for (Map.Entry<String, float[]> entry : embeddings.entrySet()) {
            byte[] serialized = serializeEmbedding(entry.getValue());
            writeBuffer.offer(new WriteEntry(entry.getKey(), serialized, timestamp));
        }
        int size = writeBufferSize.addAndGet(embeddings.size());

        // Trigger flush if batch size reached
        if (size >= WRITE_BEHIND_BATCH_SIZE && flushPending.compareAndSet(false, true)) {
            return CompletableFuture.runAsync(() -> {
                flushInternal().join();
                flushPending.set(false);
            }, executor);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> flush() {
        if (writeBufferSize.get() == 0) {
            return CompletableFuture.completedFuture(null);
        }
        flushPending.set(true);
        return flushInternal().whenComplete((v, e) -> flushPending.set(false));
    }

    private CompletableFuture<Void> flushInternal() {
        return CompletableFuture.runAsync(() -> {
            List<WriteEntry> batch = new ArrayList<>(WRITE_BEHIND_BATCH_SIZE);
            WriteEntry entry;
            while ((entry = writeBuffer.poll()) != null) {
                batch.add(entry);
                writeBufferSize.decrementAndGet();
                if (batch.size() >= WRITE_BEHIND_BATCH_SIZE) {
                    flushBatch(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                flushBatch(batch);
            }
        }, executor);
    }

    private void flushBatch(List<WriteEntry> batch) {
        if (batch.isEmpty()) return;

        String sql = "INSERT OR REPLACE INTO embedding_cache (content_hash, embedding, created_at) VALUES (?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (WriteEntry entry : batch) {
                stmt.setString(1, entry.hash);
                stmt.setBytes(2, entry.data);
                stmt.setLong(3, entry.timestamp);
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to flush batch to cache", e);
        }
    }

    // === Other Operations ===

    @Override
    public CompletableFuture<Void> clear() {
        l1Cache.invalidateAll();
        writeBuffer.clear();
        writeBufferSize.set(0);
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.createStatement().execute("DELETE FROM embedding_cache");
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to clear cache", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Long> size() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM embedding_cache")) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to get cache size", e);
            }
            return 0L;
        }, executor);
    }

    @Override
    public void shutdown() {
        shutdownRequested.set(true);
        scheduler.shutdown();

        // Final flush before shutdown
        try {
            flush().get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to flush on shutdown", e);
        }

        l1Cache.invalidateAll();
        executor.shutdown();
    }

    // === ByteBuffer Pooling ===

    private ByteBuffer acquireBuffer(int minCapacity) {
        ByteBuffer buffer = bufferPool.pollFirst();
        if (buffer == null || buffer.capacity() < minCapacity) {
            return ByteBuffer.allocate(Math.max(minCapacity, INITIAL_BUFFER_CAPACITY))
                .order(ByteOrder.nativeOrder());
        }
        buffer.clear();
        return buffer;
    }

    private void releaseBuffer(ByteBuffer buffer) {
        if (bufferPool.size() < BUFFER_POOL_SIZE) {
            bufferPool.offerFirst(buffer);
        }
    }

    private byte[] serializeEmbedding(float[] embedding) {
        int requiredCapacity = embedding.length * Float.BYTES;
        ByteBuffer buffer = acquireBuffer(requiredCapacity);
        try {
            buffer.limit(requiredCapacity);
            buffer.asFloatBuffer().put(embedding);
            byte[] result = new byte[requiredCapacity];
            buffer.get(result);
            return result;
        } finally {
            releaseBuffer(buffer);
        }
    }

    private float[] deserializeEmbedding(byte[] bytes) {
        float[] embedding = new float[bytes.length / Float.BYTES];
        ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
            .asFloatBuffer().get(embedding);
        return embedding;
    }
}
