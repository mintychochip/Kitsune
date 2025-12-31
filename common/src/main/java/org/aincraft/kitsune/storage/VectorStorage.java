package org.aincraft.kitsune.storage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.aincraft.kitsune.api.ContainerLocations;
import org.aincraft.kitsune.api.Location;
import org.aincraft.kitsune.model.ContainerChunk;
import org.aincraft.kitsune.model.ContainerDocument;
import org.aincraft.kitsune.model.SearchResult;
import org.aincraft.kitsune.model.StorageStats;

public interface VectorStorage {
    /**
     * Initializes the vector storage system.
     * @return CompletableFuture that completes when initialization is done
     */
    CompletableFuture<Void> initialize();

    /**
     * Indexes a container document.
     * @param document The container document to index
     * @return CompletableFuture that completes when indexing is done
     * @deprecated Use indexChunks(UUID, List) instead
     */
    @Deprecated
    CompletableFuture<Void> index(ContainerDocument document);

    /**
     * Indexes container chunks (multiple embeddings for one container).
     * Deletes existing chunks for this location first.
     * @param chunks The container chunks to index
     * @return CompletableFuture that completes when indexing is done
     * @deprecated Use indexChunks(UUID, List) instead
     */
    @Deprecated
    CompletableFuture<Void> indexChunks(List<ContainerChunk> chunks);

    /**
     * Indexes container chunks with explicit location context.
     * Required for Phase 1 where chunks no longer carry location information.
     * Deletes existing chunks for this location first.
     * @param chunks The container chunks to index
     * @param location The location context for these chunks
     * @return CompletableFuture that completes when indexing is done
     * @deprecated Use indexChunks(UUID, List) instead
     */
    @Deprecated
    CompletableFuture<Void> indexChunks(List<ContainerChunk> chunks, Location location);

    /**
     * Indexes chunks for a specific container by UUID.
     * Deletes existing chunks for this container first.
     * @param containerId The UUID of the container
     * @param chunks The container chunks to index
     * @return CompletableFuture that completes when indexing is done
     */
    CompletableFuture<Void> indexChunks(UUID containerId, List<ContainerChunk> chunks);

    /**
     * Searches for containers by embedding similarity.
     * @param embedding The query embedding
     * @param limit Maximum number of results
     * @param world Optional world filter (null for all worlds)
     * @return CompletableFuture containing search results
     */
    CompletableFuture<List<SearchResult>> search(float[] embedding, int limit, String world);

    /**
     * Deletes a container from the index (all chunks).
     * @param location The location of the container
     * @return CompletableFuture that completes when deletion is done
     */
    CompletableFuture<Void> delete(Location location);

    /**
     * Gets storage statistics.
     * @return CompletableFuture containing storage stats
     */
    CompletableFuture<StorageStats> getStats();

    /**
     * Purges all vectors from storage.
     * @return CompletableFuture that completes when purge is done
     */
    CompletableFuture<Void> purgeAll();

    /**
     * Shuts down the storage and releases resources.
     */
    void shutdown();

    /**
     * Registers position mappings for a multi-block container.
     * @param locations The container locations containing primary and all positions
     * @return CompletableFuture that completes when registration is done
     */
    CompletableFuture<Void> registerContainerPositions(ContainerLocations locations);

    /**
     * Looks up the primary/canonical position from any position.
     * @param anyPosition Any position of the container
     * @return CompletableFuture containing the primary location, or empty if not found
     */
    CompletableFuture<Optional<Location>> getPrimaryLocation(Location anyPosition);

    /**
     * Gets all positions for a container given its primary location.
     * @param primaryLocation The primary location of the container
     * @return CompletableFuture containing all positions for this container
     */
    CompletableFuture<List<Location>> getAllPositions(Location primaryLocation);

    /**
     * Deletes position mappings for a container.
     * @param primaryLocation The primary location of the container
     * @return CompletableFuture that completes when deletion is done
     */
    CompletableFuture<Void> deleteContainerPositions(Location primaryLocation);

    // Phase 2-3: Container management API

    /**
     * Gets or creates a container by its locations.
     * Looks up an existing container by any of its locations, or creates a new one if not found.
     * @param locations The container locations
     * @return CompletableFuture containing the container UUID
     */
    CompletableFuture<UUID> getOrCreateContainer(ContainerLocations locations);

    /**
     * Looks up a container UUID by any of its locations.
     * @param location Any location of the container
     * @return CompletableFuture containing the container UUID, or empty if not found
     */
    CompletableFuture<Optional<UUID>> getContainerByLocation(Location location);

    /**
     * Gets all locations for a container.
     * @param containerId The container UUID
     * @return CompletableFuture containing all locations for this container
     */
    CompletableFuture<List<Location>> getContainerLocations(UUID containerId);

    /**
     * Gets the primary location for a container.
     * @param containerId The container UUID
     * @return CompletableFuture containing the primary location, or empty if container not found
     */
    CompletableFuture<Optional<Location>> getPrimaryLocationForContainer(UUID containerId);

    /**
     * Deletes a container and all its chunks by UUID.
     * @param containerId The container UUID
     * @return CompletableFuture that completes when deletion is done
     */
    CompletableFuture<Void> deleteContainer(UUID containerId);
}
