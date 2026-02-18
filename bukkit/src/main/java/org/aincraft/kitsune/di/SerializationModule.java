package org.aincraft.kitsune.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.aincraft.kitsune.api.serialization.TagProviderRegistry;
import org.aincraft.kitsune.serialization.BukkitItemSerializer;
import org.aincraft.kitsune.serialization.BukkitDataComponentTagProvider;
import org.aincraft.kitsune.serialization.providers.TagProviders;

public class SerializationModule extends AbstractModule {
    @Provides @Singleton
    BukkitItemSerializer provideBukkitItemSerializer(TagProviderRegistry registry) {
        // Register platform-specific providers
        registry.register(new BukkitDataComponentTagProvider());
        TagProviders.registerDefaults(registry);
        return new BukkitItemSerializer(registry);
    }
}