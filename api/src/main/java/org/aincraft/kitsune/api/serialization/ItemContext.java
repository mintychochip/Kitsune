package org.aincraft.kitsune.api.serialization;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Platform-agnostic item context for serialization and tag providers.
 * Provides access to common item properties without platform dependencies.
 *
 * @param <S> The platform-specific item type (e.g., ItemStack for Bukkit)
 */
public interface ItemContext<S> {
    /**
     * Get the material/type name (e.g., "DIAMOND_SWORD").
     */
    String materialName();

    /**
     * Get the stack amount.
     */
    int amount();

    /**
     * Get the display name if customized.
     * @return Display name Component, or empty if using default
     */
    Optional<Component> displayName();

    /**
     * Get lore lines.
     * @return List of lore Components (never null, may be empty)
     */
    List<Component> lore();

    /**
     * Get enchantments as name -> level map.
     * Keys are enchantment key names (e.g., "sharpness", "efficiency").
     * @return Map of enchantment keys to levels (never null)
     */
    Map<String, Integer> enchantments();

    /**
     * Check if this is a block type.
     */
    boolean isBlock();

    /**
     * Check if this is edible.
     */
    boolean isEdible();

    /**
     * Check if this can be used as fuel.
     */
    boolean isFuel();

    /**
     * Check if this is flammable.
     */
    boolean isFlammable();

    /**
     * Compute a stable content hash for embedding caching.
     * @return Deterministic hash of item content
     */
    String contentHash();

    /**
     * Unwrap the platform-specific item for advanced access.
     * TagProviders can use this when they need platform-specific features.
     *
     * @return The underlying platform item
     */
    S unwrap();

    // ==================== Data Component Accessors ====================

    /**
     * Get food properties (nutrition, saturation, effects).
     * @return FoodProperties if item has food data, empty otherwise
     */
    Optional<Object> foodProperties();

    /**
     * Get tool rules (mining speed, blocks that can be mined).
     * @return Tool if item has tool data, empty otherwise
     */
    Optional<Object> tool();

    /**
     * Get dyed item color information.
     * @return DyedItemColor if item is dyed, empty otherwise
     */
    Optional<Object> dyedColor();

    /**
     * Get armor trim information (material and pattern).
     * @return ItemArmorTrim if item has trim data, empty otherwise
     */
    Optional<Object> armorTrim();

    /**
     * Get custom model data value.
     * @return CustomModelData if item has custom model data, empty otherwise
     */
    Optional<Object> customModelData();

    /**
     * Get potion contents (color, effects, type).
     * @return PotionContents if item has potion data, empty otherwise
     */
    Optional<Object> potionContents();

    /**
     * Get bundle contents (items stored in bundle).
     * @return BundleContents if bundle has items, empty otherwise
     */
    Optional<Object> bundleContents();

    /**
     * Get item container contents (shulker box items).
     * @return ItemContainerContents if container has items, empty otherwise
     */
    Optional<Object> containerContents();

    /**
     * Get player head profile (player name, UUID, skin).
     * @return ResolvableProfile if item is player head, empty otherwise
     */
    Optional<Object> resolvableProfile();

    /**
     * Get fireworks explosion properties (colors, effects, flight duration).
     * @return Fireworks if item is fireworks, empty otherwise
     */
    Optional<Object> fireworks();

    /**
     * Get banner pattern layers (patterns and colors applied).
     * @return BannerPatternLayers if item is banner, empty otherwise
     */
    Optional<Object> bannerPatternLayers();

    /**
     * Get equipment slot information (where this can be equipped).
     * @return Equippable if item is equippable, empty otherwise
     */
    Optional<Object> equippable();
}
