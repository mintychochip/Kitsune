package org.aincraft.chestfind.config;

import org.bukkit.configuration.Configuration;

/**
 * Bukkit implementation of ConfigProvider.
 * Wraps Bukkit's Configuration interface.
 */
public class BukkitConfigProvider implements ConfigProvider {
    private final Configuration config;

    public BukkitConfigProvider(Configuration config) {
        this.config = config;
    }

    @Override
    public String getString(String path, String defaultValue) {
        return config.getString(path, defaultValue);
    }

    @Override
    public int getInt(String path, int defaultValue) {
        return config.getInt(path, defaultValue);
    }

    @Override
    public boolean getBoolean(String path, boolean defaultValue) {
        return config.getBoolean(path, defaultValue);
    }

    @Override
    public double getDouble(String path, double defaultValue) {
        return config.getDouble(path, defaultValue);
    }
}
