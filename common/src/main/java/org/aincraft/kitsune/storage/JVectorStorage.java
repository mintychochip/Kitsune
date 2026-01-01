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
 * Vector storage implementation using JVector for ANN search with SQLite for relational data.
 * Hybrid architecture:
 * - SQLite: containers, container_locations, container_chunks metadata
 * - JVector: embeddings with in-memory vectors and on-disk graph index
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

    // Maps ordinal (node ID in graph) to UUID (chunk ID)
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
            // Create containers table
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS containers (
                    id TEXT PRIMARY KEY,
                    created_at INTEGER NOT NULL
                )
            """);

            // Create container_locations table
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS container_locations (
                    container_id TEXT NOT NULL REFERENCES containers(id) ON DELETE CASCADE,
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    is_primary INTEGER DEFAULT 0,
                    PRIMARY KEY (world, x, y, z)
                )
            """);

            // Create container_chunks table with ordinal for graph node IDs
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS container_chunks (
                    id TEXT PRIMARY KEY,
                    container_id TEXT NOT NULL REFERENCES containers(id) ON DELETE CASCADE,
                    ordinal INTEGER UNIQUE NOT NULL,
                    chunk_index INTEGER NOT NULL,
                    content_text TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    container_path TEXT DEFAULT NULL
                )
            """);

            // Create indices
            conn.createStatement().execute(
                "CREATE INDEX IF NOT EXISTS idx_container_locations_container_id ON container_locations(container_id)"
            );
            conn.createStatement().execute(
                "CREATE INDEX IF NOT EXISTS idx_container_chunks_container_id ON container_chunks(container_id)"
            );
            conn.createStatement().execute(
                "CREATE INDEX IF NOT EXISTS idx_container_chunks_ordinal ON container_chunks(ordinal)"
            );

            // Check for and perform any necessary migrations
            migrateSchema(conn);
        }
    }

    private void migrateSchema(Connection conn) throws SQLException {
        // Check if old containers table exists with location data
        boolean hasOldSchema = false;
        try (ResultSet rs = conn.createStatement().executeQuery("PRAGMA table_info(containers)")) {
            while (rs.next()) {
                if ("world".equals(rs.getString("name"))) {
                    hasOldSchema = true;
                    break;
                }
            }
        }

        if (hasOldSchema) {
            logger.info("Migrating JVector schema from old format to hybrid SQLite+JVector");
            // Migration logic for old schema would go here if needed
        }
    }

    private void loadIndex() throws SQLException, IOException {
        Path indexPath = dataDir.resolve("vectors.idx");

        // Load metadata, ordinal mappings, and embeddings from container_chunks
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.createStatement().executeQuery(
                 "SELECT id, ordinal FROM container_chunks ORDER BY ordinal")) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("id"));
                int ordinal = rs.getInt("ordinal");

                // Expand lists to fit ordinal
                while (ordinalToUuid.size() <= ordinal) {
                    ordinalToUuid.add(null);
                    vectors.add(null);
                }
                ordinalToUuid.set(ordinal, uuid);
                uuidToOrdinal.put(uuid, ordinal);
            }

            // Load embedding vectors from a separate embeddings table if it exists
            // For now, we'll use the old containers table as fallback for backward compatibility
            loadEmbeddingsLegacy(conn);
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

    private void loadEmbeddingsLegacy(Connection conn) throws SQLException {
        // Load embeddings from old containers table if it has embedding column
        try {
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT id, ordinal, embedding FROM containers ORDER BY ordinal");
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("id"));
                int ordinal = rs.getInt("ordinal");
                byte[] embeddingBytes = rs.getBytes("embedding");

                if (embeddingBytes != null) {
                    try {
                        float[] embedding = deserializeEmbedding(embeddingBytes);
                        if (ordinal < vectors.size()) {
                            vectors.set(ordinal, toVectorFloat(embedding));
                        }
                    } catch (Exception e) {
                        logger.warning("Failed to deserialize embedding for ordinal " + ordinal);
                    }
                }
            }
        } catch (SQLException e) {
            // Old table format doesn't exist or doesn't have embedding column - that's ok
            logger.fine("No legacy embeddings found to migrate");
        }
    }

    @Override
    @Deprecated
    public CompletableFuture<Void> index(ContainerDocument document) {
        java.util.UUID containerId = generateContainerIdFromLocation(document.location());
        ContainerChunk chunk = new ContainerChunk(
            containerId,
            0,
            document.contentText(),
            document.embedding(),
            document.timestamp()
        );
        return indexChunks(containerId, List.of(chunk));
    }

    /**
     * Generates a deterministic container ID from a location.
     * Phase 1 implementation uses UUID v5 (namespace-based) from location coordinates.
     *
     * @param location the location to generate ID from
     * @return a stable UUID for the location
     */
    private java.util.UUID generateContainerIdFromLocation(org.aincraft.kitsune.api.Location location) {
        String locationString = location.worldName() + ":" + location.blockX() + "," +
                                location.blockY() + "," + location.blockZ();
        return java.util.UUID.nameUUIDFromBytes(locationString.getBytes());
    }

    @Override
    @Deprecated
    public CompletableFuture<Void> indexChunks(List<ContainerChunk> chunks) {
        if (chunks.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        // Use the first chunk's containerId
        return indexChunks(chunks.get(0).containerId(), chunks);
    }

    @Override
    @Deprecated
    public CompletableFuture<Void> indexChunks(List<ContainerChunk> chunks, Location location) {
        java.util.UUID containerId = generateContainerIdFromLocation(location);
        return indexChunks(containerId, chunks);
    }

    @Override
    public CompletableFuture<Void> indexChunks(UUID containerId, List<ContainerChunk> chunks) {
        if (chunks.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            indexLock.writeLock().lock();
            try {
                try (Connection conn = dataSource.getConnection()) {
                    conn.setAutoCommit(false);
                    try {
                        // Ensure container exists
                        ensureContainerExists(conn, containerId);

                        // Delete existing chunks for this container
                        deleteContainerChunks(conn, containerId);

                        // Insert new chunks with ordinals
                        for (ContainerChunk chunk : chunks) {
                            UUID chunkId = UUID.randomUUID();
                            int ordinal = ordinalToUuid.size();

                            insertChunk(conn, chunkId, containerId, ordinal, chunk);

                            ordinalToUuid.add(chunkId);
                            uuidToOrdinal.put(chunkId, ordinal);
                            vectors.add(toVectorFloat(chunk.embedding()));
                        }

                        conn.commit();
                        indexDirty = true;
                    } catch (SQLException e) {
                        conn.rollback();
                        throw e;
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to index chunks", e);
                throw new RuntimeException("Chunk indexing failed", e);
            } finally {
                indexLock.writeLock().unlock();
            }
        }, executor);
    }

    private void ensureContainerExists(Connection conn, UUID containerId) throws SQLException {
        String selectSql = "SELECT id FROM containers WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
            stmt.setString(1, containerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    // Container doesn't exist, create it
                    String insertSql = "INSERT INTO containers (id, created_at) VALUES (?, ?)";
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setString(1, containerId.toString());
                        insertStmt.setLong(2, System.currentTimeMillis());
                        insertStmt.executeUpdate();
                    }
                }
            }
        }
    }

    private void deleteContainerChunks(Connection conn, UUID containerId) throws SQLException {
        // Get chunk IDs and ordinals for deletion
        String selectSql = "SELECT id, ordinal FROM container_chunks WHERE container_id = ?";
        List<UUID> toDelete = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
            stmt.setString(1, containerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    toDelete.add(UUID.fromString(rs.getString("id")));
                }
            }
        }

        // Delete from SQLite
        String deleteSql = "DELETE FROM container_chunks WHERE container_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setString(1, containerId.toString());
            stmt.executeUpdate();
        }

        // Mark as deleted in mappings
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

    private void insertChunk(Connection conn, UUID chunkId, UUID containerId, int ordinal, ContainerChunk chunk) throws SQLException {
        String sql = """
            INSERT INTO container_chunks
            (id, container_id, ordinal, chunk_index, content_text, timestamp, container_path)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, chunkId.toString());
            stmt.setString(2, containerId.toString());
            stmt.setInt(3, ordinal);
            stmt.setInt(4, chunk.chunkIndex());
            stmt.setString(5, chunk.contentText());
            stmt.setLong(6, chunk.timestamp());
            stmt.setString(7, formatContainerPath(chunk.containerPath()));
            stmt.executeUpdate();
        }
    }

    private String formatContainerPath(org.aincraft.kitsune.model.ContainerPath path) {
        return path != null ? path.toString() : null;
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

                // Track best match per container
                Map<UUID, org.aincraft.kitsune.model.SearchResult> bestMatches = new HashMap<>();

                try (GraphSearcher searcher = new GraphSearcher(graphIndex)) {
                    ListRandomAccessVectorValues ravv = new ListRandomAccessVectorValues(vectors, dimension);
                    DefaultSearchScoreProvider ssp = DefaultSearchScoreProvider.exact(
                        queryVector, VectorSimilarityFunction.COSINE, ravv
                    );

                    // Search for more results than needed since we filter by world and dedupe by container
                    int searchLimit = Math.min(limit * 10, ordinalToUuid.size());
                    SearchResult sr = searcher.search(ssp, searchLimit, Bits.ALL);

                    try (Connection conn = dataSource.getConnection()) {
                        for (SearchResult.NodeScore ns : sr.getNodes()) {
                            int ordinal = ns.node;
                            // Safety check: skip stale ordinals
                            if (ordinal >= ordinalToUuid.size() || ordinal >= vectors.size()) {
                                logger.warning("Skipping stale ordinal " + ordinal);
                                continue;
                            }

                            UUID chunkId = ordinalToUuid.get(ordinal);
                            if (chunkId == null) continue;

                            // Get chunk and container metadata from SQLite
                            String sql = """
                                SELECT cc.container_id, cc.content_text, cc.container_path, cl.world, cl.x, cl.y, cl.z
                                FROM container_chunks cc
                                JOIN containers c ON cc.container_id = c.id
                                LEFT JOIN container_locations cl ON c.id = cl.container_id AND cl.is_primary = 1
                                WHERE cc.id = ?
                            """;

                            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                                stmt.setString(1, chunkId.toString());
                                try (ResultSet rs = stmt.executeQuery()) {
                                    if (rs.next()) {
                                        UUID containerId = UUID.fromString(rs.getString("container_id"));
                                        String w = rs.getString("world");
                                        Integer x = rs.getInt("x");
                                        Integer y = rs.getInt("y");
                                        Integer z = rs.getInt("z");

                                        // Filter by world if specified
                                        if (world != null && !world.equals(w)) {
                                            continue;
                                        }

                                        // Skip if no location found
                                        if (w == null) {
                                            continue;
                                        }

                                        Location loc = Location.of(w, x, y, z);
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

                                        logger.info(String.format("Search result: %.1f%% match at %s - %s",
                                            score * 100, loc, preview));

                                        // Keep best match per container
                                        org.aincraft.kitsune.model.SearchResult newResult =
                                            new org.aincraft.kitsune.model.SearchResult(
                                                loc, score, preview, fullContent
                                            );
                                        org.aincraft.kitsune.model.SearchResult existing =
                                            bestMatches.get(containerId);

                                        if (existing == null || score > existing.score()) {
                                            bestMatches.put(containerId, newResult);
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
                logger.info("Search complete: " + finalResults.size() + " results returned (from " + bestMatches.size() + " unique containers)");
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
                            "UPDATE container_chunks SET ordinal = ? WHERE ordinal = ?")) {
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
            try (Connection conn = dataSource.getConnection()) {
                // Find container by location
                UUID containerId = getContainerIdByLocation(conn, location);
                if (containerId != null) {
                    deleteContainerChunks(conn, containerId);
                    indexDirty = true;
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to delete container", e);
                throw new RuntimeException("Deletion failed", e);
            } finally {
                indexLock.writeLock().unlock();
            }
        }, executor);
    }

    private UUID getContainerIdByLocation(Connection conn, Location location) throws SQLException {
        String sql = "SELECT container_id FROM container_locations WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, location.worldName());
            stmt.setInt(2, location.blockX());
            stmt.setInt(3, location.blockY());
            stmt.setInt(4, location.blockZ());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return UUID.fromString(rs.getString("container_id"));
                }
            }
        }
        return null;
    }

    public CompletableFuture<Optional<IndexedChunk>> getById(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
            indexLock.readLock().lock();
            try (Connection conn = dataSource.getConnection()) {
                String sql = """
                    SELECT cc.container_id, cc.chunk_index, cc.content_text, cc.timestamp
                    FROM container_chunks cc
                    WHERE cc.id = ?
                """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, id.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            UUID containerId = UUID.fromString(rs.getString("container_id"));
                            return Optional.of(new IndexedChunk(
                                id,
                                containerId,
                                rs.getInt("chunk_index"),
                                rs.getString("content_text"),
                                getVectorForChunkId(id),
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

    private float[] getVectorForChunkId(UUID chunkId) {
        Integer ordinal = uuidToOrdinal.get(chunkId);
        if (ordinal != null && ordinal < vectors.size()) {
            VectorFloat<?> vf = vectors.get(ordinal);
            if (vf != null) {
                float[] arr = new float[dimension];
                for (int i = 0; i < Math.min(dimension, vf.length()); i++) {
                    arr[i] = vf.get(i);
                }
                return arr;
            }
        }
        return new float[dimension];
    }

    public CompletableFuture<Void> deleteById(UUID id) {
        return CompletableFuture.runAsync(() -> {
            indexLock.writeLock().lock();
            try (Connection conn = dataSource.getConnection()) {
                // Get ordinal before deleting
                Integer ordinal = uuidToOrdinal.get(id);

                String sql = "DELETE FROM container_chunks WHERE id = ?";
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
                // Note: embeddings are now stored separately if needed
                // For now, just update in-memory vector

                // Update in-memory vector
                Integer ordinal = uuidToOrdinal.get(id);
                if (ordinal != null && ordinal < vectors.size()) {
                    vectors.set(ordinal, toVectorFloat(newEmbedding));
                }

                indexDirty = true;
            } catch (Exception e) {
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
                String sql = """
                    SELECT cc.id FROM container_chunks cc
                    JOIN containers c ON cc.container_id = c.id
                    JOIN container_locations cl ON c.id = cl.container_id
                    WHERE cl.world = ? AND cl.x = ? AND cl.y = ? AND cl.z = ?
                """;
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
                     .executeQuery("SELECT COUNT(*) as count FROM container_chunks")) {
                if (rs.next()) {
                    return new StorageStats(rs.getLong("count"), "JVector");
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to get stats", e);
            }
            return new StorageStats(0, "JVector");
        }, executor);
    }

    @Override
    public CompletableFuture<Void> purgeAll() {
        return CompletableFuture.runAsync(() -> {
            indexLock.writeLock().lock();
            try {
                // Clear SQLite
                try (Connection conn = dataSource.getConnection()) {
                    conn.setAutoCommit(false);
                    try {
                        conn.createStatement().execute("DELETE FROM container_chunks");
                        conn.createStatement().execute("DELETE FROM container_locations");
                        conn.createStatement().execute("DELETE FROM containers");
                        conn.commit();
                    } catch (SQLException e) {
                        conn.rollback();
                        throw e;
                    }
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

    @Override
    public CompletableFuture<Void> registerContainerPositions(ContainerLocations locations) {
        return CompletableFuture.runAsync(() -> {
            indexLock.writeLock().lock();
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    Location primaryLoc = locations.primaryLocation();
                    String key = primaryLoc.worldName() + ":" + primaryLoc.blockX() + "," + primaryLoc.blockY() + "," + primaryLoc.blockZ();
                    UUID containerId = UUID.nameUUIDFromBytes(key.getBytes());

                    // Ensure container exists
                    ensureContainerExists(conn, containerId);

                    // Clear existing locations for this container
                    String deleteSql = "DELETE FROM container_locations WHERE container_id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                        stmt.setString(1, containerId.toString());
                        stmt.executeUpdate();
                    }

                    // Insert all locations
                    String insertSql = """
                        INSERT INTO container_locations (container_id, world, x, y, z, is_primary)
                        VALUES (?, ?, ?, ?, ?, ?)
                    """;
                    try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                        for (Location loc : locations.allLocations()) {
                            stmt.setString(1, containerId.toString());
                            stmt.setString(2, loc.worldName());
                            stmt.setInt(3, loc.blockX());
                            stmt.setInt(4, loc.blockY());
                            stmt.setInt(5, loc.blockZ());
                            stmt.setInt(6, loc.equals(primaryLoc) ? 1 : 0);
                            stmt.addBatch();
                        }
                        stmt.executeBatch();
                    }

                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to register container positions", e);
                throw new RuntimeException("Position registration failed", e);
            } finally {
                indexLock.writeLock().unlock();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<Location>> getPrimaryLocation(Location anyPosition) {
        return CompletableFuture.supplyAsync(() -> {
            indexLock.readLock().lock();
            try (Connection conn = dataSource.getConnection()) {
                String sql = """
                    SELECT cl.world, cl.x, cl.y, cl.z FROM container_locations cl
                    JOIN container_locations query ON cl.container_id = query.container_id
                    WHERE query.world = ? AND query.x = ? AND query.y = ? AND query.z = ?
                    AND cl.is_primary = 1
                """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, anyPosition.worldName());
                    stmt.setInt(2, anyPosition.blockX());
                    stmt.setInt(3, anyPosition.blockY());
                    stmt.setInt(4, anyPosition.blockZ());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            Location loc = Location.of(
                                rs.getString("world"),
                                rs.getInt("x"),
                                rs.getInt("y"),
                                rs.getInt("z")
                            );
                            return Optional.of(loc);
                        }
                    }
                }
                return Optional.empty();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to get primary location", e);
                return Optional.empty();
            } finally {
                indexLock.readLock().unlock();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<Location>> getAllPositions(Location primaryLocation) {
        return CompletableFuture.supplyAsync(() -> {
            indexLock.readLock().lock();
            try (Connection conn = dataSource.getConnection()) {
                String sql = """
                    SELECT world, x, y, z FROM container_locations
                    WHERE container_id = (
                        SELECT container_id FROM container_locations
                        WHERE world = ? AND x = ? AND y = ? AND z = ? AND is_primary = 1
                    )
                """;

                List<Location> locations = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, primaryLocation.worldName());
                    stmt.setInt(2, primaryLocation.blockX());
                    stmt.setInt(3, primaryLocation.blockY());
                    stmt.setInt(4, primaryLocation.blockZ());
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            locations.add(Location.of(
                                rs.getString("world"),
                                rs.getInt("x"),
                                rs.getInt("y"),
                                rs.getInt("z")
                            ));
                        }
                    }
                }
                return locations;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to get all positions", e);
                return Collections.emptyList();
            } finally {
                indexLock.readLock().unlock();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deleteContainerPositions(Location primaryLocation) {
        return CompletableFuture.runAsync(() -> {
            indexLock.writeLock().lock();
            try (Connection conn = dataSource.getConnection()) {
                // Find container ID for this primary location
                String selectSql = "SELECT container_id FROM container_locations WHERE world = ? AND x = ? AND y = ? AND z = ? AND is_primary = 1";
                UUID containerId = null;
                try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                    stmt.setString(1, primaryLocation.worldName());
                    stmt.setInt(2, primaryLocation.blockX());
                    stmt.setInt(3, primaryLocation.blockY());
                    stmt.setInt(4, primaryLocation.blockZ());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            containerId = UUID.fromString(rs.getString("container_id"));
                        }
                    }
                }

                if (containerId != null) {
                    // Delete all locations for this container
                    String deleteSql = "DELETE FROM container_locations WHERE container_id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                        stmt.setString(1, containerId.toString());
                        stmt.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to delete container positions", e);
                throw new RuntimeException("Position deletion failed", e);
            } finally {
                indexLock.writeLock().unlock();
            }
        }, executor);
    }

    // Phase 2-3: Container management API implementations

    @Override
    public CompletableFuture<UUID> getOrCreateContainer(ContainerLocations locations) {
        return CompletableFuture.supplyAsync(() -> {
            indexLock.writeLock().lock();
            try (Connection conn = dataSource.getConnection()) {
                // Try to find existing container by any location
                UUID existingContainerId = null;
                for (Location loc : locations.allLocations()) {
                    Optional<UUID> existing = getContainerByLocationInternal(conn, loc);
                    if (existing.isPresent()) {
                        existingContainerId = existing.get();
                        break;
                    }
                }

                if (existingContainerId != null) {
                    // Found existing container - update locations (handles single→double chest)
                    updateContainerLocations(conn, existingContainerId, locations);
                    return existingContainerId;
                }

                // Not found, create new container
                UUID newContainerId = UUID.randomUUID();
                ensureContainerExists(conn, newContainerId);

                // Register all locations for this container
                updateContainerLocations(conn, newContainerId, locations);

                return newContainerId;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to get or create container", e);
                throw new RuntimeException("Get or create container failed", e);
            } finally {
                indexLock.writeLock().unlock();
            }
        }, executor);
    }

    /**
     * Updates container locations, replacing existing locations with the new set.
     * Handles single→double chest transitions by adding new locations.
     */
    private void updateContainerLocations(Connection conn, UUID containerId, ContainerLocations locations) throws SQLException {
        // Delete existing locations
        String deleteSql = "DELETE FROM container_locations WHERE container_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setString(1, containerId.toString());
            stmt.executeUpdate();
        }

        // Insert all locations
        Location primaryLoc = locations.primaryLocation();
        String insertSql = """
            INSERT INTO container_locations (container_id, world, x, y, z, is_primary)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            for (Location loc : locations.allLocations()) {
                stmt.setString(1, containerId.toString());
                stmt.setString(2, loc.worldName());
                stmt.setInt(3, loc.blockX());
                stmt.setInt(4, loc.blockY());
                stmt.setInt(5, loc.blockZ());
                stmt.setInt(6, loc.equals(primaryLoc) ? 1 : 0);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    @Override
    public CompletableFuture<Optional<UUID>> getContainerByLocation(Location location) {
        return CompletableFuture.supplyAsync(() -> {
            indexLock.readLock().lock();
            try (Connection conn = dataSource.getConnection()) {
                return getContainerByLocationInternal(conn, location);
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to get container by location", e);
                return Optional.empty();
            } finally {
                indexLock.readLock().unlock();
            }
        }, executor);
    }

    private Optional<UUID> getContainerByLocationInternal(Connection conn, Location location) throws SQLException {
        String sql = "SELECT container_id FROM container_locations WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, location.worldName());
            stmt.setInt(2, location.blockX());
            stmt.setInt(3, location.blockY());
            stmt.setInt(4, location.blockZ());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(UUID.fromString(rs.getString("container_id")));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public CompletableFuture<List<Location>> getContainerLocations(UUID containerId) {
        return CompletableFuture.supplyAsync(() -> {
            indexLock.readLock().lock();
            try (Connection conn = dataSource.getConnection()) {
                String sql = "SELECT world, x, y, z FROM container_locations WHERE container_id = ?";
                List<Location> locations = new ArrayList<>();

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, containerId.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            locations.add(Location.of(
                                rs.getString("world"),
                                rs.getInt("x"),
                                rs.getInt("y"),
                                rs.getInt("z")
                            ));
                        }
                    }
                }
                return locations;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to get container locations", e);
                return Collections.emptyList();
            } finally {
                indexLock.readLock().unlock();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<Location>> getPrimaryLocationForContainer(UUID containerId) {
        return CompletableFuture.supplyAsync(() -> {
            indexLock.readLock().lock();
            try (Connection conn = dataSource.getConnection()) {
                String sql = "SELECT world, x, y, z FROM container_locations WHERE container_id = ? AND is_primary = 1";

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, containerId.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            Location loc = Location.of(
                                rs.getString("world"),
                                rs.getInt("x"),
                                rs.getInt("y"),
                                rs.getInt("z")
                            );
                            return Optional.of(loc);
                        }
                    }
                }
                return Optional.empty();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to get primary location for container", e);
                return Optional.empty();
            } finally {
                indexLock.readLock().unlock();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deleteContainer(UUID containerId) {
        return CompletableFuture.runAsync(() -> {
            indexLock.writeLock().lock();
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    // Delete chunks for this container
                    deleteContainerChunks(conn, containerId);

                    // Delete locations for this container
                    String deleteLoc = "DELETE FROM container_locations WHERE container_id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(deleteLoc)) {
                        stmt.setString(1, containerId.toString());
                        stmt.executeUpdate();
                    }

                    // Delete container itself
                    String deleteContainer = "DELETE FROM containers WHERE id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(deleteContainer)) {
                        stmt.setString(1, containerId.toString());
                        stmt.executeUpdate();
                    }

                    conn.commit();
                    indexDirty = true;
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to delete container", e);
                throw new RuntimeException("Container deletion failed", e);
            } finally {
                indexLock.writeLock().unlock();
            }
        }, executor);
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
}
