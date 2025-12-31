package org.aincraft.kitsune.platform;

import java.nio.file.Path;

/**
 * Provides the data folder path for platform-specific storage.
 */
@FunctionalInterface
public interface DataFolderProvider {
    Path getDataFolder();
}
