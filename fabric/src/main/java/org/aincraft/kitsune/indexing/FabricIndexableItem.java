package org.aincraft.kitsune.indexing;

import com.google.common.base.Preconditions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import org.aincraft.kitsune.api.IndexableItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Fabric implementation of IndexableItem.
 * Wraps a Minecraft ItemStack and provides platform-agnostic access to item properties.
 */
public class FabricIndexableItem implements IndexableItem<ItemStack> {
    private static final String DISPLAY_KEY = "display";
    private static final String NAME_KEY = "Name";
    private static final String LORE_KEY = "Lore";

    private final ItemStack itemStack;

    public FabricIndexableItem(ItemStack itemStack) {
        this.itemStack = Preconditions.checkNotNull(itemStack, "itemStack cannot be null");
    }

    @Override
    public int amount() {
        return itemStack.getCount();
    }

    @Override
    public Optional<Component> displayName() {
        if (!itemStack.hasCustomName()) {
            return Optional.empty();
        }
        Text mcText = itemStack.getName();
        return Optional.of(textToAdventure(mcText));
    }

    @Override
    public List<Component> lore() {
        NbtCompound nbt = itemStack.getNbt();
        if (nbt == null || !nbt.contains(DISPLAY_KEY)) {
            return Collections.emptyList();
        }

        NbtCompound display = nbt.getCompound(DISPLAY_KEY);
        if (!display.contains(LORE_KEY)) {
            return Collections.emptyList();
        }

        NbtList loreList = display.getList(LORE_KEY, 8); // 8 = String tag type
        List<Component> result = new ArrayList<>();
        for (int i = 0; i < loreList.size(); i++) {
            String json = loreList.getString(i);
            try {
                Text mcText = Text.Serializer.fromJson(json);
                if (mcText != null) {
                    result.add(textToAdventure(mcText));
                }
            } catch (Exception e) {
                // Skip malformed lore entries
            }
        }
        return result;
    }

    @Override
    public ItemStack unwrap() {
        return itemStack;
    }

    @Override
    public String contentHashCode() {
        // Use NBT serialization for stable, deterministic hashing
        NbtCompound nbt = new NbtCompound();
        itemStack.writeNbt(nbt);
        // Remove count from NBT for content-based hashing
        nbt.remove("Count");
        int hash = nbt.hashCode();
        return Integer.toHexString(hash);
    }

    /**
     * Get a string value from custom NBT data.
     *
     * @param key The NBT key path (e.g., "MyMod.CustomData")
     * @return The value if present
     */
    public Optional<String> getString(String key) {
        Preconditions.checkNotNull(key, "key cannot be null");
        NbtCompound nbt = itemStack.getNbt();
        if (nbt == null) {
            return Optional.empty();
        }

        // Support dot-notation for nested keys
        String[] parts = key.split("\\.");
        NbtCompound current = nbt;
        for (int i = 0; i < parts.length - 1; i++) {
            if (!current.contains(parts[i])) {
                return Optional.empty();
            }
            current = current.getCompound(parts[i]);
        }

        String finalKey = parts[parts.length - 1];
        if (!current.contains(finalKey)) {
            return Optional.empty();
        }
        return Optional.of(current.getString(finalKey));
    }

    /**
     * Get the item's material/type name.
     */
    public String getTypeName() {
        return Registries.ITEM.getId(itemStack.getItem()).toString();
    }

    /**
     * Get enchantments map.
     */
    public Map<String, Integer> getEnchantments() {
        var enchants = EnchantmentHelper.get(itemStack);
        if (enchants.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Integer> result = new HashMap<>();
        enchants.forEach((enchantment, level) -> {
            var id = Registries.ENCHANTMENT.getId(enchantment);
            if (id != null) {
                result.put(id.toString(), level);
            }
        });
        return result;
    }

    /**
     * Convert Minecraft Text to Adventure Component.
     */
    private Component textToAdventure(Text mcText) {
        // Serialize MC Text to JSON, then parse with Adventure
        String json = Text.Serializer.toJson(mcText);
        return GsonComponentSerializer.gson().deserialize(json);
    }

    /**
     * Content-based equality. Amount excluded - only content properties matter.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FabricIndexableItem that)) return false;
        return Objects.equals(getTypeName(), that.getTypeName())
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
                getTypeName(),
                displayName(),
                lore(),
                new TreeMap<>(getEnchantments())
        );
    }
}
