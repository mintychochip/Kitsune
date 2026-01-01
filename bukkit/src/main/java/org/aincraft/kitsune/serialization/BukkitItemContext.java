package org.aincraft.kitsune.serialization;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.*;
import net.kyori.adventure.text.Component;
import org.aincraft.kitsune.api.serialization.ItemContext;
import org.bukkit.Material;
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

/**
 * Bukkit/Paper implementation of ItemContext. Extracts item data using Paper's DataComponent API
 * (1.21+).
 */
public class BukkitItemContext implements ItemContext<ItemStack> {

  private final ItemStack itemStack;
  private final Material material;

  // Cached values (lazy initialized)
  private Map<String, Integer> cachedEnchantments;
  private Component cachedDisplayName;
  private boolean displayNameResolved;

  public BukkitItemContext(ItemStack itemStack) {
    this.itemStack = Objects.requireNonNull(itemStack, "itemStack cannot be null");
    this.material = itemStack.getType();
  }

  @Override
  public String materialName() {
    return material.name();
  }

  @Override
  public int amount() {
    return itemStack.getAmount();
  }

  @Override
  public Optional<Component> displayName() {
    return Optional.of(itemStack.displayName());
  }

  @Override
  public List<Component> lore() {
    return itemStack.lore();
  }

  @Override
  public Map<String, Integer> enchantments() {
    if (cachedEnchantments == null) {
      cachedEnchantments = extractEnchantments();
    }
    return cachedEnchantments;
  }

  private Map<String, Integer> extractEnchantments() {
    Map<String, Integer> result = new HashMap<>();

    // Regular enchantments
    if (itemStack.hasData(DataComponentTypes.ENCHANTMENTS)) {
      ItemEnchantments enchants = itemStack.getData(DataComponentTypes.ENCHANTMENTS);
      if (enchants != null) {
        for (Map.Entry<Enchantment, Integer> entry : enchants.enchantments().entrySet()) {
          result.put(entry.getKey().getKey().getKey(), entry.getValue());
        }
      }
    }

    // Stored enchantments (enchanted books)
    if (itemStack.hasData(DataComponentTypes.STORED_ENCHANTMENTS)) {
      ItemEnchantments stored = itemStack.getData(DataComponentTypes.STORED_ENCHANTMENTS);
      if (stored != null) {
        for (Map.Entry<Enchantment, Integer> entry : stored.enchantments().entrySet()) {
          result.put(entry.getKey().getKey().getKey(), entry.getValue());
        }
      }
    }

    return result.isEmpty() ? Collections.emptyMap() : result;
  }

  @Override
  public boolean isBlock() {
    return material.isBlock();
  }

  @Override
  public boolean isEdible() {
    return material.isEdible();
  }

  @Override
  public boolean isFuel() {
    return material.isFuel();
  }

  @Override
  public boolean isFlammable() {
    return material.isFlammable();
  }

  @Override
  public String contentHash() {
    byte[] serialized = itemStack.serializeAsBytes();
    int hash = java.util.Arrays.hashCode(serialized);
    return Integer.toHexString(hash);
  }

  @Override
  public ItemStack unwrap() {
    return itemStack;
  }

  /**
   * Check if this is an unbreakable item.
   */
  public boolean isUnbreakable() {
    return itemStack.hasData(DataComponentTypes.UNBREAKABLE);
  }

  /**
   * Check if this item has custom model data.
   */
  public boolean hasCustomModelData() {
    return itemStack.hasData(DataComponentTypes.CUSTOM_MODEL_DATA);
  }

  /**
   * Check if this is a dyed item.
   */
  public boolean isDyed() {
    return itemStack.hasData(DataComponentTypes.DYED_COLOR);
  }

  /**
   * Check if this has armor trim.
   */
  public boolean hasTrim() {
    return itemStack.hasData(DataComponentTypes.TRIM);
  }

  // ==================== Data Component Accessors ====================

  @Override
  public Optional<Object> foodProperties() {
    return Optional.ofNullable((Object) itemStack.getData(DataComponentTypes.FOOD));
  }

  @Override
  public Optional<Object> tool() {
    return Optional.ofNullable((Object) itemStack.getData(DataComponentTypes.TOOL));
  }

  @Override
  public Optional<Object> dyedColor() {
    return Optional.ofNullable((Object) itemStack.getData(DataComponentTypes.DYED_COLOR));
  }

  @Override
  public Optional<Object> armorTrim() {
    return Optional.ofNullable((Object) itemStack.getData(DataComponentTypes.TRIM));
  }

  @Override
  public Optional<Object> customModelData() {
    return Optional.ofNullable((Object) itemStack.getData(DataComponentTypes.CUSTOM_MODEL_DATA));
  }

  @Override
  public Optional<Object> potionContents() {
    return Optional.ofNullable((Object) itemStack.getData(DataComponentTypes.POTION_CONTENTS));
  }

  @Override
  public Optional<Object> bundleContents() {
    return Optional.ofNullable((Object) itemStack.getData(DataComponentTypes.BUNDLE_CONTENTS));
  }

  @Override
  public Optional<Object> containerContents() {
    return Optional.ofNullable((Object) itemStack.getData(DataComponentTypes.CONTAINER));
  }

  @Override
  public Optional<Object> resolvableProfile() {
    return Optional.ofNullable((Object) itemStack.getData(DataComponentTypes.PROFILE));
  }

  @Override
  public Optional<Object> fireworks() {
    return Optional.ofNullable((Object) itemStack.getData(DataComponentTypes.FIREWORKS));
  }

  @Override
  public Optional<Object> bannerPatternLayers() {
    return Optional.ofNullable((Object) itemStack.getData(DataComponentTypes.BANNER_PATTERNS));
  }

  @Override
  public Optional<Object> equippable() {
    return Optional.ofNullable((Object) itemStack.getData(DataComponentTypes.EQUIPPABLE));
  }
}
