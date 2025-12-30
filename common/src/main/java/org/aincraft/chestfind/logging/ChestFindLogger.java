package org.aincraft.chestfind.logging;

/**
 * Platform-agnostic logging interface.
 * Implementations provided by platform-specific modules (e.g., Bukkit).
 */
public interface ChestFindLogger {

    void info(String message);

    void warning(String message);

    void severe(String message);

    void log(LogLevel level, String message, Throwable throwable);

    enum LogLevel {
        INFO,
        WARNING,
        SEVERE
    }
}
