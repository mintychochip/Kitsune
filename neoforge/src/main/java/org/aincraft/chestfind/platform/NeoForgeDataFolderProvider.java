package org.aincraft.chestfind.platform;

import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.Objects;

/**
 * NeoForge implementation of DataFolderProvider.
 * Uses FMLPaths to access the mod's data directory.
 */
public class NeoForgeDataFolderProvider implements DataFolderProvider {
    private final Path dataFolder;

    public NeoForgeDataFolderProvider(String modId) {
        Objects.requireNonNull(modId, "modId cannot be null");
        // NeoForge stores config in gamedir/config/modid/
        // For data, we use the same pattern
        this.dataFolder = FMLPaths.CONFIGDIR.get().resolve(modId);
    }

    @Override
    public Path getDataFolder() {
        return dataFolder;
    }
}
