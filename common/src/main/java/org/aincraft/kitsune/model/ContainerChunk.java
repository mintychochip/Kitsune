package org.aincraft.kitsune.model;

import com.google.common.base.Preconditions;
import org.aincraft.kitsune.api.LocationData;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a chunk of a container's contents with its embedding.
 * Large containers are split into multiple chunks to respect token limits.
 *
 * Can optionally include the path through nested containers where the item was found.
 */
public record ContainerChunk(
    LocationData location,
    int chunkIndex,
    String contentText,
    float[] embedding,
    long timestamp,
    @Nullable ContainerPath containerPath
) {
    public ContainerChunk {
        Preconditions.checkNotNull(location, "Location cannot be null");
        Preconditions.checkNotNull(contentText, "Content text cannot be null");
        Preconditions.checkNotNull(embedding, "Embedding cannot be null");
        Preconditions.checkArgument(chunkIndex >= 0, "Chunk index must be non-negative");
    }

    /**
     * Constructor for backward compatibility without containerPath.
     */
    public ContainerChunk(
        LocationData location,
        int chunkIndex,
        String contentText,
        float[] embedding,
        long timestamp
    ) {
        this(location, chunkIndex, contentText, embedding, timestamp, null);
    }
}
