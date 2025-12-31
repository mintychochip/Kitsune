package org.aincraft.kitsune.api;

import java.util.Set;

/**
 * Registry for external item tag providers.
 * Plugins can register custom tag providers to augment item embedding text.
 *
 * Thread-safe for concurrent access.
 */
public interface ItemTagProviderRegistry {
    /**
     * Register a tag provider for a plugin.
     * Multiple providers can be registered per plugin; all will be called.
     *
     * @param plugin The plugin registering the provider (used for organization)
     * @param provider The tag provider implementation
     * @throws NullPointerException if plugin or provider is null
     */
    void register(Object plugin, ItemTagProvider provider);

    /**
     * Unregister all tag providers for a plugin.
     * Typically called during plugin shutdown.
     *
     * @param plugin The plugin whose providers to unregister
     */
    void unregisterAll(Object plugin);

    /**
     * Collect all tags for an item from registered providers.
     * Calls all registered providers and aggregates their results.
     *
     * @param item The item to collect tags for
     * @return Set of all collected tags (never null; empty if no tags)
     */
    Set<String> collectTags(IndexableItem<?> item);
}
