package org.aincraft.kitsune.storage;

import org.aincraft.kitsune.api.LocationData;
import org.aincraft.kitsune.model.ContainerChunk;
import org.aincraft.kitsune.model.ContainerDocument;
import org.aincraft.kitsune.model.SearchResult;
import org.aincraft.kitsune.model.StorageStats;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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
     * @deprecated Use indexChunks for chunked storage
     */
    @Deprecated
    CompletableFuture<Void> index(ContainerDocument document);

    /**
     * Indexes container chunks (multiple embeddings for one container).
     * Deletes existing chunks for this location first.
     * @param chunks The container chunks to index
     * @return CompletableFuture that completes when indexing is done
     */
    CompletableFuture<Void> indexChunks(List<ContainerChunk> chunks);

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
    CompletableFuture<Void> delete(LocationData location);

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
}
