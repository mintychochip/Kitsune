package org.aincraft.kitsune.config;

/**
 * Interface for accessing configuration values in a platform-agnostic way.
 * This interface provides access to configuration sections.
 */
public interface KitsuneConfigInterface {

    // Embedding configuration accessors
    String embeddingModel();
    String embeddingProvider();
    String embeddingApiKey();
    boolean embeddingAutoDownload();
    String embeddingRepository();
    String embeddingModelPath();
    String embeddingTokenizerPath();
    int embeddingDownloadRetries();
    int embeddingDownloadTimeoutSeconds();

    // Storage configuration accessors
    String storageVectorProvider();
    String storageMetadataProvider();
    String storageMetadataHost();
    int storageMetadataPort();
    String storageMetadataDatabase();
    String storageMetadataUsername();
    String storageMetadataPassword();
    String storageMilvusHost();
    int storageMilvusPort();
    String storageMilvusCollection();
    String storageSqlitePath();

    // Search configuration accessors
    int searchDefaultLimit();
    int searchMaxLimit();
    int searchRadius();
    double searchThreshold();

    // Indexing configuration accessors
    int indexingDebounceDelayMs();
    boolean indexingHopperTransfersEnabled();
    boolean indexingHopperMinecartDepositsEnabled();

    // Protection configuration accessors
    String protectionPlugin();
    boolean protectionEnabled();

    // Cache configuration accessors
    boolean cachePersistent();
    int cacheMaxSize();

    // History configuration accessors
    boolean historyEnabled();
    int historyMaxEntriesPerPlayer();
    int historyMaxGlobalEntries();
    int historyRetentionDays();

    // Visualizer configuration accessors
    int visualizerItemDisplayCount();
    double visualizerDisplayHeight();
    double visualizerDisplayRadius();
    int visualizerDisplayDurationTicks();
    boolean visualizerItemDisplayEnabled();
    boolean visualizerOrbitEnabled();
    double visualizerOrbitSpeed();
    boolean visualizerSpinEnabled();
    double visualizerSpinSpeed();
}