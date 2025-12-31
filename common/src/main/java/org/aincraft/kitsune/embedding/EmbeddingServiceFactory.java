package org.aincraft.kitsune.embedding;

import org.aincraft.kitsune.KitsunePlatform;
import org.aincraft.kitsune.cache.EmbeddingCache;
import org.aincraft.kitsune.cache.SqliteEmbeddingCache;
import org.aincraft.kitsune.config.KitsuneConfig;
import org.aincraft.kitsune.logging.ChestFindLogger;

import java.util.logging.Logger;

public class EmbeddingServiceFactory {
    private EmbeddingServiceFactory() {
    }

    public static EmbeddingService create(KitsuneConfig config, KitsunePlatform plugin) {
        return create(config, plugin, plugin);
    }

    public static EmbeddingService create(KitsuneConfig config, ChestFindLogger logger, KitsunePlatform dataFolder) {
        String provider = config.getEmbeddingProvider().toLowerCase();

        EmbeddingService baseService = switch (provider) {
            case "openai" -> new OpenAIEmbeddingService(logger, config);
            case "google", "gemini" -> new GoogleEmbeddingService(
                    logger,
                    config.getGoogleApiKey(),
                    config.getGoogleModel());
            case "onnx" -> new OnnxEmbeddingService(config, logger, dataFolder);
            default -> {
                logger.warning("Unknown embedding provider: " + provider + ", using ONNX");
                yield new OnnxEmbeddingService(config, logger, dataFolder);
            }
        };

        // Create cache
        Logger cacheLogger = Logger.getLogger("Kitsune.EmbeddingCache");
        String cachePath = dataFolder.getDataFolder().resolve("embedding_cache.db").toString();
        EmbeddingCache cache = new SqliteEmbeddingCache(cacheLogger, cachePath);

        return new CachedEmbeddingService(baseService, cache);
    }
}
