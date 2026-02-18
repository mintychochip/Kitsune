package org.aincraft.kitsune.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.aincraft.kitsune.config.KitsuneConfigInterface;
import org.aincraft.kitsune.embedding.EmbeddingService;
import org.aincraft.kitsune.indexing.BukkitContainerIndexer;
import org.aincraft.kitsune.serialization.BukkitItemSerializer;
import org.aincraft.kitsune.storage.KitsuneStorage;

import java.util.Optional;
import java.util.logging.Logger;

public class IndexerModule extends AbstractModule {
    @Provides @Singleton
    BukkitContainerIndexer provideBukkitContainerIndexer(
            Logger logger,
            EmbeddingService embeddingService,
            KitsuneStorage storage,
            KitsuneConfigInterface config,
            BukkitItemSerializer itemSerializer) {
        return new BukkitContainerIndexer(
            logger,
            embeddingService,
            storage,
            config,
            itemSerializer
        );
    }
}