package org.aincraft.kitsune.storage;

import java.nio.file.Path;
import java.util.logging.Logger;
import org.aincraft.kitsune.KitsunePlatform;
import org.aincraft.kitsune.config.KitsuneConfig;

public class VectorStorageFactory {
    private VectorStorageFactory() {
    }

    public static VectorStorage create(KitsuneConfig config, KitsunePlatform plugin) {
        return create(config, plugin.getLogger(), plugin);
    }

    public static VectorStorage create(KitsuneConfig config, Logger logger, KitsunePlatform dataFolder) {
        String provider = config.getStorageProvider().toLowerCase();

        return switch (provider) {
            case "supabase" -> new SupabaseVectorStorage(logger, config);
            case "sqlite" -> {
                String dbPath = dataFolder.getDataFolder().resolve(config.getSqlitePath()).toString();
                yield new SqliteVectorStorage(logger, dbPath);
            }
            case "jvector" -> {
                Path dataDir = dataFolder.getDataFolder();
                int dimension = config.getEmbeddingDimension();
                yield new JVectorStorage(logger, dataDir, dimension);
            }
            default -> {
                logger.warning("Unknown storage provider: " + provider + ", using SQLite");
                String dbPath = dataFolder.getDataFolder().resolve(config.getSqlitePath()).toString();
                yield new SqliteVectorStorage(logger, dbPath);
            }
        };
    }
}
