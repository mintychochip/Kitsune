package org.aincraft.kitsune.serialization;

import org.aincraft.kitsune.Item;
import org.aincraft.kitsune.api.serialization.TagProvider;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

/**
 * Provides tags based on Bukkit/Paper DataComponents.
 * Adds platform-specific tags for item properties.
 */
public class BukkitDataComponentTagProvider implements TagProvider {

    @Override
    public void appendTags(Collection<String> tags, Item item) {
        ItemStack stack = item.unwrap(ItemStack.class);

        // Durability-related
        if (stack.hasData(DataComponentTypes.UNBREAKABLE)) {
            tags.add("unbreakable");
            tags.add("indestructible");
        }

        if (stack.hasData(DataComponentTypes.MAX_DAMAGE)) {
            int maxDamage = stack.getData(DataComponentTypes.MAX_DAMAGE);
            int damage = stack.getDataOrDefault(DataComponentTypes.DAMAGE, 0);
            if (damage > 0) {
                tags.add("damaged");
                double percent = (double) (maxDamage - damage) / maxDamage;
                if (percent < 0.25) {
                    tags.add("nearlybroke");
                } else if (percent < 0.5) {
                    tags.add("worn");
                }
            } else {
                tags.add("pristine");
            }
        }

        // Appearance
        if (stack.hasData(DataComponentTypes.DYED_COLOR)) {
            tags.add("dyed");
            tags.add("colored");
        }

        if (stack.hasData(DataComponentTypes.TRIM)) {
            tags.add("trimmed");
            tags.add("decorated");
        }

        if (stack.hasData(DataComponentTypes.CUSTOM_MODEL_DATA)) {
            tags.add("custommodeldata");
            tags.add("custom");
        }

        // Rarity
        if (stack.hasData(DataComponentTypes.RARITY)) {
            var rarity = stack.getData(DataComponentTypes.RARITY);
            if (rarity != null) {
                tags.add(rarity.name().toLowerCase());
            }
        }

        // Container types
        if (stack.hasData(DataComponentTypes.BUNDLE_CONTENTS)) {
            tags.add("bundle");
            tags.add("container");
        }

        if (stack.hasData(DataComponentTypes.CONTAINER)) {
            tags.add("container");
            if (stack.getType().name().contains("SHULKER_BOX")) {
                tags.add("shulker");
            }
        }

        // Charged/loaded weapons
        if (stack.hasData(DataComponentTypes.CHARGED_PROJECTILES)) {
            tags.add("loaded");
            tags.add("charged");
        }

        // Books
        if (stack.hasData(DataComponentTypes.WRITABLE_BOOK_CONTENT)) {
            tags.add("book");
            tags.add("writable");
        }

        if (stack.hasData(DataComponentTypes.WRITTEN_BOOK_CONTENT)) {
            tags.add("book");
            tags.add("signed");
            tags.add("written");
        }

        // Potions
        if (stack.hasData(DataComponentTypes.POTION_CONTENTS)) {
            tags.add("potion");
            tags.add("brewable");
        }

        // Food properties
        if (stack.hasData(DataComponentTypes.FOOD)) {
            var food = stack.getData(DataComponentTypes.FOOD);
            if (food != null) {
                if (food.canAlwaysEat()) {
                    tags.add("alwaysedible");
                }
                int nutrition = food.nutrition();
                if (nutrition >= 8) {
                    tags.add("veryfilling");
                } else if (nutrition >= 4) {
                    tags.add("filling");
                } else {
                    tags.add("snack");
                }
            }
        }

        // Tool properties
        if (stack.hasData(DataComponentTypes.TOOL)) {
            var tool = stack.getData(DataComponentTypes.TOOL);
            if (tool != null) {
                float speed = tool.defaultMiningSpeed();
                if (speed >= 12.0) {
                    tags.add("veryfast");
                } else if (speed >= 8.0) {
                    tags.add("fast");
                }
            }
        }

        // Music
        if (stack.getType().isRecord()) {
            tags.add("musicdisc");
            tags.add("record");
        }

        // Gravity
        if (stack.getType().hasGravity()) {
            tags.add("falling");
            tags.add("gravity");
        }

        // Block/item distinction
        if (stack.getType().isBlock()) {
            tags.add("block");
        } else {
            tags.add("item");
        }
    }
}
