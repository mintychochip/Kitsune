package org.aincraft.chestfind.indexing;

import com.google.common.base.Preconditions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * NeoForge-specific wrapper for ItemStack.
 * Uses NeoForge 1.20.6+ DataComponents API for item property access.
 */
public class NeoForgeIndexableItem {
    private final ItemStack itemStack;

    public NeoForgeIndexableItem(ItemStack itemStack) {
        this.itemStack = Preconditions.checkNotNull(itemStack, "itemStack cannot be null");
    }

    public int amount() {
        return itemStack.getCount();
    }

    public Optional<String> displayName() {
        if (!itemStack.has(DataComponents.CUSTOM_NAME)) {
            return Optional.empty();
        }
        Component customName = itemStack.get(DataComponents.CUSTOM_NAME);
        return customName != null ? Optional.of(customName.getString()) : Optional.empty();
    }

    public List<String> lore() {
        if (!itemStack.has(DataComponents.LORE)) {
            return Collections.emptyList();
        }

        ItemLore itemLore = itemStack.get(DataComponents.LORE);
        if (itemLore == null) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        for (Component line : itemLore.lines()) {
            result.add(line.getString());
        }
        return result;
    }

    public ItemStack unwrap() {
        return itemStack;
    }

    /**
     * Generates a content-based hash code that excludes stack amount.
     * Used for caching and deduplication of item embeddings.
     */
    public String contentHashCode() {
        // Use item properties for hashing
        int hash = Objects.hash(
            getItemId(),
            displayName().orElse(""),
            String.join("|", lore()),
            getEnchantmentData(),
            getDamage()
        );
        return Integer.toHexString(hash);
    }

    /**
     * Gets the item registry ID.
     */
    public String getItemId() {
        var key = itemStack.getItem().builtInRegistryHolder().key();
        return key != null ? key.location().toString() : itemStack.getItem().toString();
    }

    /**
     * Gets the damage value (durability used).
     */
    public int getDamage() {
        if (itemStack.has(DataComponents.DAMAGE)) {
            Integer damage = itemStack.get(DataComponents.DAMAGE);
            return damage != null ? damage : 0;
        }
        return 0;
    }

    /**
     * Gets max durability of the item.
     */
    public int getMaxDamage() {
        if (itemStack.has(DataComponents.MAX_DAMAGE)) {
            Integer maxDamage = itemStack.get(DataComponents.MAX_DAMAGE);
            return maxDamage != null ? maxDamage : 0;
        }
        return 0;
    }

    /**
     * Gets enchantment data as a string for comparison.
     */
    public String getEnchantmentData() {
        if (!itemStack.has(DataComponents.ENCHANTMENTS)) {
            return "";
        }

        ItemEnchantments enchantments = itemStack.get(DataComponents.ENCHANTMENTS);
        if (enchantments == null || enchantments.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        enchantments.entrySet().forEach(entry -> {
            var enchant = entry.getKey();
            int level = entry.getIntValue();
            var key = enchant.unwrapKey();
            if (key.isPresent()) {
                sb.append(key.get().location()).append(":").append(level).append(";");
            }
        });
        return sb.toString();
    }

    /**
     * Gets stored enchantments (for enchanted books).
     */
    public String getStoredEnchantmentData() {
        if (!itemStack.has(DataComponents.STORED_ENCHANTMENTS)) {
            return "";
        }

        ItemEnchantments enchantments = itemStack.get(DataComponents.STORED_ENCHANTMENTS);
        if (enchantments == null || enchantments.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        enchantments.entrySet().forEach(entry -> {
            var enchant = entry.getKey();
            int level = entry.getIntValue();
            var key = enchant.unwrapKey();
            if (key.isPresent()) {
                sb.append(key.get().location()).append(":").append(level).append(";");
            }
        });
        return sb.toString();
    }

    /**
     * Checks if item is unbreakable.
     */
    public boolean isUnbreakable() {
        return itemStack.has(DataComponents.UNBREAKABLE);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NeoForgeIndexableItem that)) return false;

        // Content-based equality: amount excluded
        return Objects.equals(getItemId(), that.getItemId())
            && Objects.equals(displayName(), that.displayName())
            && Objects.equals(lore(), that.lore())
            && Objects.equals(getEnchantmentData(), that.getEnchantmentData())
            && getDamage() == that.getDamage();
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            getItemId(),
            displayName(),
            lore(),
            getEnchantmentData(),
            getDamage()
        );
    }
}
