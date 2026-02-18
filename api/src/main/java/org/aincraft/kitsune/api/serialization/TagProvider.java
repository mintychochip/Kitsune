package org.aincraft.kitsune.api.serialization;

import java.util.Collection;
import org.aincraft.kitsune.Item;

/**
 * Provides custom tags for items during serialization.
 * Implementations can add tags based on item properties or external data.
 */
@FunctionalInterface
public interface TagProvider {
    /**
     * Appends tags for an item to the provided collection.
     *
     * @param tags The collection to append tags to
     * @param item The item context to extract tags from
     */
    void appendTags(Collection<String> tags, Item item);
}
