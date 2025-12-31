package org.aincraft.kitsune.config;

/**
 * Platform-agnostic configuration provider interface.
 * Returns primitive types and Strings only - no platform-specific types.
 */
public interface ConfigProvider {

    String getString(String path, String defaultValue);

    int getInt(String path, int defaultValue);

    boolean getBoolean(String path, boolean defaultValue);

    double getDouble(String path, double defaultValue);
}
