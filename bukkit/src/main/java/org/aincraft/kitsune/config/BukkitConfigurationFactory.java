package org.aincraft.kitsune.config;

public final class BukkitConfigurationFactory implements ConfigurationFactory {
    private final org.bukkit.configuration.Configuration config;

    public BukkitConfigurationFactory(org.bukkit.configuration.Configuration config) {
        this.config = config;
    }

    @Override
    public Configuration getConfiguration() {
        return new BukkitConfiguration(config);
    }
}
