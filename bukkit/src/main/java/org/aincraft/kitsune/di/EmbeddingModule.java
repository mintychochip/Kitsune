package org.aincraft.kitsune.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.aincraft.kitsune.config.KitsuneConfigInterface;
import org.aincraft.kitsune.embedding.EmbeddingService;
import org.aincraft.kitsune.embedding.EmbeddingServiceFactory;
import org.aincraft.kitsune.Platform;

import javax.sql.DataSource;
import java.util.logging.Logger;

public class EmbeddingModule extends AbstractModule {

    @Provides @Singleton
    EmbeddingDimensionHolder provideEmbeddingDimensionHolder() {
        return new EmbeddingDimensionHolder();
    }

    @Provides @Singleton
    EmbeddingService provideEmbeddingService(
            KitsuneConfigInterface config,
            Platform platform,
            DataSource dataSource,
            EmbeddingDimensionHolder dimensionHolder,
            Logger logger) {
        EmbeddingService service = EmbeddingServiceFactory.create(config, platform, dataSource);
        // Set dimension in holder for late binding
        dimensionHolder.setDimension(service.getDimension());
        return service;
    }

    @Provides @Singleton @EmbeddingDimension
    int provideDimension(EmbeddingDimensionHolder holder) {
        return holder.getDimension();
    }
}