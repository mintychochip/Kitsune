package org.aincraft.kitsune.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.aincraft.kitsune.config.KitsuneConfig;
import org.aincraft.kitsune.storage.KitsuneStorage;
import org.aincraft.kitsune.storage.metadata.ContainerStorage;
import org.aincraft.kitsune.storage.vector.JVectorIndex;
import org.aincraft.kitsune.di.EmbeddingDimension;
import org.aincraft.kitsune.di.EmbeddingDimensionHolder;
import org.aincraft.kitsune.Platform;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.logging.Logger;

public class StorageModule extends AbstractModule {
    @Provides @Singleton
    DataSource provideDataSource(Platform platform, Logger logger) {
        Path dbFile = platform.getDataFolder().resolve("kitsune.db");
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.toAbsolutePath());
        hikariConfig.setDriverClassName("org.sqlite.JDBC");
        hikariConfig.setMaximumPoolSize(1);
        hikariConfig.setLeakDetectionThreshold(30000);
        return new HikariDataSource(hikariConfig);
    }

    @Provides @Singleton
    ContainerStorage provideContainerStorage(DataSource dataSource, Logger logger) {
        return new ContainerStorage(dataSource, logger);
    }

    @Provides @Singleton
    JVectorIndex provideJVectorIndex(Logger logger, Platform platform, @EmbeddingDimension int dimension) {
        return new JVectorIndex(logger, platform.getDataFolder(), dimension);
    }

    @Provides @Singleton
    KitsuneStorage provideKitsuneStorage(Logger logger, ContainerStorage containerStorage, JVectorIndex vectorIndex) {
        return new KitsuneStorage(logger, containerStorage, vectorIndex);
    }
}