package org.aincraft.kitsune.config;

import java.util.function.Supplier;

/**
 * Platform-agnostic configuration wrapper.
 * Uses Configuration interface for accessing configuration values via hierarchical fluent API.
 */
public final class KitsuneConfig {
    private final Configuration config;

    // Lazily initialized nested configs
    private volatile EmbeddingConfig embeddingConfig;
    private volatile StorageConfig storageConfig;
    private volatile SearchConfig searchConfig;
    private volatile IndexingConfig indexingConfig;
    private volatile ProtectionConfig protectionConfig;
    private volatile CacheConfig cacheConfig;
    private volatile HistoryConfig historyConfig;
    private volatile VisualizerConfig visualizerConfig;

    public KitsuneConfig(ConfigurationFactory configFactory) {
        this.config = configFactory.getConfiguration();
    }

    public EmbeddingConfig embedding() { return lazy(() -> embeddingConfig, v -> embeddingConfig = v, () -> new EmbeddingConfig(config)); }
    public StorageConfig storage() { return lazy(() -> storageConfig, v -> storageConfig = v, () -> new StorageConfig(config)); }
    public SearchConfig search() { return lazy(() -> searchConfig, v -> searchConfig = v, () -> new SearchConfig(config)); }
    public IndexingConfig indexing() { return lazy(() -> indexingConfig, v -> indexingConfig = v, () -> new IndexingConfig(config)); }
    public ProtectionConfig protection() { return lazy(() -> protectionConfig, v -> protectionConfig = v, () -> new ProtectionConfig(config)); }
    public CacheConfig cache() { return lazy(() -> cacheConfig, v -> cacheConfig = v, () -> new CacheConfig(config)); }
    public HistoryConfig history() { return lazy(() -> historyConfig, v -> historyConfig = v, () -> new HistoryConfig(config)); }
    public VisualizerConfig visualizer() { return lazy(() -> visualizerConfig, v -> visualizerConfig = v, () -> new VisualizerConfig(config)); }

    private <T> T lazy(Supplier<T> getter, java.util.function.Consumer<T> setter, Supplier<T> factory) {
        T value = getter.get();
        if (value == null) {
            synchronized (this) {
                value = getter.get();
                if (value == null) {
                    value = factory.get();
                    setter.accept(value);
                }
            }
        }
        return value;
    }

    // ==================== EMBEDDING CONFIG ====================

    public static class EmbeddingConfig {
        private static final String MODEL = "embedding.model";
        private static final String API_KEY = "embedding.api-key";
        private static final String AUTO_DOWNLOAD = "embedding.auto-download";
        private static final String REPOSITORY = "embedding.repository";
        private static final String MODEL_PATH = "embedding.model-path";
        private static final String TOKENIZER_PATH = "embedding.tokenizer-path";
        private static final String DOWNLOAD_RETRIES = "embedding.download-retries";
        private static final String DOWNLOAD_TIMEOUT = "embedding.download-timeout-seconds";

        private static final String DEFAULT_MODEL = "nomic-embed-text-v1.5";

        private final Configuration config;

        EmbeddingConfig(Configuration config) { this.config = config; }

        public String model() { return config.getString(MODEL, DEFAULT_MODEL); }

        public String provider() {
            String model = model().toLowerCase();
            if (model.startsWith("text-embedding-")) return "openai";
            if (model.startsWith("embedding-") || model.contains("gecko")) return "google";
            return "onnx";
        }

        public boolean requiresApiKey() {
            String p = provider();
            return "openai".equals(p) || "google".equals(p);
        }

        public String apiKey() { return config.getString(API_KEY, ""); }
        public boolean autoDownload() { return config.getBoolean(AUTO_DOWNLOAD, true); }
        public String repository() { return config.getString(REPOSITORY, ""); }
        public String modelPath() { return config.getString(MODEL_PATH, ""); }
        public String tokenizerPath() { return config.getString(TOKENIZER_PATH, ""); }
        public int downloadRetries() { return config.getInt(DOWNLOAD_RETRIES, 3); }
        public int downloadTimeoutSeconds() { return config.getInt(DOWNLOAD_TIMEOUT, 300); }
    }

    // ==================== STORAGE CONFIG ====================

    public static class StorageConfig {
        private static final String VECTOR_PROVIDER = "storage.vector-provider";
        private static final String METADATA_PROVIDER = "storage.metadata-provider";
        private static final String METADATA_HOST = "storage.metadata-host";
        private static final String METADATA_PORT = "storage.metadata-port";
        private static final String METADATA_DATABASE = "storage.metadata-database";
        private static final String METADATA_USERNAME = "storage.metadata-username";
        private static final String METADATA_PASSWORD = "storage.metadata-password";
        private static final String MILVUS_HOST = "storage.milvus-host";
        private static final String MILVUS_PORT = "storage.milvus-port";
        private static final String MILVUS_COLLECTION = "storage.milvus-collection";
        private static final String SQLITE_PATH = "storage.sqlite.path";

        private final Configuration config;

        StorageConfig(Configuration config) { this.config = config; }

