package org.aincraft.kitsune.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.logging.Logger;

public class PluginModule extends AbstractModule {
    private final JavaPlugin plugin;

    public PluginModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Provides @Singleton
    JavaPlugin providePlugin() {
        return plugin;
    }

    @Provides @Singleton
    Logger provideLogger(JavaPlugin plugin) {
        return plugin.getLogger();
    }

    @Provides @Singleton
    Path provideDataPath(JavaPlugin plugin) {
        return plugin.getDataFolder().toPath();
    }
}