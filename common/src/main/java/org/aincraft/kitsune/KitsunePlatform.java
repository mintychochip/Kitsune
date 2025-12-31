package org.aincraft.kitsune;

import java.nio.file.Path;
import java.util.logging.Logger;
import org.aincraft.kitsune.config.ConfigProvider;

public interface KitsunePlatform {
    Logger getLogger();
    Path getDataFolder();
    ConfigProvider getConfig();
}
