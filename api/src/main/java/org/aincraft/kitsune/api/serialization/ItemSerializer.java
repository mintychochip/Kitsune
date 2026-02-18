package org.aincraft.kitsune.api.serialization;

import org.aincraft.kitsune.api.indexing.SerializedItem;
import org.aincraft.kitsune.api.model.ContainerNode;

import java.util.List;

/**
 * Serializes items into embedding text and storage JSON.
 */
public interface ItemSerializer {
    /**
     * Serialize a list of items with backward compatibility.
     * Returns a flat list of serialized items.
     *
     * @param items The items to serialize
     * @return List of serialized items with embedding text and storage JSON
     */
    List<SerializedItem> serialize(List<?> items);

    /**
     * Serialize items with tree structure preservation.
     * Returns a root ContainerNode representing the container hierarchy.
     *
     * @param items The items to serialize
     * @return ContainerNode root with children and items
     */
    ContainerNode serializeTree(List<?> items);

    /**
     * Serialize items from an array.
     *
     * @param items The items to serialize
     * @return List of serialized items
     */
    default List<SerializedItem> serialize(Object[] items) {
        return serialize(List.of(items));
    }

    /**
     * Serialize items from an array with tree structure.
     *
     * @param items The items to serialize
     * @return ContainerNode root with children and items
     */
    default ContainerNode serializeTree(Object[] items) {
        return serializeTree(List.of(items));
    }
}
