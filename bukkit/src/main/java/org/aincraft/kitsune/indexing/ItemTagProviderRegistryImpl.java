package org.aincraft.kitsune.indexing;

import com.google.common.base.Preconditions;
import org.aincraft.kitsune.api.IndexableItem;
import org.aincraft.kitsune.api.ItemTagProvider;
import org.aincraft.kitsune.api.ItemTagProviderRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe implementation of ItemTagProviderRegistry.
 * Uses ConcurrentHashMap to store providers by plugin.
 * Supports multiple providers per plugin.
 */
public class ItemTagProviderRegistryImpl implements ItemTagProviderRegistry {
    private final ConcurrentHashMap<Object, List<ItemTagProvider>> providers =
            new ConcurrentHashMap<>();

    @Override
    public void register(Object plugin, ItemTagProvider provider) {
        Preconditions.checkNotNull(plugin, "plugin cannot be null");
        Preconditions.checkNotNull(provider, "provider cannot be null");

        providers.computeIfAbsent(plugin, k -> new ArrayList<>())
                .add(provider);
    }

    @Override
    public void unregisterAll(Object plugin) {
        Preconditions.checkNotNull(plugin, "plugin cannot be null");
        providers.remove(plugin);
    }

    @Override
    public Set<String> collectTags(IndexableItem<?> item) {
        Preconditions.checkNotNull(item, "item cannot be null");

        Set<String> tags = new HashSet<>();
        for (List<ItemTagProvider> providerList : providers.values()) {
            for (ItemTagProvider provider : providerList) {
                try {
                    var collectedTags = provider.getTags(item);
                    if (collectedTags != null) {
                        tags.addAll(collectedTags);
                    }
                } catch (Exception e) {
                    // Silently skip providers that throw exceptions
                    // to prevent one bad provider from breaking indexing
                }
            }
        }
        return tags;
    }
}
