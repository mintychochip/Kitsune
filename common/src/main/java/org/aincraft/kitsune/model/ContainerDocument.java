package org.aincraft.kitsune.model;

import org.aincraft.kitsune.api.LocationData;

public record ContainerDocument(
    LocationData location,
    String contentText,
    float[] embedding,
    long timestamp
) {
    public ContainerDocument {
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }
        if (contentText == null) {
            throw new IllegalArgumentException("Content text cannot be null");
        }
        if (embedding == null) {
            throw new IllegalArgumentException("Embedding cannot be null");
        }
    }
}
