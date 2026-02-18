package org.aincraft.kitsune.api.serialization;

import org.aincraft.kitsune.api.indexing.SerializedItem;

import java.util.List;

/**
 * Serializes items into embedding text and storage JSON.
 */
public interface ItemSerializer {
    /**
     * Serialize a list of items.
     *
     * @param items The items to serialize
     * @return List of serialized items with embedding text and storage JSON
     */
    List<SerializedItem> serialize(List<?> items);

    /**
     * Serialize items from an array.
     *
     * @param items The items to serialize
     * @return List of serialized items
     */
    default List<SerializedItem> serialize(Object[] items) {
        return serialize(List.of(items));
    }
}
