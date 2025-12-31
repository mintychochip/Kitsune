package org.aincraft.kitsune.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.jbellis.jvector.disk.ReaderSupplier;
import io.github.jbellis.jvector.disk.ReaderSupplierFactory;
import io.github.jbellis.jvector.graph.GraphIndexBuilder;
import io.github.jbellis.jvector.graph.GraphSearcher;
import io.github.jbellis.jvector.graph.ListRandomAccessVectorValues;
import io.github.jbellis.jvector.graph.SearchResult;
import io.github.jbellis.jvector.graph.disk.OnDiskGraphIndex;
import io.github.jbellis.jvector.graph.similarity.BuildScoreProvider;
import io.github.jbellis.jvector.graph.similarity.DefaultSearchScoreProvider;
import io.github.jbellis.jvector.util.Bits;
import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.aincraft.kitsune.api.ContainerLocations;
import org.aincraft.kitsune.api.Location;
import org.aincraft.kitsune.model.ContainerChunk;
import org.aincraft.kitsune.model.ContainerDocument;
import org.aincraft.kitsune.model.ContainerPath;
import org.aincraft.kitsune.model.IndexedChunk;
import org.aincraft.kitsune.model.StorageStats;

/**
 * Vector storage implementation using JVector for ANN search.
 * Uses SQLite for metadata storage and JVector's graph index for vector similarity search.
 */
public class JVectorStorage implements VectorStorage {
    private static final int GRAPH_DEGREE = 16;
    private static final int CONSTRUCTION_SEARCH_DEPTH = 100;
    private static final float OVERFLOW_FACTOR = 1.2f;
    private static final float ALPHA = 1.2f;

    private final Logger logger;
    private final Path dataDir;
    private final int dimension;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
    private final ReadWriteLock indexLock = new ReentrantReadWriteLock();

    private HikariDataSource dataSource;
    private OnDiskGraphIndex graphIndex;
    private ReaderSupplier readerSupplier;

    // Maps ordinal (node ID in graph) to UUID
    private final List<UUID> ordinalToUuid = new ArrayList<>();
    // Maps UUID to ordinal for quick lookup
    private final Map<UUID, Integer> uuidToOrdinal = new HashMap<>();
    // Stores vectors for reranking during search
    private final List<VectorFloat<?>> vectors = new ArrayList<>();

    private volatile boolean indexDirty = false;

