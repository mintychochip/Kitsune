package org.aincraft.kitsune.storage;

import com.google.gson.Gson;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.aincraft.kitsune.api.ContainerLocations;
import org.aincraft.kitsune.api.Location;
import org.aincraft.kitsune.config.KitsuneConfig;
import org.aincraft.kitsune.model.ContainerChunk;
import org.aincraft.kitsune.model.ContainerDocument;
import org.aincraft.kitsune.model.SearchResult;
import org.aincraft.kitsune.model.StorageStats;

public class SupabaseVectorStorage implements VectorStorage {
    private final Logger logger;
    private final KitsuneConfig config;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
    private final Gson gson = new Gson();
    private HikariDataSource dataSource;
    private final String tableName;

    public SupabaseVectorStorage(Logger logger, KitsuneConfig config) {
        this.logger = logger;
        this.config = config;
        this.tableName = config.getSupabaseTable();
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                String url = config.getSupabaseUrl();
                String key = config.getSupabaseKey();

                if (url == null || url.isEmpty() || key == null || key.isEmpty()) {
                    throw new IllegalStateException("Supabase URL and key must be configured");
                }

                String jdbcUrl = url.replace("https://", "")
                    .replace("http://", "");
                String[] parts = jdbcUrl.split("\\.");
                if (parts.length == 0) {
                    throw new IllegalStateException("Invalid Supabase URL format");
                }

                String projectId = parts[0];
                String connectionUrl = "jdbc:postgresql://" + projectId + ".db.supabase.co:5432/postgres";

                HikariConfig hikariConfig = new HikariConfig();
                hikariConfig.setJdbcUrl(connectionUrl);
                hikariConfig.setUsername("postgres");
                hikariConfig.setPassword(key);
                hikariConfig.setMaximumPoolSize(4);
                hikariConfig.setLeakDetectionThreshold(30000);

                dataSource = new HikariDataSource(hikariConfig);

                try (Connection conn = dataSource.getConnection()) {
                    conn.createStatement().execute("""
                        CREATE TABLE IF NOT EXISTS %s (
                            id BIGSERIAL PRIMARY KEY,
                            world TEXT NOT NULL,
                            x INTEGER NOT NULL,
                            y INTEGER NOT NULL,
                            z INTEGER NOT NULL,
                            chunk_index INTEGER NOT NULL DEFAULT 0,
                            content_text TEXT NOT NULL,
                            embedding vector(768),
                            timestamp BIGINT NOT NULL,
                            UNIQUE(world, x, y, z, chunk_index)
                        )
                        """.formatted(tableName));

                    conn.createStatement().execute("""
                        CREATE INDEX IF NOT EXISTS idx_%s_embedding
                        ON %s USING ivfflat (embedding vector_cosine_ops)
                        WITH (lists = 100)
                        """.formatted(tableName, tableName));

                    conn.createStatement().execute("""
                        CREATE INDEX IF NOT EXISTS idx_%s_location
                        ON %s (world, x, y, z)
                        """.formatted(tableName, tableName));

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

                logger.info("Supabase vector storage initialized");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to initialize Supabase storage", e);
                throw new RuntimeException("Supabase initialization failed", e);
            }
        }, executor);
    }

    @Override
    @Deprecated
    public CompletableFuture<Void> index(ContainerDocument document) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                String sql = """
                    INSERT INTO %s (world, x, y, z, chunk_index, content_text, embedding, timestamp)
                    VALUES (?, ?, ?, ?, 0, ?, ?::vector, ?)
                    ON CONFLICT (world, x, y, z, chunk_index) DO UPDATE
                    SET content_text = EXCLUDED.content_text,
                        embedding = EXCLUDED.embedding,
                        timestamp = EXCLUDED.timestamp
                    """.formatted(tableName);

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, document.location().worldName());
                    stmt.setInt(2, document.location().blockX());
                    stmt.setInt(3, document.location().blockY());
                    stmt.setInt(4, document.location().blockZ());
                    stmt.setString(5, document.contentText());
                    stmt.setString(6, embeddingToString(document.embedding()));
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
        throw new UnsupportedOperationException(
            "indexChunks(List<ContainerChunk>) is not supported. Use indexChunks(List<ContainerChunk>, Location) instead."
        );
    }

    @Override
    public CompletableFuture<Void> indexChunks(List<ContainerChunk> chunks, Location location) {
        if (chunks.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                // Delete all existing chunks for this location first
                String deleteSql = "DELETE FROM %s WHERE world = ? AND x = ? AND y = ? AND z = ?".formatted(tableName);
                try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                    stmt.setString(1, location.worldName());
                    stmt.setInt(2, location.blockX());
                    stmt.setInt(3, location.blockY());
                    stmt.setInt(4, location.blockZ());
                    stmt.executeUpdate();
                }

                // Insert all chunks
                String insertSql = """
                    INSERT INTO %s (world, x, y, z, chunk_index, content_text, embedding, timestamp)
                    VALUES (?, ?, ?, ?, ?, ?, ?::vector, ?)
                    """.formatted(tableName);

                try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    for (ContainerChunk chunk : chunks) {
                        stmt.setString(1, location.worldName());
                        stmt.setInt(2, location.blockX());
                        stmt.setInt(3, location.blockY());
                        stmt.setInt(4, location.blockZ());
                        stmt.setInt(5, chunk.chunkIndex());
                        stmt.setString(6, chunk.contentText());
                        stmt.setString(7, embeddingToString(chunk.embedding()));
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
                // Query all chunks, then deduplicate to get best match per location
                String sql = """
                    SELECT world, x, y, z, chunk_index, content_text,
                           embedding <=> ?::vector as distance
                    FROM %s
                    %s
                    ORDER BY distance ASC
                    LIMIT 10000
                    """.formatted(tableName, world != null ? "WHERE world = ?" : "");

                // Map to track best match per location: locationKey -> SearchResult
                java.util.Map<String, SearchResult> bestMatches = new java.util.HashMap<>();

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, embeddingToString(embedding));

                    if (world != null) {
                        stmt.setString(2, world);
                    }

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            double distance = rs.getDouble("distance");
                            double similarity = Math.max(0.0, 1 - distance);

                            Location loc = Location.of(
                                rs.getString("world"),
                                rs.getInt("x"),
                                rs.getInt("y"),
                                rs.getInt("z")
                            );

                            String preview = rs.getString("content_text");
                            if (preview.length() > 100) {
                                preview = preview.substring(0, 100) + "...";
                            }

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
                            SearchResult newResult = new SearchResult(loc, similarity, preview);
                            SearchResult existing = bestMatches.get(locationKey);

                            if (existing == null || similarity > existing.score()) {
                                bestMatches.put(locationKey, newResult);
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
    public CompletableFuture<Void> delete(Location location) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                String sql = """
                    DELETE FROM %s
                    WHERE world = ? AND x = ? AND y = ? AND z = ?
                    """.formatted(tableName);

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
                try (ResultSet rs = conn.createStatement()
                    .executeQuery("SELECT COUNT(*) as count FROM " + tableName)) {
                    if (rs.next()) {
                        long count = rs.getLong("count");
                        return new StorageStats(count, "Supabase");
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to get stats", e);
            }
            return new StorageStats(0, "Supabase");
        }, executor);
    }

    @Override
    public CompletableFuture<Void> purgeAll() {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.createStatement().execute("DELETE FROM " + tableName);
                logger.info("Purged all vectors from Supabase storage");
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
                    INSERT INTO container_positions
                    (world, x, y, z, primary_world, primary_x, primary_y, primary_z)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (world, x, y, z) DO UPDATE SET
                    primary_world = EXCLUDED.primary_world,
                    primary_x = EXCLUDED.primary_x,
                    primary_y = EXCLUDED.primary_y,
                    primary_z = EXCLUDED.primary_z
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    Location primary = locations.primaryLocation();
                    for (Location position : locations.allLocations()) {
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
    public CompletableFuture<Optional<Location>> getPrimaryLocation(Location anyPosition) {
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
                            Location primary = Location.of(
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
    public CompletableFuture<List<Location>> getAllPositions(Location primaryLocation) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                String sql = """
                    SELECT world, x, y, z
                    FROM container_positions
                    WHERE primary_world = ? AND primary_x = ? AND primary_y = ? AND primary_z = ?
                    """;

                List<Location> positions = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, primaryLocation.worldName());
                    stmt.setInt(2, primaryLocation.blockX());
                    stmt.setInt(3, primaryLocation.blockY());
                    stmt.setInt(4, primaryLocation.blockZ());

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            Location pos = Location.of(
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
    public CompletableFuture<Void> deleteContainerPositions(Location primaryLocation) {
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

    private String embeddingToString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            sb.append(embedding[i]);
            if (i < embedding.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public CompletableFuture<Void> indexChunks(UUID containerId, List<ContainerChunk> chunks) {
        // Stub implementation for SupabaseVectorStorage
        return CompletableFuture.completedFuture(null);
    }

    // Phase 2-3: Container management API implementations

    @Override
    public CompletableFuture<UUID> getOrCreateContainer(ContainerLocations locations) {
        // Stub implementation for SupabaseVectorStorage
        return CompletableFuture.completedFuture(UUID.randomUUID());
    }

    @Override
    public CompletableFuture<Optional<UUID>> getContainerByLocation(Location location) {
        // Stub implementation for SupabaseVectorStorage
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<List<Location>> getContainerLocations(UUID containerId) {
        // Stub implementation for SupabaseVectorStorage
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<Optional<Location>> getPrimaryLocationForContainer(UUID containerId) {
        // Stub implementation for SupabaseVectorStorage
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<Void> deleteContainer(UUID containerId) {
        // Stub implementation for SupabaseVectorStorage
        return CompletableFuture.completedFuture(null);
    }
}
