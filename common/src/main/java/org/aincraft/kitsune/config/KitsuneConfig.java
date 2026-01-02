package org.aincraft.kitsune.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.aincraft.kitsune.embedding.download.ModelRegistry;
import org.aincraft.kitsune.embedding.download.ModelSpec;

/**
 * Platform-agnostic configuration wrapper.
 * Uses Configuration interface for accessing configuration values via hierarchical fluent API.
 */
public class KitsuneConfig {
    private final Configuration config;

    // Lazily initialized nested configuration objects
    private volatile EmbeddingConfig embeddingConfig;
    private volatile StorageConfig storageConfig;
    private volatile SearchConfig searchConfig;
    private volatile IndexingConfig indexingConfig;
    private volatile ProtectionConfig protectionConfig;
    private volatile CacheConfig cacheConfig;
    private volatile ThresholdConfig thresholdConfig;
    private volatile HistoryConfig historyConfig;

    public KitsuneConfig(ConfigurationFactory configFactory) {
        this.config = configFactory.getConfiguration();
    }

    /**
     * Access embedding configuration settings.
     */
    public EmbeddingConfig embedding() {
        if (embeddingConfig == null) {
            synchronized (this) {
                if (embeddingConfig == null) {
                    embeddingConfig = new EmbeddingConfig(config);
                }
            }
        }
        return embeddingConfig;
    }

    /**
     * Access storage configuration settings.
     */
    public StorageConfig storage() {
        if (storageConfig == null) {
            synchronized (this) {
                if (storageConfig == null) {
                    storageConfig = new StorageConfig(config);
                }
            }
        }
        return storageConfig;
    }

    /**
     * Access search configuration settings.
     */
    public SearchConfig search() {
        if (searchConfig == null) {
            synchronized (this) {
                if (searchConfig == null) {
                    searchConfig = new SearchConfig(config);
                }
            }
        }
        return searchConfig;
    }

    /**
     * Access indexing configuration settings.
     */
    public IndexingConfig indexing() {
        if (indexingConfig == null) {
            synchronized (this) {
                if (indexingConfig == null) {
                    indexingConfig = new IndexingConfig(config);
                }
            }
        }
        return indexingConfig;
    }

    /**
     * Access protection configuration settings.
     */
    public ProtectionConfig protection() {
        if (protectionConfig == null) {
            synchronized (this) {
                if (protectionConfig == null) {
                    protectionConfig = new ProtectionConfig(config);
                }
            }
        }
        return protectionConfig;
    }

    /**
     * Access cache configuration settings.
     */
    public CacheConfig cache() {
        if (cacheConfig == null) {
            synchronized (this) {
                if (cacheConfig == null) {
                    cacheConfig = new CacheConfig(config);
                }
            }
        }
        return cacheConfig;
    }

    /**
     * Access threshold configuration settings.
     */
    public ThresholdConfig threshold() {
        if (thresholdConfig == null) {
            synchronized (this) {
                if (thresholdConfig == null) {
                    thresholdConfig = new ThresholdConfig(config);
                }
            }
        }
        return thresholdConfig;
    }

    /**
     * Access history configuration settings.
     */
    public HistoryConfig history() {
        if (historyConfig == null) {
            synchronized (this) {
                if (historyConfig == null) {
                    historyConfig = new HistoryConfig(config);
                }
            }
        }
        return historyConfig;
    }

    /**
     * Embedding configuration accessor.
     * Model-based configuration: infers provider from model name.
     */
    public static class EmbeddingConfig {
        private final Configuration config;
        private volatile OnnxConfig onnxConfig;

        EmbeddingConfig(Configuration config) {
            this.config = config;
        }

        /**
         * Gets model name for the embedding service.
         * Defaults to "nomic-embed-text-v1.5" if not configured.
         */
        public String model() {
            String configuredModel = config.getString("embedding.model", "");
            return !configuredModel.isEmpty() ? configuredModel : "nomic-embed-text-v1.5";
        }

