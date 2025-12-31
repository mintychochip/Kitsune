package org.aincraft.kitsune.config;

/**
 * Platform-agnostic configuration wrapper.
 * Uses ConfigProvider interface for accessing configuration values.
 */
public class KitsuneConfig {
    private final ConfigProvider config;

    public KitsuneConfig(ConfigProvider config) {
        this.config = config;
    }

    public String getEmbeddingProvider() {
        return config.getString("embedding.provider", "onnx");
    }

    public String getEmbeddingModel() {
        String provider = getEmbeddingProvider();
        return switch (provider) {
            case "openai" -> getOpenAIModel();
            case "google" -> getGoogleModel();
            default -> "all-MiniLM-L6-v2"; // Default ONNX model
        };
    }

    public String getStorageProvider() {
        return config.getString("storage.provider", "sqlite");
    }

    public int getDebounceDelayMs() {
        return config.getInt("indexing.debounce-delay-ms", 2000);
    }

    public int getDefaultSearchLimit() {
        return config.getInt("search.default-limit", 10);
    }

    public int getMaxSearchLimit() {
        return config.getInt("search.max-limit", 50);
    }

    public boolean isHopperIndexingEnabled() {
        return config.getBoolean("indexing.hopper-transfers", true);
    }

    public boolean isHopperMinecartIndexingEnabled() {
        return config.getBoolean("indexing.hopper-minecart-deposits", true);
    }

    // Embedding settings
    public String getOpenAIApiKey() {
        return config.getString("embedding.openai.api-key", "");
    }

    public String getOpenAIModel() {
        return config.getString("embedding.openai.model", "text-embedding-3-small");
    }

    public String getGoogleApiKey() {
        return config.getString("embedding.google.api-key", "");
    }

    public String getGoogleModel() {
        return config.getString("embedding.google.model", "text-embedding-004");
    }

    public int getEmbeddingDimension() {
        return config.getInt("embedding.dimension", 384);
    }

    public String getOnnxModel() {
        return config.getString("embedding.onnx.model", "nomic-embed-text-v1.5");
    }

    // Storage settings
    public String getSqlitePath() {
        return config.getString("storage.sqlite.path", "kitsune.db");
    }

    public String getSupabaseUrl() {
        return config.getString("storage.supabase.url", "");
    }

    public String getSupabaseKey() {
        return config.getString("storage.supabase.key", "");
    }

    public String getSupabaseTable() {
        return config.getString("storage.supabase.table", "containers");
    }

    // Protection settings
    public String getProtectionPlugin() {
        return config.getString("protection.plugin", "auto");
    }

    public boolean isProtectionEnabled() {
        return config.getBoolean("protection.enabled", true);
    }
}
