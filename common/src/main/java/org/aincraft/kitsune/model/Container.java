package org.aincraft.kitsune.model;

import com.google.common.base.Preconditions;
import java.util.UUID;

/**
 * Represents a container entity with a unique identifier.
 * A container is a logical grouping that can span multiple block locations.
 */
public record Container(UUID id, long createdAt) {
    public Container {
        Preconditions.checkNotNull(id, "ID cannot be null");
        Preconditions.checkArgument(createdAt > 0, "createdAt must be positive");
    }

    public static Container create() {
        return new Container(UUID.randomUUID(), System.currentTimeMillis());
    }

    public static Container of(UUID id, long createdAt) {
        return new Container(id, createdAt);
    }
}
