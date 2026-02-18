package org.aincraft.kitsune.di;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.kitsune.config.KitsuneConfig;
import org.aincraft.kitsune.config.KitsuneConfigInterface;
import org.aincraft.kitsune.embedding.EmbeddingService;
import org.aincraft.kitsune.storage.KitsuneStorage;
import org.aincraft.kitsune.storage.SearchHistoryStorage;
import org.aincraft.kitsune.storage.PlayerRadiusStorage;
import org.aincraft.kitsune.storage.ProviderMetadata;
import org.aincraft.kitsune.Platform;
import org.aincraft.kitsune.di.EmbeddingDimensionHolder;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

@Singleton
public class ServiceInitializationService {
    private final Logger logger;
    private final EmbeddingService embeddingService;
    private final KitsuneStorage storage;
    private final SearchHistoryStorage searchHistoryStorage;
    private final PlayerRadiusStorage playerRadiusStorage;
    private final ProviderMetadata providerMetadata;
    private final KitsuneConfigInterface config;
    private final EmbeddingDimensionHolder dimensionHolder;

    private volatile boolean initialized = false;
    private volatile boolean providerMismatch = false;

    @Inject
    public ServiceInitializationService(
            Logger logger,
            EmbeddingService embeddingService,
            KitsuneStorage storage,
            SearchHistoryStorage searchHistoryStorage,
            PlayerRadiusStorage playerRadiusStorage,
            ProviderMetadata providerMetadata,
            KitsuneConfig config,
            EmbeddingDimensionHolder dimensionHolder) {
        this.logger = logger;
        this.embeddingService = embeddingService;
        this.storage = storage;
        this.searchHistoryStorage = searchHistoryStorage;
        this.playerRadiusStorage = playerRadiusStorage;
        this.providerMetadata = providerMetadata;
        this.config = (KitsuneConfigInterface) config;
        this.dimensionHolder = dimensionHolder;
    }

    public CompletableFuture<Void> initializeAll() {
        return embeddingService.initialize()
            .thenRun(() -> {
                // Set dimension after embedding service initializes
                dimensionHolder.setDimension(embeddingService.getDimension());
            })
            .thenRun(() -> storage.initialize())
            .thenCompose(v -> searchHistoryStorage.initialize()
                .thenCompose(v2 -> playerRadiusStorage.initialize()))
            .thenRun(() -> {
                providerMetadata.load();
                checkProviderMismatch();
                initialized = true;
                logger.info("All services initialized successfully!");
            });
    }

    private void checkProviderMismatch() {
        String currentProvider = ((KitsuneConfig) config).embedding().provider();
        String currentModel = ((KitsuneConfig) config).embedding().model();

        providerMetadata.checkMismatch(currentProvider, currentModel).ifPresentOrElse(
            mismatch -> {
                providerMismatch = true;
                logger.warning("=".repeat(60));
                logger.warning("EMBEDDING PROVIDER CHANGED!");
                logger.warning(mismatch.message());
                logger.warning("Indexing and search are DISABLED until you run:");
                logger.warning("  /kitsune purge");
                logger.warning("=".repeat(60));
            },
            () -> providerMetadata.save(currentProvider, currentModel)
        );
    }

    public boolean isInitialized() { return initialized; }
    public boolean hasProviderMismatch() { return providerMismatch; }
}