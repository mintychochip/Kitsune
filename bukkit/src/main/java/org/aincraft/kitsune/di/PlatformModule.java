package org.aincraft.kitsune.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.aincraft.kitsune.Platform;
import org.aincraft.kitsune.BukkitPlatform;
import org.aincraft.kitsune.api.serialization.TagProviderRegistry;
import org.aincraft.kitsune.config.Configuration;
import org.bukkit.plugin.java.JavaPlugin;

public class PlatformModule extends AbstractModule {
    @Provides @Singleton
    Platform providePlatform(JavaPlugin plugin, Configuration config, TagProviderRegistry registry) {
        BukkitPlatform platform = new BukkitPlatform(plugin, () -> config, registry);
        Platform.set(platform);
        return platform;
    }

    @Provides @Singleton
    TagProviderRegistry provideTagProviderRegistry() {
        return TagProviderRegistry.INSTANCE;
    }
}