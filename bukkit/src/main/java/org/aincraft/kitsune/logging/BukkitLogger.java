package org.aincraft.kitsune.logging;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bukkit implementation of ChestFindLogger.
 * Delegates to the plugin's java.util.logging.Logger.
 */
public class BukkitLogger implements ChestFindLogger {
    private final Logger logger;

    public BukkitLogger(JavaPlugin plugin) {
        this.logger = plugin.getLogger();
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warning(String message) {
        logger.warning(message);
    }

    @Override
    public void severe(String message) {
        logger.severe(message);
    }

    @Override
    public void log(LogLevel level, String message, Throwable throwable) {
        logger.log(toJavaLevel(level), message, throwable);
    }

    private Level toJavaLevel(LogLevel level) {
        return switch (level) {
            case INFO -> Level.INFO;
            case WARNING -> Level.WARNING;
            case SEVERE -> Level.SEVERE;
        };
    }
}
