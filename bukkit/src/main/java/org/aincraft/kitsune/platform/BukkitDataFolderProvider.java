package org.aincraft.kitsune.platform;

import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;

/**
 * Bukkit implementation of DataFolderProvider.
 * Returns the plugin's data folder path.
 */
public class BukkitDataFolderProvider implements DataFolderProvider {
    private final Path dataFolder;

    public BukkitDataFolderProvider(JavaPlugin plugin) {
        this.dataFolder = plugin.getDataFolder().toPath();
    }

    @Override
    public Path getDataFolder() {
        return dataFolder;
    }
}
