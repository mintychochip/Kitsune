package org.aincraft.kitsune.model;

import org.aincraft.kitsune.api.LocationData;

public record SearchResult(
    LocationData location,
    double score,
    String preview,
    String fullContent
) {
    // Constructor with full content
    public SearchResult {
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }
        if (preview == null) {
            throw new IllegalArgumentException("Preview cannot be null");
        }
        if (score < 0 || score > 1) {
            throw new IllegalArgumentException("Score must be between 0 and 1");
        }
        // fullContent can be null for backwards compatibility
    }

    // Backwards compatible constructor
    public SearchResult(LocationData location, double score, String preview) {
        this(location, score, preview, preview);
    }
}
