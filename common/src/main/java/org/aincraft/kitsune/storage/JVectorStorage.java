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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.aincraft.kitsune.World;
import org.aincraft.kitsune.api.ContainerLocations;
import org.aincraft.kitsune.api.Location;
import org.aincraft.kitsune.api.model.ContainerPath;
import org.aincraft.kitsune.KitsunePlatform;
import org.aincraft.kitsune.model.ContainerChunk;
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

    private final KitsunePlatform platform;
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

    public JVectorStorage(KitsunePlatform platform, Logger logger, Path dataDir, int dimension) {
      this.platform = platform;
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

            // Create threshold_config table
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS threshold_config (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    threshold REAL NOT NULL DEFAULT 0.7
                )
            """);

            // Initialize threshold_config with default value if not exists
            try (ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) as count FROM threshold_config")) {
                if (rs.next() && rs.getLong("count") == 0) {
                    conn.createStatement().execute("""
                        INSERT INTO threshold_config (id, threshold) VALUES (1, 0.7)
                    """);
                }
            }

            // Create R-tree spatial index for container locations
            conn.createStatement().execute("""
                CREATE VIRTUAL TABLE IF NOT EXISTS container_locations_rtree USING rtree(
                    id,              -- rowid
                    min_x, max_x,    -- X bounds
                    min_y, max_y,    -- Y bounds
                    min_z, max_z     -- Z bounds
                )
            """);

            // Create mapping table to link rtree entries to containers
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS container_rtree_map (
                    rtree_id INTEGER PRIMARY KEY,
                    container_id TEXT NOT NULL,
                    world TEXT NOT NULL,
                    UNIQUE(container_id, world)
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
            conn.createStatement().execute(
                "CREATE INDEX IF NOT EXISTS idx_container_rtree_map_container ON container_rtree_map(container_id)"
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

        // Load ordinal mappings from container_chunks (vectors come from JVector graph)
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
        }

        long ordinalCount = ordinalToUuid.stream().filter(u -> u != null).count();
        logger.info("Loaded " + ordinalCount + " ordinal mappings from SQLite");

        // Load existing graph index and vectors from JVector
        if (Files.exists(indexPath)) {
            try {
                readerSupplier = ReaderSupplierFactory.open(indexPath);
                graphIndex = OnDiskGraphIndex.load(readerSupplier);

                int graphSize = graphIndex.size();
                logger.info("Loaded JVector graph index with " + graphSize + " nodes");

                // Populate vectors from graph's view (JVector persists vectors in the graph file)
                var graphView = graphIndex.getView();
                for (int ordinal = 0; ordinal < ordinalToUuid.size(); ordinal++) {
                    if (ordinalToUuid.get(ordinal) != null && ordinal < graphSize) {
                        try {
                            VectorFloat<?> vec = graphView.getVector(ordinal);
                            if (vec != null) {
                                vectors.set(ordinal, vec);
                            }
                        } catch (Exception e) {
                            logger.warning("Failed to load vector for ordinal " + ordinal + ": " + e.getMessage());
                        }
                    }
                }

                long validVectorCount = vectors.stream().filter(v -> v != null).count();
                logger.info("Loaded " + validVectorCount + " vectors from JVector graph");

                // Validate graph matches ordinal mappings
                long expectedCount = ordinalToUuid.stream().filter(u -> u != null).count();
                if (graphSize != expectedCount) {
                    logger.warning("Graph size (" + graphSize + ") doesn't match ordinal count ("
                        + expectedCount + "), will rebuild");
                    indexDirty = true;
                }
            } catch (Exception e) {
                logger.warning("Failed to load existing index, will rebuild: " + e.getMessage());
                graphIndex = null;
                if (readerSupplier != null) {
                    try { readerSupplier.close(); } catch (Exception ignored) {}
                    readerSupplier = null;
                }
                indexDirty = true;
            }
        } else if (!ordinalToUuid.isEmpty()) {
            // We have ordinal mappings but no index - mark for rebuild
            logger.info("No graph index found, will rebuild from stored data");
            indexDirty = true;
        }
    }

    private void loadEmbeddingsLegacy(Connection conn) {
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

        // Mark index as dirty so it gets rebuilt before next search
        if (!toDelete.isEmpty()) {
            indexDirty = true;
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

    private String formatContainerPath(ContainerPath path) {
        return path != null ? path.toJson() : null;
    }

    @Override
    public CompletableFuture<List<org.aincraft.kitsune.model.SearchResult>> search(
            float[] embedding, int limit, String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Search starting: limit=" + limit + ", world=" + worldName + ", vectorsInIndex=" + vectors.size());

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

                // Collect all matching items (including multiple items from same container)
                List<org.aincraft.kitsune.model.SearchResult> allMatches = new ArrayList<>();

                try (GraphSearcher searcher = new GraphSearcher(graphIndex)) {
                    ListRandomAccessVectorValues ravv = new ListRandomAccessVectorValues(vectors, dimension);
                    DefaultSearchScoreProvider ssp = DefaultSearchScoreProvider.exact(
                        queryVector, VectorSimilarityFunction.COSINE, ravv
                    );

                    // Search for more results than needed since we filter by world
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
                                        if (worldName != null && !worldName.equals(w)) {
                                            continue;
                                        }

                                        // Skip if no location found
                                        if (w == null) {
                                            continue;
                                        }
                                        World world = platform.getWorld(w);

                                        Location loc = KitsunePlatform.get().createLocation(w, x, y, z);
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

                                        // Add result without deduplication
                                        org.aincraft.kitsune.model.SearchResult newResult =
                                            new org.aincraft.kitsune.model.SearchResult(
                                                loc, List.of(loc), score, preview, fullContent, containerPath
                                            );
                                        allMatches.add(newResult);
                                    }
                                }
                            }
                        }
                    }
                }

                // Sort by score and limit results
                allMatches.sort((a, b) -> Double.compare(b.score(), a.score()));
                List<org.aincraft.kitsune.model.SearchResult> finalResults = allMatches.subList(0, Math.min(limit, allMatches.size()));
                logger.info("Search complete: " + finalResults.size() + " results returned");
                return finalResults;

            } catch (Exception e) {
                logger.log(Level.WARNING, "Search failed", e);
                return Collections.emptyList();
            } finally {
                indexLock.readLock().unlock();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<org.aincraft.kitsune.model.SearchResult>> searchWithinRadius(
            float[] embedding, int limit, Location center, int radius) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Search within radius starting: limit=" + limit + ", center=" + center
                + ", radius=" + radius + ", vectorsInIndex=" + vectors.size());

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
                    logger.info("Search within radius aborted: graphIndex=" + (graphIndex != null) + ", vectorCount=" + vectors.size());
                    return Collections.emptyList();
                }

                // Step (a): SQL pre-filtering to find valid ordinals within bounding box
                Set<Integer> validOrdinals = new HashSet<>();
                try (Connection conn = dataSource.getConnection()) {
                    String boundingBoxSql = """
                        SELECT DISTINCT cc.ordinal
                        FROM container_chunks cc
                        JOIN containers c ON cc.container_id = c.id
                        LEFT JOIN container_locations cl ON c.id = cl.container_id AND cl.is_primary = 1
                        WHERE cl.world = ?
                        AND cl.x BETWEEN ? AND ?
                        AND cl.y BETWEEN ? AND ?
                        AND cl.z BETWEEN ? AND ?
                    """;

                    try (PreparedStatement stmt = conn.prepareStatement(boundingBoxSql)) {
                        stmt.setString(1, center.getWorld().getName());
                        stmt.setInt(2, center.blockX() - radius);
                        stmt.setInt(3, center.blockX() + radius);
                        stmt.setInt(4, center.blockY() - radius);
                        stmt.setInt(5, center.blockY() + radius);
                        stmt.setInt(6, center.blockZ() - radius);
                        stmt.setInt(7, center.blockZ() + radius);

                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                validOrdinals.add(rs.getInt("ordinal"));
                            }
                        }
                    }

                    // Early return if no containers in bounding box
                    if (validOrdinals.isEmpty()) {
                        logger.info("Search within radius: no containers found in bounding box");
                        return Collections.emptyList();
                    }

                    logger.info("Search within radius: found " + validOrdinals.size() + " ordinals in bounding box");

                    // Step (b): Create custom Bits filter for valid ordinals
                    Bits radiusBits = validOrdinals::contains;

                    VectorFloat<?> queryVector = toVectorFloat(embedding);

                    // Collect all matching items within radius
                    List<org.aincraft.kitsune.model.SearchResult> allMatches = new ArrayList<>();

                    try (GraphSearcher searcher = new GraphSearcher(graphIndex)) {
                        ListRandomAccessVectorValues ravv = new ListRandomAccessVectorValues(vectors, dimension);
                        DefaultSearchScoreProvider ssp = DefaultSearchScoreProvider.exact(
                            queryVector, VectorSimilarityFunction.COSINE, ravv
                        );

                        // Step (c): Use custom Bits filter in search
                        int searchLimit = Math.min(limit * 10, validOrdinals.size());
                        SearchResult sr = searcher.search(ssp, searchLimit, radiusBits);

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

                                        // Skip if no location found
                                        if (w == null) {
                                            continue;
                                        }

                                        Location loc = KitsunePlatform.get().createLocation(w, x, y, z);

                                        // Step (d): Post-filter by actual Euclidean distance
                                        double actualDistance = center.distanceTo(loc);
                                        if (actualDistance > radius) {
                                            logger.fine("Container at " + loc + " is " + actualDistance
                                                + " blocks away, outside radius " + radius);
                                            continue;
                                        }

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

                                        logger.info(String.format("Search within radius result: %.1f%% match at %s (%.1f blocks away) - %s",
                                            score * 100, loc, actualDistance, preview));

                                        // Add result without deduplication
                                        org.aincraft.kitsune.model.SearchResult newResult =
                                            new org.aincraft.kitsune.model.SearchResult(
                                                loc, List.of(loc), score, preview, fullContent, containerPath
                                            );
                                        allMatches.add(newResult);
                                    }
                                }
                            }
                        }
                    }

                    // Sort by score and limit results
                    allMatches.sort((a, b) -> Double.compare(b.score(), a.score()));
                    List<org.aincraft.kitsune.model.SearchResult> finalResults = allMatches.subList(0, Math.min(limit, allMatches.size()));
                    logger.info("Search within radius complete: " + finalResults.size() + " results returned");
                    return finalResults;
                }

            } catch (Exception e) {
                logger.log(Level.WARNING, "Search within radius failed", e);
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
                    try {
                        // Delete orphan rows not tracked in memory
                        if (oldToNew.isEmpty()) {
                            // If no vectors in memory, delete all rows
                            conn.createStatement().executeUpdate("DELETE FROM container_chunks");
                        } else {
                            // Delete rows with ordinals not in oldToNew keySet
                            String ordinalsList = oldToNew.keySet().stream()
                                .map(String::valueOf)
                                .collect(java.util.stream.Collectors.joining(","));
                            String deleteSql = "DELETE FROM container_chunks WHERE ordinal NOT IN (" + ordinalsList + ")";
                            conn.createStatement().executeUpdate(deleteSql);
                        }

                        try (PreparedStatement stmt = conn.prepareStatement(
                                "UPDATE container_chunks SET ordinal = ? WHERE ordinal = ?")) {
                            // Two-phase update to avoid UNIQUE constraint conflicts:
                            // Phase 1: Offset all ordinals to negative (temporary)
                            for (var entry : oldToNew.entrySet()) {
                                int oldOrd = entry.getKey();
                                int newOrd = entry.getValue();
                                if (oldOrd != newOrd) {
                                    stmt.setInt(1, -oldOrd - 1); // temporary negative
                                    stmt.setInt(2, oldOrd);
                                    stmt.addBatch();
                                }
                            }
                            stmt.executeBatch();
                            stmt.clearBatch();

                            // Phase 2: Set final ordinals from temporary negatives
                            for (var entry : oldToNew.entrySet()) {
                                int oldOrd = entry.getKey();
                                int newOrd = entry.getValue();
                                if (oldOrd != newOrd) {
                                    stmt.setInt(1, newOrd);
                                    stmt.setInt(2, -oldOrd - 1); // from temporary
                                    stmt.addBatch();
                                }
                            }
                            stmt.executeBatch();
                        }
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

    /**
     * Gets all container IDs within a bounding box using R-tree spatial index.
     * Much faster than iterating all blocks for large radii.
     *
     * @param world World name to filter
     * @param centerX Center X coordinate
     * @param centerY Center Y coordinate
     * @param centerZ Center Z coordinate
     * @param radius Search radius
     * @return CompletableFuture with list of container IDs in bounding box
     */
    public CompletableFuture<List<UUID>> getContainersInRadius(String world, int centerX, int centerY, int centerZ, int radius) {
        return CompletableFuture.supplyAsync(() -> {
            indexLock.readLock().lock();
            try (Connection conn = dataSource.getConnection()) {
                // R-tree range query
                String sql = """
                    SELECT m.container_id
                    FROM container_locations_rtree r
                    JOIN container_rtree_map m ON r.id = m.rtree_id
                    WHERE m.world = ?
                    AND r.max_x >= ? AND r.min_x <= ?
                    AND r.max_y >= ? AND r.min_y <= ?
                    AND r.max_z >= ? AND r.min_z <= ?
                """;

                List<UUID> containers = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, world);
                    stmt.setInt(2, centerX - radius);  // min bound
                    stmt.setInt(3, centerX + radius);  // max bound
                    stmt.setInt(4, centerY - radius);
                    stmt.setInt(5, centerY + radius);
                    stmt.setInt(6, centerZ - radius);
                    stmt.setInt(7, centerZ + radius);

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            containers.add(UUID.fromString(rs.getString("container_id")));
                        }
                    }
                }

                logger.info("R-tree query found " + containers.size() + " containers in radius " + radius);
                return containers;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "R-tree query failed", e);
                return Collections.emptyList();
            } finally {
                indexLock.readLock().unlock();
            }
        }, executor);
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
            stmt.setString(1, location.getWorld().getName());
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
                    stmt.setString(1, location.getWorld().getName());
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
                indexLock.writeLock().lock();
                try {
                    rebuildIndex();
                } finally {
                    indexLock.writeLock().unlock();
                }
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
                    String key = primaryLoc.getWorld().getName() + ":" + primaryLoc.blockX() + "," + primaryLoc.blockY() + "," + primaryLoc.blockZ();
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
                            stmt.setString(2, loc.getWorld().getName());
                            stmt.setInt(3, loc.blockX());
                            stmt.setInt(4, loc.blockY());
                            stmt.setInt(5, loc.blockZ());
                            stmt.setInt(6, loc.equals(primaryLoc) ? 1 : 0);
                            stmt.addBatch();
                        }
                        stmt.executeBatch();
                    }

                    // Update R-tree spatial index
                    // First delete existing entry for this container+world
                    String deleteRtree = "DELETE FROM container_rtree_map WHERE container_id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(deleteRtree)) {
                        stmt.setString(1, containerId.toString());
                        stmt.executeUpdate();
                    }

                    // Calculate bounding box for all locations in this container
                    int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
                    int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
                    int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
                    String world = null;

                    for (Location loc : locations.allLocations()) {
                        world = loc.getWorld().getName();
                        minX = Math.min(minX, loc.blockX());
                        maxX = Math.max(maxX, loc.blockX());
                        minY = Math.min(minY, loc.blockY());
                        maxY = Math.max(maxY, loc.blockY());
                        minZ = Math.min(minZ, loc.blockZ());
                        maxZ = Math.max(maxZ, loc.blockZ());
                    }

                    if (world != null) {
                        // Insert into rtree
                        String insertRtree = "INSERT INTO container_locations_rtree (min_x, max_x, min_y, max_y, min_z, max_z) VALUES (?, ?, ?, ?, ?, ?)";
                        try (PreparedStatement stmt = conn.prepareStatement(insertRtree, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                            stmt.setInt(1, minX);
                            stmt.setInt(2, maxX);
                            stmt.setInt(3, minY);
                            stmt.setInt(4, maxY);
                            stmt.setInt(5, minZ);
                            stmt.setInt(6, maxZ);
                            stmt.executeUpdate();

                            try (ResultSet rs = stmt.getGeneratedKeys()) {
                                if (rs.next()) {
                                    long rtreeId = rs.getLong(1);
                                    // Insert mapping
                                    String insertMap = "INSERT INTO container_rtree_map (rtree_id, container_id, world) VALUES (?, ?, ?)";
                                    try (PreparedStatement mapStmt = conn.prepareStatement(insertMap)) {
                                        mapStmt.setLong(1, rtreeId);
                                        mapStmt.setString(2, containerId.toString());
                                        mapStmt.setString(3, world);
                                        mapStmt.executeUpdate();
                                    }
                                }
                            }
                        }
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
                    stmt.setString(1, anyPosition.getWorld().getName());
                    stmt.setInt(2, anyPosition.blockX());
                    stmt.setInt(3, anyPosition.blockY());
                    stmt.setInt(4, anyPosition.blockZ());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            Location loc = KitsunePlatform.get().createLocation(
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
                    stmt.setString(1, primaryLocation.getWorld().getName());
                    stmt.setInt(2, primaryLocation.blockX());
                    stmt.setInt(3, primaryLocation.blockY());
                    stmt.setInt(4, primaryLocation.blockZ());
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            locations.add(KitsunePlatform.get().createLocation(
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
                conn.setAutoCommit(false);
                try {
                    // Find container ID for this primary location
                    String selectSql = "SELECT container_id FROM container_locations WHERE world = ? AND x = ? AND y = ? AND z = ? AND is_primary = 1";
                    UUID containerId = null;
                    try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                        stmt.setString(1, primaryLocation.getWorld().getName());
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

                        // Clean up R-tree entry
                        String deleteRtreeMap = "DELETE FROM container_rtree_map WHERE container_id = ?";
                        try (PreparedStatement stmt = conn.prepareStatement(deleteRtreeMap)) {
                            stmt.setString(1, containerId.toString());
                            int deleted = stmt.executeUpdate();
                            if (deleted > 0) {
                                // Delete orphaned rtree entries
                                conn.createStatement().execute("""
                                    DELETE FROM container_locations_rtree
                                    WHERE id NOT IN (SELECT rtree_id FROM container_rtree_map)
                                """);
                            }
                        }
                    }

                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
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
                stmt.setString(2, loc.getWorld().getName());
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
            stmt.setString(1, location.getWorld().getName());
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
                            locations.add(KitsunePlatform.get().createLocation(
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
                            Location loc = KitsunePlatform.get().createLocation(
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

                    // Clean up R-tree entry
                    String deleteRtreeMap = "DELETE FROM container_rtree_map WHERE container_id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(deleteRtreeMap)) {
                        stmt.setString(1, containerId.toString());
                        int deleted = stmt.executeUpdate();
                        if (deleted > 0) {
                            // Delete orphaned rtree entries
                            conn.createStatement().execute("""
                                DELETE FROM container_locations_rtree
                                WHERE id NOT IN (SELECT rtree_id FROM container_rtree_map)
                            """);
                        }
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

    /**
     * Get the underlying HikariDataSource for shared database connections.
     * Used by other components (e.g., SearchHistoryStorage) to access the same SQLite database.
     *
     * @return the HikariDataSource
     */
    public HikariDataSource getDataSource() {
        return dataSource;
    }

    public CompletableFuture<Double> getThreshold() {
        return CompletableFuture.supplyAsync(() -> {
            indexLock.readLock().lock();
            try (Connection conn = dataSource.getConnection()) {
                String sql = "SELECT threshold FROM threshold_config WHERE id = 1";
                try (ResultSet rs = conn.createStatement().executeQuery(sql)) {
                    if (rs.next()) {
                        return rs.getDouble("threshold");
                    }
                }
                // Return default threshold if not found
                return 0.81;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to get threshold", e);
                return 0.81;
            } finally {
                indexLock.readLock().unlock();
            }
        }, executor);
    }

    public CompletableFuture<Void> setThreshold(double threshold) {
        return CompletableFuture.runAsync(() -> {
            indexLock.writeLock().lock();
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    // Check if row exists
                    String checkSql = "SELECT id FROM threshold_config WHERE id = 1";
                    boolean exists = false;
                    try (ResultSet rs = conn.createStatement().executeQuery(checkSql)) {
                        exists = rs.next();
                    }

                    if (exists) {
                        // Update existing row
                        String updateSql = "UPDATE threshold_config SET threshold = ? WHERE id = 1";
                        try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                            stmt.setDouble(1, threshold);
                            stmt.executeUpdate();
                        }
                    } else {
                        // Insert new row
                        String insertSql = "INSERT INTO threshold_config (id, threshold) VALUES (1, ?)";
                        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                            stmt.setDouble(1, threshold);
                            stmt.executeUpdate();
                        }
                    }

                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to set threshold", e);
                throw new RuntimeException("Threshold update failed", e);
            } finally {
                indexLock.writeLock().unlock();
            }
        }, executor);
    }
}
