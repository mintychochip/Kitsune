package org.aincraft.kitsune.embedding;

import org.aincraft.kitsune.KitsunePlatform;
import org.aincraft.kitsune.cache.EmbeddingCache;
import org.aincraft.kitsune.cache.InMemoryEmbeddingCache;
import org.aincraft.kitsune.cache.LayeredEmbeddingCache;
import org.aincraft.kitsune.config.KitsuneConfig;

import java.util.logging.Logger;

public class EmbeddingServiceFactory {
    private EmbeddingServiceFactory() {
    }

    public static EmbeddingService create(KitsuneConfig config, KitsunePlatform plugin) {
        return create(config, plugin.getLogger(), plugin);
    }

    public static EmbeddingService create(KitsuneConfig config, Logger logger, KitsunePlatform dataFolder) {
        String provider = config.embedding().provider();
        String model = config.embedding().model();

        EmbeddingService baseService = switch (provider) {
            case "openai" -> {
                String apiKey = config.embedding().apiKey();
                if (apiKey == null || apiKey.isEmpty()) {
                    logger.warning("OpenAI API key not configured for model: " + model + ", falling back to ONNX");
                    yield new OnnxEmbeddingService(config, logger, dataFolder);
                }
                yield new OpenAIEmbeddingService(logger, config);
            }
            case "google" -> {
                String apiKey = config.embedding().apiKey();
                if (apiKey == null || apiKey.isEmpty()) {
                    logger.warning("Google API key not configured for model: " + model + ", falling back to ONNX");
                    yield new OnnxEmbeddingService(config, logger, dataFolder);
                }
                yield new GoogleEmbeddingService(logger, apiKey, model);
            }
            default -> new OnnxEmbeddingService(config, logger, dataFolder);
        };

        // Create cache based on provider and config
        EmbeddingCache cache;
        boolean usePersistent = config.cache().persistent();
        int maxCacheSize = config.cache().maxSize();

        // Remote providers (openai, google) support persistent cache
        boolean isRemoteProvider = "openai".equals(provider) || "google".equals(provider);

        if (!usePersistent || !isRemoteProvider) {
            // Use in-memory only for local (ONNX) providers or when persistence disabled
            cache = new InMemoryEmbeddingCache(logger, maxCacheSize);
        } else {
            // Use layered cache for remote providers (openai, google)
            String cachePath = dataFolder.getDataFolder().resolve("embedding_cache.db").toString();
            cache = new LayeredEmbeddingCache(logger, cachePath, maxCacheSize);
        }

        return new CachedEmbeddingService(baseService, cache);
    }
}
