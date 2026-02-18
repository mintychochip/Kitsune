package org.aincraft.kitsune.api.serialization;

import java.util.Set;
import org.aincraft.kitsune.Item;

/**
 * Registry for tag providers.
 * All providers run on all items and self-select based on their logic.
 * Thread-safe for concurrent access.
 *
 * Sealed interface that only permits DefaultTagProviderRegistry as an implementation.
 */
public sealed interface TagProviderRegistry permits DefaultTagProviderRegistry {

    static TagProviderRegistry createRegistry() {
        return new DefaultTagProviderRegistry();
    }
    /**
     * Register a tag provider.
     *
     * @param provider The tag provider
     * @throws NullPointerException if provider is null
     */
    void register(TagProvider provider);

    /**
     * Unregister a specific provider.
     *
     * @param provider The provider to remove
     */
    void unregister(TagProvider provider);

    /**
     * Collect all tags for an item from all registered providers.
     *
     * @param item The item context
     * @return Set of all collected tags (never null)
     */
    Set<String> collectTags(Item item);
}
