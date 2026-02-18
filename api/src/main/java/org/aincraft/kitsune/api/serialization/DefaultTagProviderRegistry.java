package org.aincraft.kitsune.api.serialization;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.aincraft.kitsune.Item;

/**
 * Thread-safe implementation of TagProviderRegistry. Uses CopyOnWriteArrayList
 * for thread-safe concurrent access.
 * <p>
 * This is the final implementation of the sealed TagProviderRegistry interface.
 */
final class DefaultTagProviderRegistry implements TagProviderRegistry {

  private final CopyOnWriteArrayList<TagProvider> providers = new CopyOnWriteArrayList<>();

  DefaultTagProviderRegistry() {

  }

  @Override
  public void register(TagProvider provider) {
    Objects.requireNonNull(provider, "provider cannot be null");
    providers.add(provider);
  }

  @Override
  public void unregister(TagProvider provider) {
    Objects.requireNonNull(provider, "provider cannot be null");
    providers.remove(provider);
  }

  @Override
  public Set<String> collectTags(Item item) {
    Objects.requireNonNull(item, "item cannot be null");

    Set<String> tags = new HashSet<>();
    for (var provider : providers) {
      try {
        provider.appendTags(tags, item);
      } catch (Exception e) {
        // Silently skip providers that throw exceptions
        // to prevent one bad provider from breaking indexing
      }
    }
    return tags;
  }
}