        /**
         * Infers the provider from the model name.
         *
         * - Models starting with "text-embedding-" → "openai"
         * - Models starting with "embedding-" or containing "gecko" → "google"
         * - "nomic-embed-text-v1.5" → "onnx"
         * - "all-MiniLM-L6-v2" or "all-minilm" → "onnx"
         * - "bge-m3" → "onnx"
         * - Default → "onnx"
         */
        public String inferProvider(String model) {
            String lowerModel = model.toLowerCase();

            // OpenAI models
            if (lowerModel.startsWith("text-embedding-")) {
                return "openai";
            }

            // Google models
            if (lowerModel.startsWith("embedding-") || lowerModel.contains("gecko")) {
                return "google";
            }

            // All others default to ONNX (local models)
            return "onnx";
        }

        /**
         * Gets the provider for the configured model.
         */
        public String provider() {
            return inferProvider(model());
        }

        /**
         * Returns whether the configured provider requires an API key.
         */
        public boolean requiresApiKey() {
            String provider = provider();
            return "openai".equals(provider) || "google".equals(provider);
        }

        /**
         * Gets API key for providers that require it (OpenAI/Google).
         * Returns empty string for local providers.
         */
        public String apiKey() {
            return config.getString("embedding.api-key", "");
        }

        public OnnxConfig onnx() {
            if (onnxConfig == null) {
                synchronized (this) {
                    if (onnxConfig == null) {
                        onnxConfig = new OnnxConfig(config);
                    }
                }
            }
            return onnxConfig;
        }
    }

    /**
     * ONNX embedding configuration accessor.
     * Handles local embedding models (nomic, allminilm, bgem3, onnx).
     */
    public static class OnnxConfig {
        private final Configuration config;

        OnnxConfig(Configuration config) {
            this.config = config;
        }

        /**
         * Gets the model name for ONNX embedding.
         * Returns configured model or provider-specific default.
         */
        public String model() {
            String configuredModel = config.getString("embedding.model", "");
            if (!configuredModel.isEmpty()) {
                return configuredModel;
            }

            // Return provider-specific default based on provider setting
            String provider = config.getString("embedding.provider", "onnx");
            return switch (provider) {
                case "nomic" -> "nomic-embed-text-v1.5";
                case "allminilm" -> "all-MiniLM-L6-v2";
                case "bgem3" -> "bge-m3";
                case "onnx" -> "nomic-embed-text-v1.5";
                default -> "nomic-embed-text-v1.5";
            };
        }

        public boolean autoDownload() {
            return config.getBoolean("embedding.auto-download", true);
        }

        public String repository() {
            return config.getString("embedding.repository", "");
        }

        public String modelPath() {
            return config.getString("embedding.model-path", "");
        }

        public String tokenizerPath() {
            return config.getString("embedding.tokenizer-path", "");
        }

        public int downloadRetries() {
            return config.getInt("embedding.download-retries", 3);
        }

        public int downloadTimeoutSeconds() {
            return config.getInt("embedding.download-timeout-seconds", 300);
        }

        public Map<String, ModelSpec> knownModels() {
            Map<String, ModelSpec> models = new HashMap<>();
            Set<String> modelNames = config.getKeys("embedding.known-models");

            for (String modelName : modelNames) {
                String basePath = "embedding.known-models." + modelName;
                String repository = config.getString(basePath + ".repository", "");
                String modelPath = config.getString(basePath + ".model-path", "onnx/model.onnx");
                String tokenizerPath = config.getString(basePath + ".tokenizer-path", "tokenizer.json");

                if (!repository.isEmpty()) {
                    // Dimension is inferred from the model (defaults to 384 for unknown models)
                    int dimension = inferDimension(modelName);
                    models.put(modelName, new ModelSpec(modelName, repository, modelPath, tokenizerPath, dimension));
                }
            }

            return models;
        }

        /**
         * Infers the embedding dimension based on the model name.
         * This keeps the dimension logic in the model implementation rather than config.
         */
        private int inferDimension(String modelName) {
            return switch (modelName) {
                case "nomic-embed-text-v1.5" -> 768;
                case "all-MiniLM-L6-v2" -> 384;
                case "bge-m3" -> 1024;
                default -> 384; // Default dimension for unknown models
            };
        }

