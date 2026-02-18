package org.aincraft.kitsune.config;

import java.util.function.Supplier;

public class ConfigurationFactoryAdapter implements org.aincraft.kitsune.config.ConfigurationFactory {
    private final Supplier<org.aincraft.kitsune.config.Configuration> configSupplier;

    public ConfigurationFactoryAdapter(Supplier<org.aincraft.kitsune.config.Configuration> configSupplier) {
        this.configSupplier = configSupplier;
    }

    @Override
    public org.aincraft.kitsune.config.Configuration getConfiguration() {
        return configSupplier.get();
    }
}