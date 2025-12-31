package org.aincraft.kitsune.indexing;

/**
 * Holds both the plain text (for embedding) and JSON (for storage) representations of an item.
 */
public record SerializedItem(String embeddingText, String storageJson) {
}
