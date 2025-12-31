package org.aincraft.chestfind.model;

import org.aincraft.chestfind.api.LocationData;
import java.util.List;

public record SearchResult(
    LocationData location,
    List<LocationData> allLocations,
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
        // allLocations defaults to single location if not provided
        if (allLocations == null) {
            allLocations = List.of(location);
        }
        // fullContent can be null for backwards compatibility
    }

    // Backwards compatible constructor without allLocations
    public SearchResult(LocationData location, double score, String preview, String fullContent) {
        this(location, List.of(location), score, preview, fullContent);
    }

    // Backwards compatible constructor with preview only
    public SearchResult(LocationData location, double score, String preview) {
        this(location, List.of(location), score, preview, preview);
    }
}
