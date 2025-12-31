package org.aincraft.kitsune.platform;

import java.nio.file.Path;

/**
 * Platform-agnostic interface for accessing the plugin's data folder.
 */
public interface DataFolderProvider {

    Path getDataFolder();
}
