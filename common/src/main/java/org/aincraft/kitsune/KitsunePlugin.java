package org.aincraft.kitsune;

import org.aincraft.kitsune.config.ConfigProvider;

import java.nio.file.Path;
import java.util.logging.Logger;

public interface KitsunePlugin {
  Logger getLogger();
  Path getDataFolder();
  ConfigProvider getConfig();
}