    public JVectorStorage(Logger logger, Path dataDir, int dimension) {
        this.logger = logger;
        this.dataDir = dataDir;
        this.dimension = dimension;
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(dataDir);
                initializeSqlite();
                loadIndex();
                logger.info("JVector storage initialized at " + dataDir.toAbsolutePath());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to initialize JVector storage", e);
                throw new RuntimeException("JVector initialization failed", e);
            }
        }, executor);
    }

    private void initializeSqlite() throws SQLException {
        Path dbFile = dataDir.resolve("metadata.db");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.toAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1);
        config.setLeakDetectionThreshold(30000);

        dataSource = new HikariDataSource(config);

        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS containers (
                    id TEXT PRIMARY KEY,
                    ordinal INTEGER UNIQUE NOT NULL,
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    chunk_index INTEGER NOT NULL DEFAULT 0,
                    content_text TEXT NOT NULL,
                    embedding BLOB NOT NULL,
                    timestamp INTEGER NOT NULL,
                    container_path TEXT DEFAULT NULL
                )
            """);
            conn.createStatement().execute(
                "CREATE INDEX IF NOT EXISTS idx_location ON containers(world, x, y, z)"
            );
            conn.createStatement().execute(
                "CREATE INDEX IF NOT EXISTS idx_ordinal ON containers(ordinal)"
            );

            // Migration: Check if container_path column exists
            migrateSchemaJVector(conn);
        }
    }

    private void migrateSchemaJVector(Connection conn) throws SQLException {
        // Check if container_path column exists
        boolean hasContainerPath = false;
        try (ResultSet rs = conn.createStatement().executeQuery("PRAGMA table_info(containers)")) {
            while (rs.next()) {
                if ("container_path".equals(rs.getString("name"))) {
                    hasContainerPath = true;
                    break;
                }
            }
        }

        if (!hasContainerPath) {
            logger.info("Migrating JVector schema to add container_path column...");
            conn.createStatement().execute("ALTER TABLE containers ADD COLUMN container_path TEXT DEFAULT NULL");
            logger.info("JVector container_path migration completed");
        }
    }

    private void loadIndex() throws SQLException, IOException {
        Path indexPath = dataDir.resolve("vectors.idx");

        // Load metadata, ordinal mappings, AND embeddings
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.createStatement().executeQuery(
                 "SELECT id, ordinal, embedding FROM containers ORDER BY ordinal")) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("id"));
                int ordinal = rs.getInt("ordinal");
                byte[] embeddingBytes = rs.getBytes("embedding");

                // Expand lists to fit ordinal
                while (ordinalToUuid.size() <= ordinal) {
                    ordinalToUuid.add(null);
                    vectors.add(null);
                }
                ordinalToUuid.set(ordinal, uuid);
                uuidToOrdinal.put(uuid, ordinal);

                // Deserialize and store the embedding vector
                if (embeddingBytes != null) {
                    try {
                        float[] embedding = deserializeEmbedding(embeddingBytes);
                        vectors.set(ordinal, toVectorFloat(embedding));
                    } catch (Exception e) {
                        logger.warning("Failed to deserialize embedding for ordinal " + ordinal);
                    }
                }
            }
        }

        logger.info("Loaded " + vectors.size() + " vectors from storage");

        // Count non-null vectors for validation
        long validVectorCount = vectors.stream().filter(v -> v != null).count();
        logger.info("Loaded " + validVectorCount + " valid vectors from " + vectors.size() + " total slots");

        // Load existing graph index if it exists
        if (Files.exists(indexPath)) {
            try {
                readerSupplier = ReaderSupplierFactory.open(indexPath);
                graphIndex = OnDiskGraphIndex.load(readerSupplier);

                // Validate graph index is compatible with loaded vectors
                int graphSize = graphIndex.size();
                if (graphSize != validVectorCount) {
                    logger.warning("Graph index size (" + graphSize + ") doesn't match vector count ("
                        + validVectorCount + "), will rebuild");
                    graphIndex = null;
                    if (readerSupplier != null) {
                        readerSupplier.close();
                        readerSupplier = null;
                    }
                    indexDirty = true;
                } else {
                    logger.info("Loaded existing JVector graph index with " + graphSize + " nodes");
                }
            } catch (Exception e) {
                logger.warning("Failed to load existing index, will rebuild: " + e.getMessage());
                graphIndex = null;
                indexDirty = true;
            }
        } else if (!vectors.isEmpty()) {
            // We have vectors but no index - mark for rebuild
            indexDirty = true;
        }
    }

    @Override
    @Deprecated
    public CompletableFuture<Void> index(ContainerDocument document) {
        ContainerChunk chunk = new ContainerChunk(
            document.location(),
            0,
            document.contentText(),
            document.embedding(),
            document.timestamp()
        );
        return indexChunks(List.of(chunk));
    }

    @Override
    public CompletableFuture<Void> indexChunks(List<ContainerChunk> chunks) {
        if (chunks.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            indexLock.writeLock().lock();
            try {
                Location loc = chunks.get(0).location();

                // Delete existing chunks for this location
                deleteLocationChunks(loc);

                // Insert new chunks - generate UUIDs for each
                try (Connection conn = dataSource.getConnection()) {
                    String sql = """
                        INSERT INTO containers
                        (id, ordinal, world, x, y, z, chunk_index, content_text, embedding, timestamp, container_path)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;

                    for (ContainerChunk chunk : chunks) {
                        UUID uuid = UUID.randomUUID();
                        int ordinal = ordinalToUuid.size();

                        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                            stmt.setString(1, uuid.toString());
                            stmt.setInt(2, ordinal);
                            stmt.setString(3, chunk.location().worldName());
                            stmt.setInt(4, chunk.location().blockX());
                            stmt.setInt(5, chunk.location().blockY());
                            stmt.setInt(6, chunk.location().blockZ());
                            stmt.setInt(7, chunk.chunkIndex());
                            stmt.setString(8, chunk.contentText());
                            stmt.setBytes(9, serializeEmbedding(chunk.embedding()));
                            stmt.setLong(10, chunk.timestamp());
                            // ContainerChunk doesn't have containerPath, set to null
                            stmt.setNull(11, java.sql.Types.VARCHAR);
                            stmt.executeUpdate();
                        }

                        ordinalToUuid.add(uuid);
                        uuidToOrdinal.put(uuid, ordinal);
                        vectors.add(toVectorFloat(chunk.embedding()));
                    }
                }

                indexDirty = true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to index chunks", e);
                throw new RuntimeException("Chunk indexing failed", e);
            } finally {
                indexLock.writeLock().unlock();
            }
        }, executor);
    }

    private void deleteLocationChunks(Location loc) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            // Get UUIDs for chunks at this location
            String selectSql = "SELECT id, ordinal FROM containers WHERE world = ? AND x = ? AND y = ? AND z = ?";
            List<UUID> toDelete = new ArrayList<>();

            try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                stmt.setString(1, loc.worldName());
                stmt.setInt(2, loc.blockX());
                stmt.setInt(3, loc.blockY());
                stmt.setInt(4, loc.blockZ());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        toDelete.add(UUID.fromString(rs.getString("id")));
                    }
                }
            }

            // Delete from SQLite
            String deleteSql = "DELETE FROM containers WHERE world = ? AND x = ? AND y = ? AND z = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                stmt.setString(1, loc.worldName());
                stmt.setInt(2, loc.blockX());
                stmt.setInt(3, loc.blockY());
                stmt.setInt(4, loc.blockZ());
                stmt.executeUpdate();
            }

            // Mark as deleted in mappings (set to null, will be cleaned up on rebuild)
            for (UUID uuid : toDelete) {
                Integer ordinal = uuidToOrdinal.remove(uuid);
                if (ordinal != null) {
                    if (ordinal < ordinalToUuid.size()) {
                        ordinalToUuid.set(ordinal, null);
                    }
                    if (ordinal < vectors.size()) {
                        vectors.set(ordinal, null);
                    }
                }
            }
        }
    }

    @Override
    public CompletableFuture<List<org.aincraft.kitsune.model.SearchResult>> search(
            float[] embedding, int limit, String world) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Search starting: limit=" + limit + ", world=" + world + ", vectorsInIndex=" + vectors.size());

            // Rebuild index if needed - BEFORE acquiring read lock to avoid deadlock
            if (indexDirty || graphIndex == null) {
                indexLock.writeLock().lock();
                try {
                    // Double-check after acquiring lock
                    if (indexDirty || graphIndex == null) {
                        rebuildIndex();
                    }
                } finally {
                    indexLock.writeLock().unlock();
                }
            }

            indexLock.readLock().lock();
            try {
                if (graphIndex == null || vectors.isEmpty()) {
                    logger.info("Search aborted: graphIndex=" + (graphIndex != null) + ", vectorCount=" + vectors.size());
                    return Collections.emptyList();
                }

                VectorFloat<?> queryVector = toVectorFloat(embedding);

                // Track best match per location
                Map<String, org.aincraft.kitsune.model.SearchResult> bestMatches = new HashMap<>();

                try (GraphSearcher searcher = new GraphSearcher(graphIndex)) {
                    // After compaction, vectors list has no nulls - use directly
                    ListRandomAccessVectorValues ravv = new ListRandomAccessVectorValues(vectors, dimension);
                    DefaultSearchScoreProvider ssp = DefaultSearchScoreProvider.exact(
                        queryVector, VectorSimilarityFunction.COSINE, ravv
                    );

                    // Search for more results than needed since we filter by world and dedupe by location
                    int searchLimit = Math.min(limit * 10, ordinalToUuid.size());
                    SearchResult sr = searcher.search(ssp, searchLimit, Bits.ALL);

                    try (Connection conn = dataSource.getConnection()) {
                        for (SearchResult.NodeScore ns : sr.getNodes()) {
                            int ordinal = ns.node;
                            // Safety check: skip stale ordinals
                            if (ordinal >= ordinalToUuid.size() || ordinal >= vectors.size()) {
                                logger.warning("Skipping stale ordinal " + ordinal +
                                    " (ordinalToUuid.size=" + ordinalToUuid.size() +
                                    ", vectors.size=" + vectors.size() + ")");
                                continue;
                            }

                            UUID uuid = ordinalToUuid.get(ordinal);
                            if (uuid == null) continue;

                            // Get metadata from SQLite
                            String sql = """
                                SELECT world, x, y, z, content_text, container_path
                                FROM containers WHERE id = ?
                            """;

                            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                                stmt.setString(1, uuid.toString());
                                try (ResultSet rs = stmt.executeQuery()) {
                                    if (rs.next()) {
                                        String w = rs.getString("world");

                                        // Filter by world if specified
                                        if (world != null && !world.equals(w)) {
                                            continue;
                                        }

                                        Location loc = Location.of(
                                            w, rs.getInt("x"), rs.getInt("y"), rs.getInt("z")
                                        );
                                        String fullContent = rs.getString("content_text");
                                        String preview = fullContent.length() > 100
                                            ? fullContent.substring(0, 100) + "..."
                                            : fullContent;

                                        // Parse container path
                                        String containerPathJson = rs.getString("container_path");
                                        ContainerPath containerPath = null;
                                        if (containerPathJson != null) {
                                            containerPath = ContainerPath.fromJson(containerPathJson);
                                        }

                                        // JVector returns similarity score (higher is better)
                                        double score = ns.score;

                                        // Log similarity at INFO level for debugging
                                        logger.info(String.format("Search result: %.1f%% match at %s - %s",
                                            score * 100, loc, preview));

                                        // Keep best match per location
                                        String locationKey = String.format("%s:%d:%d:%d",
                                            w, loc.blockX(), loc.blockY(), loc.blockZ());

                                        org.aincraft.kitsune.model.SearchResult newResult =
                                            new org.aincraft.kitsune.model.SearchResult(
                                                loc, score, preview, fullContent
                                            );
                                        org.aincraft.kitsune.model.SearchResult existing =
                                            bestMatches.get(locationKey);

                                        if (existing == null || score > existing.score()) {
                                            bestMatches.put(locationKey, newResult);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Convert to list and sort by score
                List<org.aincraft.kitsune.model.SearchResult> results = new ArrayList<>(bestMatches.values());
                results.sort((a, b) -> Double.compare(b.score(), a.score()));
                List<org.aincraft.kitsune.model.SearchResult> finalResults = results.subList(0, Math.min(limit, results.size()));
                logger.info("Search complete: " + finalResults.size() + " results returned (from " + bestMatches.size() + " unique locations)");
                return finalResults;

            } catch (Exception e) {
                logger.log(Level.WARNING, "Search failed", e);
                return Collections.emptyList();
            } finally {
                indexLock.readLock().unlock();
            }
        }, executor);
    }

    private void rebuildIndex() {
        // Must be called with write lock held
        try {
            if (!indexDirty && graphIndex != null) {
                return;
            }

            // Build compaction map: oldOrdinal -> newOrdinal
            Map<Integer, Integer> oldToNew = new HashMap<>();
            List<VectorFloat<?>> compactVectors = new ArrayList<>();
            List<UUID> compactOrdinalToUuid = new ArrayList<>();

            int newOrdinal = 0;
            for (int oldOrdinal = 0; oldOrdinal < vectors.size(); oldOrdinal++) {
                VectorFloat<?> vec = vectors.get(oldOrdinal);
                UUID uuid = oldOrdinal < ordinalToUuid.size() ? ordinalToUuid.get(oldOrdinal) : null;
                if (vec != null && uuid != null) {
                    oldToNew.put(oldOrdinal, newOrdinal);
                    compactVectors.add(vec);
                    compactOrdinalToUuid.add(uuid);
                    newOrdinal++;
                }
            }

            if (compactVectors.isEmpty()) {
                logger.info("No vectors to index");
                indexDirty = false;
                return;
            }

            // Update SQLite ordinals in transaction if any changed
            boolean ordinalsChanged = oldToNew.entrySet().stream()
                .anyMatch(e -> !e.getKey().equals(e.getValue()));

            if (ordinalsChanged) {
                try (Connection conn = dataSource.getConnection()) {
                    conn.setAutoCommit(false);
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "UPDATE containers SET ordinal = ? WHERE ordinal = ?")) {
                        for (var entry : oldToNew.entrySet()) {
                            int oldOrd = entry.getKey();
                            int newOrd = entry.getValue();
                            if (oldOrd != newOrd) {
                                stmt.setInt(1, newOrd);
                                stmt.setInt(2, oldOrd);
                                stmt.addBatch();
                            }
                        }
                        stmt.executeBatch();
                        conn.commit();
                    } catch (SQLException e) {
                        conn.rollback();
                        throw e;
                    }
                }
                logger.info("Compacted ordinals: " + vectors.size() + " -> " + compactVectors.size());
            }

            // Replace in-memory structures with compacted versions
            vectors.clear();
            vectors.addAll(compactVectors);
            ordinalToUuid.clear();
            ordinalToUuid.addAll(compactOrdinalToUuid);
            uuidToOrdinal.clear();
            for (int i = 0; i < ordinalToUuid.size(); i++) {
                uuidToOrdinal.put(ordinalToUuid.get(i), i);
            }

            logger.info("Rebuilding JVector index with " + compactVectors.size() + " vectors");

            // Delete old index file to ensure clean rebuild
            Path indexPath = dataDir.resolve("vectors.idx");
            if (readerSupplier != null) {
                try {
                    readerSupplier.close();
                } catch (Exception ignored) {}
                readerSupplier = null;
            }
            graphIndex = null;
            Files.deleteIfExists(indexPath);

            ListRandomAccessVectorValues ravv = new ListRandomAccessVectorValues(compactVectors, dimension);
            BuildScoreProvider bsp = BuildScoreProvider.randomAccessScoreProvider(
                ravv, VectorSimilarityFunction.COSINE
            );

            try (GraphIndexBuilder builder = new GraphIndexBuilder(
                    bsp, dimension, GRAPH_DEGREE, CONSTRUCTION_SEARCH_DEPTH,
                    OVERFLOW_FACTOR, ALPHA,
                    false)) { // addHierarchy

                var index = builder.build(ravv);

                // Write to disk
                OnDiskGraphIndex.write(index, ravv, indexPath);

                // Reload from disk
                if (readerSupplier != null) {
                    readerSupplier.close();
                }
                readerSupplier = ReaderSupplierFactory.open(indexPath);
                graphIndex = OnDiskGraphIndex.load(readerSupplier);

                indexDirty = false;
                logger.info("JVector index rebuilt successfully");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to rebuild index", e);
        }
    }

    @Override
    public CompletableFuture<Void> delete(Location location) {
        return CompletableFuture.runAsync(() -> {
            indexLock.writeLock().lock();
            try {
                deleteLocationChunks(location);
                indexDirty = true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to delete container", e);
                throw new RuntimeException("Deletion failed", e);
            } finally {
                indexLock.writeLock().unlock();
            }
        }, executor);
    }

    public CompletableFuture<Optional<IndexedChunk>> getById(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
            indexLock.readLock().lock();
            try (Connection conn = dataSource.getConnection()) {
                String sql = """
                    SELECT world, x, y, z, chunk_index, content_text, embedding, timestamp
                    FROM containers WHERE id = ?
                """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, id.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            Location loc = Location.of(
                                rs.getString("world"),
                                rs.getInt("x"),
                                rs.getInt("y"),
                                rs.getInt("z")
                            );
                            return Optional.of(new IndexedChunk(
                                id,
                                loc,
                                rs.getInt("chunk_index"),
                                rs.getString("content_text"),
                                deserializeEmbedding(rs.getBytes("embedding")),
                                rs.getLong("timestamp")
                            ));
                        }
                    }
                }
                return Optional.empty();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to get chunk by ID", e);
                return Optional.empty();
            } finally {
                indexLock.readLock().unlock();
            }
        }, executor);
    }

    public CompletableFuture<Void> deleteById(UUID id) {
        return CompletableFuture.runAsync(() -> {
            indexLock.writeLock().lock();
            try (Connection conn = dataSource.getConnection()) {
                // Get ordinal before deleting
                Integer ordinal = uuidToOrdinal.get(id);

                String sql = "DELETE FROM containers WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, id.toString());
                    stmt.executeUpdate();
                }

                // Mark as deleted in mappings
                if (ordinal != null) {
                    uuidToOrdinal.remove(id);
                    if (ordinal < ordinalToUuid.size()) {
                        ordinalToUuid.set(ordinal, null);
                    }
                    if (ordinal < vectors.size()) {
                        vectors.set(ordinal, null);
                    }
                }

                indexDirty = true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to delete chunk by ID", e);
                throw new RuntimeException("Deletion by ID failed", e);
            } finally {
                indexLock.writeLock().unlock();
            }
        }, executor);
    }

    public CompletableFuture<Void> updateEmbedding(UUID id, float[] newEmbedding) {
        return CompletableFuture.runAsync(() -> {
            indexLock.writeLock().lock();
            try (Connection conn = dataSource.getConnection()) {
                String sql = "UPDATE containers SET embedding = ? WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setBytes(1, serializeEmbedding(newEmbedding));
                    stmt.setString(2, id.toString());
                    stmt.executeUpdate();
                }

                // Update in-memory vector
                Integer ordinal = uuidToOrdinal.get(id);
                if (ordinal != null && ordinal < vectors.size()) {
                    vectors.set(ordinal, toVectorFloat(newEmbedding));
                }

                indexDirty = true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to update embedding", e);
                throw new RuntimeException("Embedding update failed", e);
            } finally {
                indexLock.writeLock().unlock();
            }
        }, executor);
    }

    public CompletableFuture<List<UUID>> getChunkIdsByLocation(Location location) {
        return CompletableFuture.supplyAsync(() -> {
            indexLock.readLock().lock();
            try (Connection conn = dataSource.getConnection()) {
                String sql = "SELECT id FROM containers WHERE world = ? AND x = ? AND y = ? AND z = ?";
                List<UUID> ids = new ArrayList<>();

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, location.worldName());
                    stmt.setInt(2, location.blockX());
                    stmt.setInt(3, location.blockY());
                    stmt.setInt(4, location.blockZ());

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            ids.add(UUID.fromString(rs.getString("id")));
                        }
                    }
                }
                return ids;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to get chunk IDs by location", e);
                return Collections.emptyList();
            } finally {
                indexLock.readLock().unlock();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<StorageStats> getStats() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 ResultSet rs = conn.createStatement()
                     .executeQuery("SELECT COUNT(*) as count FROM containers")) {
                if (rs.next()) {
                    return new StorageStats(rs.getLong("count"), "JVector");
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to get stats", e);
            }
            return new StorageStats(0, "JVector");
        }, executor);
    }

    public CompletableFuture<Void> purgeAll() {
        return CompletableFuture.runAsync(() -> {
            indexLock.writeLock().lock();
            try {
                // Clear SQLite
                try (Connection conn = dataSource.getConnection()) {
                    conn.createStatement().execute("DELETE FROM containers");
                }

                // Clear in-memory structures
                ordinalToUuid.clear();
                uuidToOrdinal.clear();
                vectors.clear();

                // Clear graph index
                if (readerSupplier != null) {
                    readerSupplier.close();
                    readerSupplier = null;
                }
                graphIndex = null;

                // Delete index file
                Path indexPath = dataDir.resolve("vectors.idx");
                Files.deleteIfExists(indexPath);

                indexDirty = false;
                logger.info("Purged all vectors from JVector storage");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to purge all vectors", e);
                throw new RuntimeException("Purge failed", e);
            } finally {
                indexLock.writeLock().unlock();
            }
        }, executor);
    }

    @Override
    public void shutdown() {
        try {
            // Save index before shutdown if dirty
            if (indexDirty) {
                rebuildIndex();
            }

            if (readerSupplier != null) {
                readerSupplier.close();
            }
            if (dataSource != null) {
                dataSource.close();
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during shutdown", e);
        }
        executor.shutdown();
    }

    private VectorFloat<?> toVectorFloat(float[] array) {
        return io.github.jbellis.jvector.vector.VectorizationProvider
            .getInstance()
            .getVectorTypeSupport()
            .createFloatVector(array);
    }

    private byte[] serializeEmbedding(float[] embedding) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(embedding.length);
            for (float v : embedding) {
                dos.writeFloat(v);
            }
            dos.close();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize embedding", e);
        }
    }

    private float[] deserializeEmbedding(byte[] data) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            DataInputStream dis = new DataInputStream(bais);
            int length = dis.readInt();
            float[] result = new float[length];
            for (int i = 0; i < length; i++) {
                result[i] = dis.readFloat();
            }
            dis.close();
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize embedding", e);
        }
    }

    @Override
    public CompletableFuture<Void> registerContainerPositions(ContainerLocations locations) {
        // JVectorStorage doesn't track multi-block container positions
        // This is a no-op for this implementation
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Optional<Location>> getPrimaryLocation(Location anyPosition) {
        // JVectorStorage doesn't track multi-block container positions
        // Return the input position as-is (assumes all positions are primary)
        return CompletableFuture.completedFuture(Optional.of(anyPosition));
    }

    @Override
    public CompletableFuture<List<Location>> getAllPositions(Location primaryLocation) {
        // JVectorStorage doesn't track multi-block container positions
        // Return just the primary location
        return CompletableFuture.completedFuture(Collections.singletonList(primaryLocation));
    }

    @Override
    public CompletableFuture<Void> deleteContainerPositions(Location primaryLocation) {
        // JVectorStorage doesn't track multi-block container positions
        // This is a no-op for this implementation
        return CompletableFuture.completedFuture(null);
    }
}
