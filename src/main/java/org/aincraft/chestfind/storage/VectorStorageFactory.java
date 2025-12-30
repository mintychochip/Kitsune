package org.aincraft.chestfind.storage;

import org.aincraft.chestfind.config.ChestFindConfig;
import org.bukkit.plugin.java.JavaPlugin;

public class VectorStorageFactory {
    private VectorStorageFactory() {
    }

    public static VectorStorage create(ChestFindConfig config, JavaPlugin plugin) {
        String provider = config.getStorageProvider().toLowerCase();

        return switch (provider) {
            case "supabase" -> new SupabaseVectorStorage(plugin, config);
            case "sqlite" -> new SqliteVectorStorage(plugin, config.getSqlitePath());
            default -> {
                plugin.getLogger().warning("Unknown storage provider: " + provider + ", using SQLite");
                yield new SqliteVectorStorage(plugin, config.getSqlitePath());
            }
        };
    }
}
