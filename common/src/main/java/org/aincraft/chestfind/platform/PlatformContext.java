package org.aincraft.chestfind.platform;

import org.aincraft.chestfind.config.ConfigProvider;
import org.aincraft.chestfind.logging.ChestFindLogger;

/**
 * Bundles all platform-specific dependencies for injection into common services.
 * This record provides a facade for platform abstractions.
 */
public record PlatformContext(
    ChestFindLogger logger,
    ConfigProvider config,
    DataFolderProvider dataFolder
) {
    public PlatformContext {
        if (logger == null) throw new IllegalArgumentException("Logger cannot be null");
        if (config == null) throw new IllegalArgumentException("Config cannot be null");
        if (dataFolder == null) throw new IllegalArgumentException("DataFolder cannot be null");
    }
}
