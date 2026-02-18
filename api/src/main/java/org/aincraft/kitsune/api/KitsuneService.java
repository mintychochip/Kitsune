package org.aincraft.kitsune.api;

import org.aincraft.kitsune.api.serialization.TagProviderRegistry;
import org.aincraft.kitsune.api.serialization.ItemSerializer;

/**
 * Service locator for Kitsune platform services.
 * Platform implementations register their services at startup.
 * External plugins can retrieve services without type parameters.
 */
public final class KitsuneService {
    private static volatile TagProviderRegistry tagRegistry;
    private static volatile ItemSerializer itemSerializer;

    private KitsuneService() {}

    /**
     * Register platform services. Called by platform implementation during startup.
     */
    public static void register(TagProviderRegistry registry, ItemSerializer serializer) {
        tagRegistry = registry;
        itemSerializer = serializer;
    }

    /**
     * Get the tag provider registry.
     *
     * @return The tag provider registry
     * @throws IllegalStateException if Kitsune is not initialized
     */
    public static TagProviderRegistry getTagRegistry() {
        if (tagRegistry == null) {
            throw new IllegalStateException("Kitsune not initialized");
        }
        return tagRegistry;
    }

    /**
     * Get the item serializer.
     *
     * @return The item serializer
     * @throws IllegalStateException if Kitsune is not initialized
     */
    public static ItemSerializer getItemSerializer() {
        if (itemSerializer == null) {
            throw new IllegalStateException("Kitsune not initialized");
        }
        return itemSerializer;
    }

    /**
     * Check if Kitsune is initialized.
     */
    public static boolean isInitialized() {
        return tagRegistry != null && itemSerializer != null;
    }

    /**
     * Clear services. Called during platform shutdown.
     */
    public static void unregister() {
        tagRegistry = null;
        itemSerializer = null;
    }
}
