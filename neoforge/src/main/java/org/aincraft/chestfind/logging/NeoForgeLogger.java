package org.aincraft.chestfind.logging;

import net.neoforged.api.distmarker.Dist;
import org.slf4j.Logger;

import java.util.Objects;

/**
 * NeoForge implementation of ChestFindLogger.
 * Wraps SLF4J Logger provided by NeoForge.
 */
public class NeoForgeLogger implements ChestFindLogger {
    private final Logger logger;

    public NeoForgeLogger(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warning(String message) {
        logger.warn(message);
    }

    @Override
    public void severe(String message) {
        logger.error(message);
    }

    @Override
    public void log(LogLevel level, String message, Throwable throwable) {
        switch (level) {
            case INFO -> logger.info(message, throwable);
            case WARNING -> logger.warn(message, throwable);
            case SEVERE -> logger.error(message, throwable);
        }
    }
}
