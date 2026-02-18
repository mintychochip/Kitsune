package org.aincraft.kitsune.embedding;

import javax.sql.DataSource;
import org.aincraft.kitsune.Platform;
import org.aincraft.kitsune.cache.EmbeddingCache;
import org.aincraft.kitsune.cache.LayeredEmbeddingCache;
import org.aincraft.kitsune.config.KitsuneConfig;
import org.aincraft.kitsune.embedding.download.ModelMap;
import org.aincraft.kitsune.embedding.download.ModelSpec;

/**
 * Factory for creating EmbeddingService instances based on configuration.
 */
public final class EmbeddingServiceFactory {
    private EmbeddingServiceFactory() {}

    public static EmbeddingService create(KitsuneConfig config, Platform platform, DataSource dataSource) {
        String provider = config.embedding().provider();
        String model = config.embedding().model();

        EmbeddingService baseService = switch (provider.toLowerCase()) {
            case "openai" -> {
                String apiKey = config.embedding().apiKey();
                if (apiKey == null || apiKey.isEmpty()) {
                    platform.getLogger().warning("OpenAI API key not configured, falling back to local model");
                    yield createLocalService(model, platform);
                }
                yield new OpenAIEmbeddingService(platform, config);
            }
            case "google" -> {
                String apiKey = config.embedding().apiKey();
                if (apiKey == null || apiKey.isEmpty()) {
                    platform.getLogger().warning("Google API key not configured, falling back to local model");
                    yield createLocalService(model, platform);
                }
                yield new GoogleEmbeddingService(platform, apiKey, model);
            }
            default -> createLocalService(model, platform);
        };

        // Wrap with caching
        EmbeddingCache cache = new LayeredEmbeddingCache(dataSource, platform.getLogger());
        return new CachedEmbeddingService(platform, baseService, cache);
    }

    private static EmbeddingService createLocalService(String model, Platform platform) {
        // Look up model in registry
        ModelSpec spec = ModelMap.getInstance().get(model).orElseGet(() -> {
            platform.getLogger().warning("Unknown model: " + model + ", defaulting to nomic-embed-text-v1.5");
            return ModelMap.getInstance().get("nomic-embed-text-v1.5").orElseThrow();
        });

        platform.getLogger().info("Creating ONNX embedding service for: " + spec.modelName() +
                   " (" + spec.dimension() + "d, strategy: " + spec.taskPrefixStrategy() + ")");
        return new OnnxEmbeddingService(platform, spec);
    }
}
