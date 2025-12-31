package org.aincraft.kitsune;

import java.nio.file.Path;
import java.util.logging.Logger;
import org.aincraft.kitsune.config.ConfigProvider;
import org.aincraft.kitsune.logging.ChestFindLogger;

public interface KitsunePlatform extends ChestFindLogger {
    Logger getLogger();
    Path getDataFolder();
    ConfigProvider getConfig();

    // Default implementations bridging java.util.logging.Logger to ChestFindLogger
    @Override
    default void info(String message) {
        getLogger().info(message);
    }

    @Override
    default void warning(String message) {
        getLogger().warning(message);
    }

    @Override
    default void severe(String message) {
        getLogger().severe(message);
    }

    @Override
    default void log(LogLevel level, String message, Throwable throwable) {
        java.util.logging.Level julLevel = switch (level) {
            case INFO -> java.util.logging.Level.INFO;
            case WARNING -> java.util.logging.Level.WARNING;
            case SEVERE -> java.util.logging.Level.SEVERE;
        };
        getLogger().log(julLevel, message, throwable);
    }
}
