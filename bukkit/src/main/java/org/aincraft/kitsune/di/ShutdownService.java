package org.aincraft.kitsune.di;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.kitsune.embedding.EmbeddingService;
import org.aincraft.kitsune.indexing.BukkitContainerIndexer;
import org.aincraft.kitsune.storage.KitsuneStorage;
import org.aincraft.kitsune.storage.SearchHistoryStorage;
import org.aincraft.kitsune.storage.PlayerRadiusStorage;
import org.aincraft.kitsune.visualizer.ContainerItemDisplay;
import org.aincraft.kitsune.cache.ItemDataCache;
import org.aincraft.kitsune.api.KitsuneService;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

@Singleton
public class ShutdownService {
    private final Logger logger;
    private final Optional<BukkitContainerIndexer> containerIndexer;
    private final KitsuneStorage storage;
    private final EmbeddingService embeddingService;
    private final SearchHistoryStorage searchHistoryStorage;
    private final PlayerRadiusStorage playerRadiusStorage;
    private final ContainerItemDisplay itemDisplayVisualizer;
    private final ItemDataCache itemDataCache;
    private final @SearchHistoryExecutor ExecutorService searchHistoryExecutor;
    private final @PlayerRadiusExecutor ExecutorService playerRadiusExecutor;

    @Inject
    public ShutdownService(
            Logger logger,
            Optional<BukkitContainerIndexer> containerIndexer,
            KitsuneStorage storage,
            EmbeddingService embeddingService,
            SearchHistoryStorage searchHistoryStorage,
            PlayerRadiusStorage playerRadiusStorage,
            ContainerItemDisplay itemDisplayVisualizer,
            ItemDataCache itemDataCache,
            @SearchHistoryExecutor ExecutorService searchHistoryExecutor,
            @PlayerRadiusExecutor ExecutorService playerRadiusExecutor) {
        this.logger = logger;
        this.containerIndexer = containerIndexer;
        this.storage = storage;
        this.embeddingService = embeddingService;
        this.searchHistoryStorage = searchHistoryStorage;
        this.playerRadiusStorage = playerRadiusStorage;
        this.itemDisplayVisualizer = itemDisplayVisualizer;
        this.itemDataCache = itemDataCache;
        this.searchHistoryExecutor = searchHistoryExecutor;
        this.playerRadiusExecutor = playerRadiusExecutor;
    }

    public void shutdown() {
        logger.info("Shutting down Kitsune services...");

        containerIndexer.ifPresent(BukkitContainerIndexer::shutdown);

        if (storage != null) {
            storage.shutdown();
        }
        if (embeddingService != null) {
            embeddingService.shutdown();
        }
        if (searchHistoryStorage != null) {
            searchHistoryStorage.close();
        }
        if (playerRadiusStorage != null) {
            playerRadiusStorage.close();
        }
        if (searchHistoryExecutor != null) {
            searchHistoryExecutor.shutdownNow();
        }
        if (playerRadiusExecutor != null) {
            playerRadiusExecutor.shutdownNow();
        }
        if (itemDisplayVisualizer != null) {
            itemDisplayVisualizer.cleanupAll();
        }
        if (itemDataCache != null) {
            itemDataCache.clear();
        }

        KitsuneService.unregister();
        logger.info("Kitsune disabled.");
    }
}