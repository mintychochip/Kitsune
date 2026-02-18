package org.aincraft.kitsune.config;

import java.util.Collections;
import java.util.Set;
import org.bukkit.configuration.Configuration;

public final class BukkitConfiguration implements org.aincraft.kitsune.config.Configuration {
    private final Configuration config;

    public BukkitConfiguration(Configuration config) {
        this.config = config;
    }

    @Override
    public String getString(String path, String def) {
        return config.getString(path, def);
    }

    @Override
    public int getInt(String path, int def) {
        return config.getInt(path, def);
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        return config.getBoolean(path, def);
    }

    @Override
    public double getDouble(String path, double def) {
        return config.getDouble(path, def);
    }

    @Override
    public Set<String> getKeys(String path) {
        var section = config.getConfigurationSection(path);
        if (section == null) {
            return Collections.emptySet();
        }
        return section.getKeys(false);
    }
}