        public String vectorProvider() { return config.getString(VECTOR_PROVIDER, "jvector"); }
        public String metadataProvider() { return config.getString(METADATA_PROVIDER, "sqlite"); }
        public String metadataHost() { return config.getString(METADATA_HOST, "localhost"); }
        public int metadataPort() { return config.getInt(METADATA_PORT, 5432); }
        public String metadataDatabase() { return config.getString(METADATA_DATABASE, "kitsune"); }
        public String metadataUsername() { return config.getString(METADATA_USERNAME, ""); }
        public String metadataPassword() { return config.getString(METADATA_PASSWORD, ""); }
        public String milvusHost() { return config.getString(MILVUS_HOST, "localhost"); }
        public int milvusPort() { return config.getInt(MILVUS_PORT, 19530); }
        public String milvusCollection() { return config.getString(MILVUS_COLLECTION, "kitsune_vectors"); }
        public String sqlitePath() { return config.getString(SQLITE_PATH, "kitsune.db"); }
    }

    // ==================== SEARCH CONFIG ====================

    public static class SearchConfig {
        private static final String DEFAULT_LIMIT = "search.default-limit";
        private static final String MAX_LIMIT = "search.max-limit";
        private static final String RADIUS = "search.radius";
        private static final String THRESHOLD = "search.threshold";

        private final Configuration config;

        SearchConfig(Configuration config) { this.config = config; }

        public int defaultLimit() { return config.getInt(DEFAULT_LIMIT, 10); }
        public int maxLimit() { return config.getInt(MAX_LIMIT, 50); }
        public int radius() { return config.getInt(RADIUS, 500); }
        public double threshold() { return config.getDouble(THRESHOLD, 0.7); }
    }

    // ==================== INDEXING CONFIG ====================

    public static class IndexingConfig {
        private static final String DEBOUNCE_DELAY = "indexing.debounce-delay-ms";
        private static final String HOPPER_TRANSFERS = "indexing.hopper-transfers";
        private static final String HOPPER_MINECART = "indexing.hopper-minecart-deposits";

        private final Configuration config;

        IndexingConfig(Configuration config) { this.config = config; }

        public int debounceDelayMs() { return config.getInt(DEBOUNCE_DELAY, 2000); }
        public boolean hopperTransfersEnabled() { return config.getBoolean(HOPPER_TRANSFERS, true); }
        public boolean hopperMinecartDepositsEnabled() { return config.getBoolean(HOPPER_MINECART, true); }
    }

    // ==================== PROTECTION CONFIG ====================

    public static class ProtectionConfig {
        private static final String PLUGIN = "protection.plugin";
        private static final String ENABLED = "protection.enabled";

        private final Configuration config;

        ProtectionConfig(Configuration config) { this.config = config; }

        public String plugin() { return config.getString(PLUGIN, "auto"); }
        public boolean enabled() { return config.getBoolean(ENABLED, true); }
    }

    // ==================== CACHE CONFIG ====================

    public static class CacheConfig {
        private static final String PERSISTENT = "cache.persistent";
        private static final String MAX_SIZE = "cache.max-size";

        private final Configuration config;

        CacheConfig(Configuration config) { this.config = config; }

        public boolean persistent() { return config.getBoolean(PERSISTENT, true); }
        public int maxSize() { return config.getInt(MAX_SIZE, 10000); }
    }

    // ==================== HISTORY CONFIG ====================

    public static class HistoryConfig {
        private static final String ENABLED = "history.enabled";
        private static final String MAX_PER_PLAYER = "history.max-entries-per-player";
        private static final String MAX_GLOBAL = "history.max-global-entries";
        private static final String RETENTION_DAYS = "history.retention-days";

        private final Configuration config;

        HistoryConfig(Configuration config) { this.config = config; }

        public boolean enabled() { return config.getBoolean(ENABLED, true); }
        public int maxEntriesPerPlayer() { return config.getInt(MAX_PER_PLAYER, 50); }
        public int maxGlobalEntries() { return config.getInt(MAX_GLOBAL, 500); }
        public int retentionDays() { return config.getInt(RETENTION_DAYS, 30); }
    }

    // ==================== VISUALIZER CONFIG ====================

    public static class VisualizerConfig {
        private static final String ITEM_DISPLAY_COUNT = "visualizer.item-display-count";
        private static final String DISPLAY_HEIGHT = "visualizer.display-height";
        private static final String DISPLAY_RADIUS = "visualizer.display-radius";
        private static final String DISPLAY_DURATION = "visualizer.display-duration-ticks";
        private static final String ITEM_DISPLAY_ENABLED = "visualizer.item-display-enabled";
        private static final String ORBIT_ENABLED = "visualizer.orbit-enabled";
        private static final String ORBIT_SPEED = "visualizer.orbit-speed";
        private static final String SPIN_ENABLED = "visualizer.spin-enabled";
        private static final String SPIN_SPEED = "visualizer.spin-speed";

        private final Configuration config;

        VisualizerConfig(Configuration config) { this.config = config; }

        public int itemDisplayCount() { return config.getInt(ITEM_DISPLAY_COUNT, 6); }
        public double displayHeight() { return config.getDouble(DISPLAY_HEIGHT, 2.0); }
        public double displayRadius() { return config.getDouble(DISPLAY_RADIUS, 1.0); }
        public int displayDurationTicks() { return config.getInt(DISPLAY_DURATION, 200); }
        public boolean itemDisplayEnabled() { return config.getBoolean(ITEM_DISPLAY_ENABLED, true); }
        public boolean orbitEnabled() { return config.getBoolean(ORBIT_ENABLED, true); }
        public double orbitSpeed() { return config.getDouble(ORBIT_SPEED, 2.0); }
        public boolean spinEnabled() { return config.getBoolean(SPIN_ENABLED, true); }
        public double spinSpeed() { return config.getDouble(SPIN_SPEED, 3.0); }
    }
}
