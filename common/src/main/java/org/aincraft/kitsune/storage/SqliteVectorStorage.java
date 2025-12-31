package org.aincraft.kitsune.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.aincraft.kitsune.api.ContainerLocations;
import org.aincraft.kitsune.api.LocationData;
import org.aincraft.kitsune.model.ContainerChunk;
import org.aincraft.kitsune.model.ContainerDocument;
import org.aincraft.kitsune.model.SearchResult;
import org.aincraft.kitsune.model.StorageStats;

public class SqliteVectorStorage implements VectorStorage {
    private final Logger logger;
    private final String dbPath;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
    private HikariDataSource dataSource;

    public SqliteVectorStorage(Logger logger, String dbPath) {
        this.logger = logger;
        this.dbPath = dbPath;
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                // Load SQLite driver from Paper's bundled version
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
                config.setMaximumPoolSize(1); // SQLite only supports one writer
                config.setLeakDetectionThreshold(30000);

                dataSource = new HikariDataSource(config);

                try (Connection conn = dataSource.getConnection()) {
                    conn.createStatement().execute("""
                        CREATE TABLE IF NOT EXISTS containers (
                            world TEXT NOT NULL,
                            x INTEGER NOT NULL,
                            y INTEGER NOT NULL,
                            z INTEGER NOT NULL,
                            chunk_index INTEGER NOT NULL DEFAULT 0,
                            content_text TEXT NOT NULL,
                            embedding BLOB NOT NULL,
                            timestamp INTEGER NOT NULL,
                            PRIMARY KEY (world, x, y, z, chunk_index)
                        )
                        """);
                    conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_timestamp ON containers(timestamp)");
                    conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_location ON containers(world, x, y, z)");

                    conn.createStatement().execute("""
                        CREATE TABLE IF NOT EXISTS container_positions (
                            world TEXT NOT NULL,
                            x INTEGER NOT NULL,
                            y INTEGER NOT NULL,
                            z INTEGER NOT NULL,
                            primary_world TEXT NOT NULL,
                            primary_x INTEGER NOT NULL,
                            primary_y INTEGER NOT NULL,
                            primary_z INTEGER NOT NULL,
                            PRIMARY KEY (world, x, y, z)
                        )
                        """);
                    conn.createStatement().execute("""
                        CREATE INDEX IF NOT EXISTS idx_container_positions_primary
                        ON container_positions(primary_world, primary_x, primary_y, primary_z)
                        """);
                }

                logger.info("SQLite storage initialized at " + dbFile.toAbsolutePath());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to initialize SQLite storage", e);
                throw new RuntimeException("SQLite initialization failed", e);
            }
        }, executor);
    }

    @Override
    @Deprecated
    public CompletableFuture<Void> index(ContainerDocument document) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                String sql = """
                    INSERT OR REPLACE INTO containers
                    (world, x, y, z, chunk_index, content_text, embedding, timestamp)
                    VALUES (?, ?, ?, ?, 0, ?, ?, ?)
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, document.location().worldName());
                    stmt.setInt(2, document.location().blockX());
                    stmt.setInt(3, document.location().blockY());
                    stmt.setInt(4, document.location().blockZ());
                    stmt.setString(5, document.contentText());
                    try {
                        stmt.setBytes(6, serializeEmbedding(document.embedding()));
                    } catch (Exception e) {
                        throw new SQLException("Failed to serialize embedding", e);
                    }
                    stmt.setLong(7, document.timestamp());
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to index container", e);
                throw new RuntimeException("Indexing failed", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> indexChunks(List<ContainerChunk> chunks) {
        if (chunks.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                // Delete all existing chunks for this location first
                LocationData loc = chunks.get(0).location();
                String deleteSql = "DELETE FROM containers WHERE world = ? AND x = ? AND y = ? AND z = ?";
                try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                    stmt.setString(1, loc.worldName());
                    stmt.setInt(2, loc.blockX());
                    stmt.setInt(3, loc.blockY());
                    stmt.setInt(4, loc.blockZ());
                    stmt.executeUpdate();
                }

                // Insert all chunks
                String insertSql = """
                    INSERT INTO containers
                    (world, x, y, z, chunk_index, content_text, embedding, timestamp)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    for (ContainerChunk chunk : chunks) {
                        stmt.setString(1, chunk.location().worldName());
                        stmt.setInt(2, chunk.location().blockX());
                        stmt.setInt(3, chunk.location().blockY());
                        stmt.setInt(4, chunk.location().blockZ());
                        stmt.setInt(5, chunk.chunkIndex());
                        stmt.setString(6, chunk.contentText());
                        try {
                            stmt.setBytes(7, serializeEmbedding(chunk.embedding()));
                        } catch (Exception e) {
                            throw new SQLException("Failed to serialize embedding", e);
                        }
                        stmt.setLong(8, chunk.timestamp());
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to index chunks", e);
                throw new RuntimeException("Chunk indexing failed", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<SearchResult>> search(float[] embedding, int limit, String world) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                String sql = """
                    SELECT world, x, y, z, chunk_index, content_text, embedding
                    FROM containers
                    """ + (world != null ? "WHERE world = ? " : "") +
                    "ORDER BY timestamp DESC LIMIT 10000";

                // Map to track best match per location: locationKey -> SearchResult
                java.util.Map<String, SearchResult> bestMatches = new java.util.HashMap<>();

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    if (world != null) {
                        stmt.setString(1, world);
                    }

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            try {
                                float[] storedEmbedding = deserializeEmbedding(rs.getBytes("embedding"));
                                double similarity = cosineSimilarity(embedding, storedEmbedding);

                                LocationData loc = LocationData.of(
                                    rs.getString("world"),
                                    rs.getInt("x"),
                                    rs.getInt("y"),
                                    rs.getInt("z")
                                );

                                String fullContent = rs.getString("content_text");
                                String preview = fullContent.length() > 100
                                    ? fullContent.substring(0, 100) + "..."
                                    : fullContent;

                                // Log similarity score
                                logger.info(String.format("Similarity %.2f%% for %s: %s",
                                    similarity * 100, loc, preview));

                                // Create unique key for this location
                                String locationKey = String.format("%s:%d:%d:%d",
                                    rs.getString("world"),
                                    rs.getInt("x"),
                                    rs.getInt("y"),
                                    rs.getInt("z"));

                                // Keep only the best matching chunk for each location
                                SearchResult newResult = new SearchResult(loc, similarity, preview, fullContent);
                                SearchResult existing = bestMatches.get(locationKey);

                                if (existing == null || similarity > existing.score()) {
                                    bestMatches.put(locationKey, newResult);
                                }
                            } catch (Exception e) {
                                logger.log(Level.WARNING, "Failed to deserialize embedding", e);
                            }
                        }
                    }
                }

                // Convert to list and sort by score
                List<SearchResult> results = new ArrayList<>(bestMatches.values());
                Collections.sort(results, (a, b) -> Double.compare(b.score(), a.score()));
                return results.subList(0, Math.min(limit, results.size()));
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Search failed", e);
                return Collections.emptyList();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> delete(LocationData location) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                String sql = "DELETE FROM containers WHERE world = ? AND x = ? AND y = ? AND z = ?";

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, location.worldName());
                    stmt.setInt(2, location.blockX());
                    stmt.setInt(3, location.blockY());
                    stmt.setInt(4, location.blockZ());
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to delete container", e);
                throw new RuntimeException("Deletion failed", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<StorageStats> getStats() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                try (ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) as count FROM containers")) {
                    if (rs.next()) {
                        long count = rs.getLong("count");
                        return new StorageStats(count, "SQLite");
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to get stats", e);
            }
            return new StorageStats(0, "SQLite");
        }, executor);
    }

    @Override
    public CompletableFuture<Void> purgeAll() {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.createStatement().execute("DELETE FROM containers");
                logger.info("Purged all vectors from SQLite storage");
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to purge all vectors", e);
                throw new RuntimeException("Purge failed", e);
            }
        }, executor);
    }

    @Override
    public void shutdown() {
        if (dataSource != null) {
            dataSource.close();
        }
        executor.shutdown();
    }

    @Override
    public CompletableFuture<Void> registerContainerPositions(ContainerLocations locations) {
        // For single-block containers, no mapping needed
        if (!locations.isMultiBlock()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                String sql = """
                    INSERT OR REPLACE INTO container_positions
                    (world, x, y, z, primary_world, primary_x, primary_y, primary_z)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    LocationData primary = locations.primaryLocation();
                    for (LocationData position : locations.allLocations()) {
                        stmt.setString(1, position.worldName());
                        stmt.setInt(2, position.blockX());
                        stmt.setInt(3, position.blockY());
                        stmt.setInt(4, position.blockZ());
                        stmt.setString(5, primary.worldName());
                        stmt.setInt(6, primary.blockX());
                        stmt.setInt(7, primary.blockY());
                        stmt.setInt(8, primary.blockZ());
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to register container positions", e);
                throw new RuntimeException("Position registration failed", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<LocationData>> getPrimaryLocation(LocationData anyPosition) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                String sql = """
                    SELECT primary_world, primary_x, primary_y, primary_z
                    FROM container_positions
                    WHERE world = ? AND x = ? AND y = ? AND z = ?
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, anyPosition.worldName());
                    stmt.setInt(2, anyPosition.blockX());
                    stmt.setInt(3, anyPosition.blockY());
                    stmt.setInt(4, anyPosition.blockZ());

                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            LocationData primary = LocationData.of(
                                rs.getString("primary_world"),
                                rs.getInt("primary_x"),
                                rs.getInt("primary_y"),
                                rs.getInt("primary_z")
                            );
                            return Optional.of(primary);
                        }
                    }
                }
                // If not found in mapping, return the input position (single-block case)
                return Optional.of(anyPosition);
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to get primary location", e);
                return Optional.of(anyPosition);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<LocationData>> getAllPositions(LocationData primaryLocation) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                String sql = """
                    SELECT world, x, y, z
                    FROM container_positions
                    WHERE primary_world = ? AND primary_x = ? AND primary_y = ? AND primary_z = ?
                    """;

                List<LocationData> positions = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, primaryLocation.worldName());
                    stmt.setInt(2, primaryLocation.blockX());
                    stmt.setInt(3, primaryLocation.blockY());
                    stmt.setInt(4, primaryLocation.blockZ());

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            LocationData pos = LocationData.of(
                                rs.getString("world"),
                                rs.getInt("x"),
                                rs.getInt("y"),
                                rs.getInt("z")
                            );
                            positions.add(pos);
                        }
                    }
                }

                // If no mappings found, return just the primary location (single-block case)
                if (positions.isEmpty()) {
                    return List.of(primaryLocation);
                }
                return positions;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to get all positions", e);
                return List.of(primaryLocation);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deleteContainerPositions(LocationData primaryLocation) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                String sql = """
                    DELETE FROM container_positions
                    WHERE primary_world = ? AND primary_x = ? AND primary_y = ? AND primary_z = ?
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, primaryLocation.worldName());
                    stmt.setInt(2, primaryLocation.blockX());
                    stmt.setInt(3, primaryLocation.blockY());
                    stmt.setInt(4, primaryLocation.blockZ());
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to delete container positions", e);
                throw new RuntimeException("Position deletion failed", e);
            }
        }, executor);
    }

    private byte[] serializeEmbedding(float[] embedding) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(embedding.length);
        for (float v : embedding) {
            dos.writeFloat(v);
        }
        dos.close();
        return baos.toByteArray();
    }

    private float[] deserializeEmbedding(byte[] data) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);
        int length = dis.readInt();
        float[] result = new float[length];
        for (int i = 0; i < length; i++) {
            result[i] = dis.readFloat();
        }
        dis.close();
        return result;
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        if (denominator == 0.0) {
            return 0.0;
        }

        return dotProduct / denominator;
    }
}
