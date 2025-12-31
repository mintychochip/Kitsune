package org.aincraft.kitsune;

import org.aincraft.kitsune.config.ConfigProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Bukkit implementation of the KitsunePlugin platform abstraction.
 * Provides a clean interface for the common module to interact with Bukkit.
 */
public final class BukkitKitsunePlugin implements KitsunePlatform {
    private final JavaPlugin plugin;
    private final ConfigProvider config;

    public BukkitKitsunePlugin(JavaPlugin plugin, ConfigProvider config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public Logger getLogger() {
        return plugin.getLogger();
    }

    @Override
    public Path getDataFolder() {
        return plugin.getDataFolder().toPath();
    }

    @Override
    public ConfigProvider getConfig() {
        return config;
    }
}
