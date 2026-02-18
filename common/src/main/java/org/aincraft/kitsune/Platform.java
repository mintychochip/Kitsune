package org.aincraft.kitsune;

import java.nio.file.Path;
import java.util.logging.Logger;
import org.aincraft.kitsune.api.serialization.TagProviderRegistry;
import org.aincraft.kitsune.config.ConfigurationFactory;

public interface Platform {

  final class PlatformHolder {
    private static Platform instance;
    private PlatformHolder() {}
  }

  static void set(Platform platform) {
    PlatformHolder.instance = platform;
  }

  static Platform get() {
    if (PlatformHolder.instance == null) {
      throw new IllegalStateException("Platform not initialized");
    }
    return PlatformHolder.instance;
  }

  default boolean isInitialized() {
    return false;
  }

  Logger getLogger();

  Path getDataFolder();

  ConfigurationFactory getConfig();

  TagProviderRegistry getTagProviderRegistry();

  World getWorld(String worldName) throws IllegalArgumentException;

  Location createLocation(String worldName, int x, int y, int z) throws IllegalArgumentException;
}
