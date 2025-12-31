package org.aincraft.kitsune.model;

import com.google.common.base.Preconditions;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an indexed container chunk with an explicit application-generated UUID.
 * This is the primary unit of storage in the vector storage system.
 * Each indexed item has a unique ID that references back to its container.
 * Now includes optional container path for nested container indexing.
 */
public record IndexedChunk(
    UUID id,
    UUID containerId,
    int chunkIndex,
    String contentText,
    float[] embedding,
    long timestamp,
    @Nullable ContainerPath containerPath
) {
    public IndexedChunk {
        Preconditions.checkNotNull(id, "ID cannot be null");
        Preconditions.checkNotNull(containerId, "Container ID cannot be null");
        Preconditions.checkNotNull(contentText, "Content text cannot be null");
        Preconditions.checkNotNull(embedding, "Embedding cannot be null");
        Preconditions.checkArgument(chunkIndex >= 0, "Chunk index must be non-negative");
        // containerPath can be null for backwards compatibility
    }

    /**
     * Backwards-compatible constructor without containerPath.
     */
    public IndexedChunk(
        UUID id,
        UUID containerId,
        int chunkIndex,
        String contentText,
        float[] embedding,
        long timestamp
    ) {
        this(id, containerId, chunkIndex, contentText, embedding, timestamp, null);
    }

    /**
     * Factory method to create an IndexedChunk from a ContainerChunk.
     * Generates a new random UUID for the chunk.
     *
     * @param chunk the ContainerChunk to wrap
     * @return a new IndexedChunk with a generated UUID
     */
    public static IndexedChunk fromContainerChunk(ContainerChunk chunk) {
        Preconditions.checkNotNull(chunk, "Chunk cannot be null");
        return new IndexedChunk(
            UUID.randomUUID(),
            chunk.containerId(),
            chunk.chunkIndex(),
            chunk.contentText(),
            chunk.embedding(),
            chunk.timestamp(),
            chunk.containerPath()
        );
    }

    /**
     * Factory method to create an IndexedChunk with a specific UUID.
     *
     * @param id the UUID to use
     * @param chunk the ContainerChunk to wrap
     * @return a new IndexedChunk with the specified UUID
     */
    public static IndexedChunk fromContainerChunk(UUID id, ContainerChunk chunk) {
        Preconditions.checkNotNull(id, "ID cannot be null");
        Preconditions.checkNotNull(chunk, "Chunk cannot be null");
        return new IndexedChunk(
            id,
            chunk.containerId(),
            chunk.chunkIndex(),
            chunk.contentText(),
            chunk.embedding(),
            chunk.timestamp(),
            chunk.containerPath()
        );
    }

    /**
     * Returns the underlying ContainerChunk (without ID).
     *
     * @return a ContainerChunk with the same data
     */
    public ContainerChunk toContainerChunk() {
        return new ContainerChunk(containerId, chunkIndex, contentText, embedding, timestamp, containerPath);
    }

    /**
     * Creates a copy with an updated embedding.
     *
     * @param newEmbedding the new embedding vector
     * @return a new IndexedChunk with the updated embedding
     */
    public IndexedChunk withEmbedding(float[] newEmbedding) {
        return new IndexedChunk(id, containerId, chunkIndex, contentText, newEmbedding, timestamp, containerPath);
    }

    /**
     * Creates a copy with an updated timestamp.
     *
     * @param newTimestamp the new timestamp
     * @return a new IndexedChunk with the updated timestamp
     */
    public IndexedChunk withTimestamp(long newTimestamp) {
        return new IndexedChunk(id, containerId, chunkIndex, contentText, embedding, newTimestamp, containerPath);
    }

    /**
     * Creates a copy with an updated container path.
     *
     * @param newContainerPath the new container path
     * @return a new IndexedChunk with the updated container path
     */
    public IndexedChunk withContainerPath(@Nullable ContainerPath newContainerPath) {
        return new IndexedChunk(id, containerId, chunkIndex, contentText, embedding, timestamp, newContainerPath);
    }
}
