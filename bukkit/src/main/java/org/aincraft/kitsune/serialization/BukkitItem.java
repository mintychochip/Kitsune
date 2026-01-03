package org.aincraft.kitsune.serialization;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemEnchantments;
import net.kyori.adventure.text.event.HoverEvent;
import org.aincraft.kitsune.api.serialization.ItemContext;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bukkit/Paper implementation of ItemContext. Extracts item data using Paper's DataComponent API
 * (1.21+).
 */
public class BukkitItemContext implements ItemContext<ItemStack> {

  private final ItemStack itemStack;
  private final Material material;

  public BukkitItemContext(ItemStack itemStack) {
    this.itemStack = Objects.requireNonNull(itemStack, "itemStack cannot be null");
    this.material = itemStack.getType();
  }

  @Override
  public Map<String, Integer> enchantments() {
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
  public ItemStack unwrap() {
    return itemStack;
  }

  @Override
  public boolean isUnbreakable() {
    return itemStack.hasData(DataComponentTypes.UNBREAKABLE);
  }

  @Override
  public boolean isSolid() {
    return material.isSolid();
  }

  @Override
  public boolean isOccluding() {
    return material.isOccluding();
  }

  @Override
  public boolean hasGravity() {
    return material.hasGravity();
  }

  @Override
  public String material() {
    return itemStack.getType().name();
  }

  @Override
  public boolean hasShulkerContents() {
    return itemStack.hasData(DataComponentTypes.CONTAINER);
  }

  @Override
  public boolean hasBundle() {
    return itemStack.hasData(DataComponentTypes.BUNDLE_CONTENTS);
  }

  @Override
  public HoverEvent<?> asHoverEvent() {
    return itemStack.asHoverEvent();
  }
}
