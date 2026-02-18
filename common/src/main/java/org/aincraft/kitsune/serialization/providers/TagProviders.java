package org.aincraft.kitsune.serialization.providers;

import org.aincraft.kitsune.api.serialization.TagProviderRegistry;

/**
 * Registers generic/vanilla tag providers that work across all platforms.
 */
public final class TagProviders {
    private TagProviders() {}

    /**
     * Register all generic tag providers to the registry.
     * Called after platform initialization.
     */
    public static void registerDefaults(TagProviderRegistry registry) {
        registry.register(new VanillaEnchantmentTagProvider());
        registry.register(new VanillaSolidBlockTagProvider());
        registry.register(new VanillaBlockOpacityTagProvider());
        registry.register(new VanillaBlockGravityTagProvider());
        registry.register(new VanillaRedstoneTagProvider());
        registry.register(new VanillaBlockColorTagProvider());
        registry.register(new VanillaBlockMaterialTagProvider());
        registry.register(new VanillaMineralTagProvider());
        registry.register(new VanillaWeaponTagProvider());
        registry.register(new VanillaToolTagProvider());
        registry.register(new VanillaArmorTagProvider());
        registry.register(new VanillaStorageItemTagProvider());
        registry.register(new VanillaUnbreakableTagProvider());
    }
}
