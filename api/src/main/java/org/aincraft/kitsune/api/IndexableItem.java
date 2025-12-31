package org.aincraft.kitsune.api;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Optional;

/**
 * Represents an item that can be indexed and augmented with custom tags.
 * Provides a platform-agnostic interface for accessing item properties.
 * Implementations wrap platform-specific item representations (e.g., Bukkit ItemStack).
 *
 * <h2>Identity Contract</h2>
 * Implementations must override {@link #hashCode()} and {@link #equals(Object)}
 * based on content properties. Amount is excluded - two items with same content
 * but different amounts are equal. This enables embedding caching and deduplication.
 *
 * @param <S> The underlying platform-specific item stack type (e.g., org.bukkit.inventory.ItemStack)
 */
public interface IndexableItem<S> {
    /**
     * Get the stack amount.
     */
    int amount();

    /**
     * Get the display name of the item.
     */
    Optional<Component> displayName();

    /**
     * Get the lore lines.
     */
    List<Component> lore();

    /**
     * Unwrap the underlying platform-specific item stack.
     *
     * @return The underlying item stack
     */
    S unwrap();

    /**
     * Compute a stable content hash for embedding caching.
     * Deterministic across JVM restarts - identical items produce identical hashes.
     *
     * @return Stable string hash of item content
     */
    String contentHashCode();
}
