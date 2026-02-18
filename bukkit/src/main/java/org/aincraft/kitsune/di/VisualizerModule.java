package org.aincraft.kitsune.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.aincraft.kitsune.config.KitsuneConfig;
import org.aincraft.kitsune.config.KitsuneConfigInterface;
import org.aincraft.kitsune.visualizer.ContainerItemDisplay;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public class VisualizerModule extends AbstractModule {
    @Provides @Singleton
    ContainerItemDisplay provideContainerItemDisplay(
            Logger logger,
            KitsuneConfig config,
            JavaPlugin plugin) {
        return new ContainerItemDisplay(logger, config, plugin);
    }
}