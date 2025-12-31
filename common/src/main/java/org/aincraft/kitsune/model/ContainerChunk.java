package org.aincraft.kitsune.model;

import org.aincraft.kitsune.api.LocationData;

/**
 * Represents a chunk of a container's contents with its embedding.
 * Large containers are split into multiple chunks to respect token limits.
 */
public record ContainerChunk(
    LocationData location,
    int chunkIndex,
    String contentText,
    float[] embedding,
    long timestamp
) {
    public ContainerChunk {
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }
        if (contentText == null) {
            throw new IllegalArgumentException("Content text cannot be null");
        }
        if (embedding == null) {
            throw new IllegalArgumentException("Embedding cannot be null");
        }
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("Chunk index must be non-negative");
        }
    }
}
