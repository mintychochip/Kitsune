package org.aincraft.kitsune.indexing;

import com.google.common.base.Preconditions;
import net.kyori.adventure.text.Component;
import org.aincraft.kitsune.api.IndexableItem;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Bukkit-specific implementation of IndexableItem.
 * Wraps an ItemStack and provides platform-agnostic access to item properties.
 */
public class BukkitIndexableItem implements IndexableItem<ItemStack> {
    private final ItemStack itemStack;

    public BukkitIndexableItem(ItemStack itemStack) {
        this.itemStack = Preconditions.checkNotNull(itemStack, "itemStack cannot be null");
    }

    @Override
    public int amount() {
        return itemStack.getAmount();
    }

    /**
     * Get a string value from the item's Persistent Data Container.
     *
     * @param key The key in format "namespace:key"
     * @return The value if present
     */
    public Optional<String> getString(String key) {
        Preconditions.checkNotNull(key, "key cannot be null");
        return getFromPdc(key, PersistentDataType.STRING);
    }

    @Override
    public Optional<Component> displayName() {
        var meta = itemStack.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return Optional.empty();
        }
        return Optional.ofNullable(meta.displayName());
    }

    @Override
    public List<Component> lore() {
        var meta = itemStack.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return Collections.emptyList();
        }
        return meta.lore();
    }

    @Override
    public ItemStack unwrap() {
        return itemStack;
    }

    @Override
    public String contentHashCode() {
        // Use Bukkit's serialization for stable, deterministic hashing
        // This captures all item properties including PDC, components, etc.
        byte[] serialized = itemStack.serializeAsBytes();
        int hash = java.util.Arrays.hashCode(serialized);
        return Integer.toHexString(hash);
    }

    /**
     * Get a value from PDC with type safety.
     */
    private <T> Optional<T> getFromPdc(String key, PersistentDataType<?, T> type) {
        return parsePdcKey(key).flatMap(parsedKey -> {
            try {
                var meta = itemStack.getItemMeta();
                if (meta == null) {
                    return Optional.empty();
                }
                var nsKey = new NamespacedKey(parsedKey.namespace(), parsedKey.key());
                var pdc = meta.getPersistentDataContainer();
                if (!pdc.has(nsKey, type)) {
                    return Optional.empty();
                }
                return Optional.of(pdc.get(nsKey, type));
            } catch (Exception e) {
                return Optional.empty();
            }
        });
    }

    /**
     * Parse "namespace:key" format into a NamespacedKey.
     * Returns empty if format is invalid.
     */
    private Optional<ParsedPdcKey> parsePdcKey(String key) {
        if (key == null || !key.contains(":")) {
            return Optional.empty();
        }
        var parts = key.split(":", 2);
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ParsedPdcKey(parts[0], parts[1]));
    }

    /**
     * Simple container for parsed PDC key (namespace:key).
     */
    private record ParsedPdcKey(String namespace, String key) {}

    /**
     * Get enchantments for internal use (hashing).
     */
    private Map<String, Integer> getEnchantments() {
        var enchants = itemStack.getEnchantments();
        if (enchants.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Integer> result = new HashMap<>();
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            result.put(entry.getKey().getKey().asString(), entry.getValue());
        }
        return result;
    }

    /**
     * Content-based equality. Amount excluded - only content properties matter.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BukkitIndexableItem that)) return false;
        return Objects.equals(itemStack.getType(), that.itemStack.getType())
            && Objects.equals(displayName(), that.displayName())
            && Objects.equals(lore(), that.lore())
            && Objects.equals(getEnchantments(), that.getEnchantments());
    }

    /**
     * Content-based hash. Amount excluded for embedding deduplication.
     */
    @Override
    public int hashCode() {
        return Objects.hash(
            itemStack.getType().name(),
            displayName(),
            lore(),
            new TreeMap<>(getEnchantments())
        );
    }
}
