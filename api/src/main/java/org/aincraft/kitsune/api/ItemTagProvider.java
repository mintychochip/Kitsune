package org.aincraft.kitsune.api;

import java.util.Collection;

/**
 * Plugin hook for registering custom item tag providers.
 * Implementations can augment item embedding text with custom tags.
 *
 * Tags should be simple, alphanumeric identifiers that describe
 * additional item properties or categories beyond the built-in tags.
 *
 * Example implementation:
 * <pre>{@code
 * public class MyTagProvider implements ItemTagProvider {
 *     public Collection<String> getTags(IndexableItem<?> item) {
 *         List<String> tags = new ArrayList<>();
 *         if (item.getString("customplugin:faction").isPresent()) {
 *             tags.add("factionitem");
 *         }
 *         return tags;
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface ItemTagProvider {
    /**
     * Get custom tags for an item.
     * Called during item indexing to augment embedding text.
     *
     * @param item The item to extract tags from
     * @return Collection of tag strings (should not be null; return empty collection if no tags)
     */
    Collection<String> getTags(IndexableItem<?> item);
}
