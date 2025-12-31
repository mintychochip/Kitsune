package org.aincraft.chestfind.config;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * NeoForge implementation of ConfigProvider.
 * Uses simple Properties-based configuration for initial implementation.
 * Can be upgraded to ModConfigSpec later for GUI integration.
 */
public class NeoForgeConfigProvider implements ConfigProvider {
    private final Properties properties = new Properties();
    private final Path configPath;

    public NeoForgeConfigProvider(String modId) {
        this.configPath = FMLPaths.CONFIGDIR.get().resolve(modId + ".properties");
        loadConfig();
    }

    private void loadConfig() {
        if (Files.exists(configPath)) {
            try (var reader = Files.newBufferedReader(configPath)) {
                properties.load(reader);
            } catch (IOException e) {
                // Use defaults if config can't be loaded
            }
        }
    }

    @Override
    public String getString(String path, String defaultValue) {
        return properties.getProperty(path, defaultValue);
    }

    @Override
    public int getInt(String path, int defaultValue) {
        String value = properties.getProperty(path);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public boolean getBoolean(String path, boolean defaultValue) {
        String value = properties.getProperty(path);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    @Override
    public double getDouble(String path, double defaultValue) {
        String value = properties.getProperty(path);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