        /**
         * Creates a ModelRegistry with default and configured models.
         * Includes built-in defaults plus any models defined in configuration.
         */
        public ModelRegistry createRegistry() {
            Map<String, ModelSpec> models = new HashMap<>();

            // Add default model
            models.put("nomic-embed-text-v1.5", new ModelSpec(
                "nomic-embed-text-v1.5",
                "nomic-ai/nomic-embed-text-v1.5",
                "onnx/model.onnx",
                "tokenizer.json",
                768
            ));

            // Add configured models, overriding defaults if same name
            models.putAll(knownModels());

            return new ModelRegistry(models);
        }
    }

    /**
     * Storage configuration accessor.
     */
    public static class StorageConfig {
        private final Configuration config;
        private volatile SqliteConfig sqliteConfig;

        StorageConfig(Configuration config) {
            this.config = config;
        }

        public String provider() {
            return config.getString("storage.provider", "sqlite");
        }

        public SqliteConfig sqlite() {
            if (sqliteConfig == null) {
                synchronized (this) {
                    if (sqliteConfig == null) {
                        sqliteConfig = new SqliteConfig(config);
                    }
                }
            }
            return sqliteConfig;
        }
    }

    /**
     * SQLite storage configuration accessor.
     */
    public static class SqliteConfig {
        private final Configuration config;

        SqliteConfig(Configuration config) {
            this.config = config;
        }

        public String path() {
            return config.getString("storage.sqlite.path", "kitsune.db");
        }
    }

    /**
     * Search configuration accessor.
     */
    public static class SearchConfig {
        private final Configuration config;

        SearchConfig(Configuration config) {
            this.config = config;
        }

        public int defaultLimit() {
            return config.getInt("search.default-limit", 10);
        }

        public int maxLimit() {
            return config.getInt("search.max-limit", 50);
        }

        public int getSearchRadius() {
            return config.getInt("search.radius", 500);
        }
    }

    /**
     * Indexing configuration accessor.
     */
    public static class IndexingConfig {
        private final Configuration config;

        IndexingConfig(Configuration config) {
            this.config = config;
        }

        public int debounceDelayMs() {
            return config.getInt("indexing.debounce-delay-ms", 2000);
        }

        public boolean hopperTransfersEnabled() {
            return config.getBoolean("indexing.hopper-transfers", true);
        }

        public boolean hopperMinecartDepositsEnabled() {
            return config.getBoolean("indexing.hopper-minecart-deposits", true);
        }
    }

    /**
     * Protection configuration accessor.
     */
    public static class ProtectionConfig {
        private final Configuration config;

        ProtectionConfig(Configuration config) {
            this.config = config;
        }

        public String plugin() {
            return config.getString("protection.plugin", "auto");
        }

        public boolean enabled() {
            return config.getBoolean("protection.enabled", true);
        }
    }

    /**
     * Cache configuration accessor.
     */
    public static class CacheConfig {
        private final Configuration config;

        CacheConfig(Configuration config) {
            this.config = config;
        }

        public boolean persistent() {
            return config.getBoolean("cache.persistent", true);
        }

        public int maxSize() {
            return config.getInt("cache.max-size", 10000);
        }
    }

    /**
     * Threshold configuration accessor.
     */
    public static class ThresholdConfig {
        private final Configuration config;

        ThresholdConfig(Configuration config) {
            this.config = config;
        }

        public double defaultThreshold() {
            return config.getDouble("search.threshold", 0.7);
        }
    }

    /**
     * History configuration accessor.
     */
    public static class HistoryConfig {
        private final Configuration config;

        HistoryConfig(Configuration config) {
            this.config = config;
        }

        public boolean enabled() {
            return config.getBoolean("history.enabled", true);
        }

        public int maxEntriesPerPlayer() {
            return config.getInt("history.max-entries-per-player", 50);
        }

        public int maxGlobalEntries() {
            return config.getInt("history.max-global-entries", 500);
        }

        public int retentionDays() {
            return config.getInt("history.retention-days", 30);
        }
    }
}
