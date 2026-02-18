package org.aincraft.kitsune.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.aincraft.kitsune.config.BukkitConfiguration;
import org.aincraft.kitsune.config.KitsuneConfig;
import org.aincraft.kitsune.config.KitsuneConfigInterface;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

public class ConfigModule extends AbstractModule {
    @Provides @Singleton
    ConfigurationSection provideConfigurationSection(JavaPlugin plugin) {
        return plugin.getConfig();
    }

    @Provides @Singleton
    BukkitConfiguration provideConfiguration(ConfigurationSection section) {
        return new BukkitConfiguration(section);
    }

    @Provides @Singleton
    KitsuneConfigInterface provideKitsuneConfig(ConfigurationSection config) {
        return new KitsuneConfig(new BukkitConfiguration(config));
    }
}