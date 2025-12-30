package org.aincraft.chestfind.embedding;

import org.aincraft.chestfind.config.ChestFindConfig;
import org.bukkit.plugin.java.JavaPlugin;

public class EmbeddingServiceFactory {
    private EmbeddingServiceFactory() {
    }

    public static EmbeddingService create(ChestFindConfig config, JavaPlugin plugin) {
        String provider = config.getEmbeddingProvider().toLowerCase();

        return switch (provider) {
            case "openai" -> new OpenAIEmbeddingService(plugin, config);
            case "google", "gemini" -> new GoogleEmbeddingService(
                    plugin,
                    config.getGoogleApiKey(),
                    config.getGoogleModel());
            case "onnx" -> new OnnxEmbeddingService(plugin);
            default -> {
                plugin.getLogger().warning("Unknown embedding provider: " + provider + ", using ONNX");
                yield new OnnxEmbeddingService(plugin);
            }
        };
    }
}
