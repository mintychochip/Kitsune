package org.aincraft.chestfind.embedding;

import org.aincraft.chestfind.config.ChestFindConfig;
import org.aincraft.chestfind.platform.PlatformContext;

public class EmbeddingServiceFactory {
    private EmbeddingServiceFactory() {
    }

    public static EmbeddingService create(ChestFindConfig config, PlatformContext platform) {
        String provider = config.getEmbeddingProvider().toLowerCase();

        return switch (provider) {
            case "openai" -> new OpenAIEmbeddingService(platform.logger(), config);
            case "google", "gemini" -> new GoogleEmbeddingService(
                    platform.logger(),
                    config.getGoogleApiKey(),
                    config.getGoogleModel());
            case "onnx" -> new OnnxEmbeddingService(platform.logger(), platform.dataFolder());
            default -> {
                platform.logger().warning("Unknown embedding provider: " + provider + ", using ONNX");
                yield new OnnxEmbeddingService(platform.logger(), platform.dataFolder());
            }
        };
    }
}
