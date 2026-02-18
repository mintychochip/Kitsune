package org.aincraft.kitsune.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.aincraft.kitsune.config.KitsuneConfig;
import org.aincraft.kitsune.config.KitsuneConfigInterface;
import org.aincraft.kitsune.protection.ProtectionProvider;
import org.aincraft.kitsune.protection.ProtectionProviderFactory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.logging.Logger;

public class ProtectionModule extends AbstractModule {
    @Provides @Singleton
    Optional<ProtectionProvider> provideProtectionProvider(
            KitsuneConfig config,
            JavaPlugin plugin,
            Logger logger) {
        return ProtectionProviderFactory.create(config, plugin, logger);
    }
}