package org.aincraft.kitsune.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.aincraft.kitsune.config.KitsuneConfig;
import org.aincraft.kitsune.config.KitsuneConfigInterface;
import org.aincraft.kitsune.storage.SearchHistoryStorage;
import org.aincraft.kitsune.storage.PlayerRadiusStorage;
import org.aincraft.kitsune.storage.KitsuneStorage;

import javax.sql.DataSource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class HistoryModule extends AbstractModule {

    @Provides @Singleton @SearchHistoryExecutor
    ExecutorService provideSearchHistoryExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Provides @Singleton @PlayerRadiusExecutor
    ExecutorService providePlayerRadiusExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Provides @Singleton
    SearchHistoryStorage provideSearchHistoryStorage(
            Logger logger,
            KitsuneStorage storage,
            @SearchHistoryExecutor ExecutorService executor) {
        DataSource dataSource = storage.getDataSource();
        return new SearchHistoryStorage(logger, dataSource, executor);
    }

    @Provides @Singleton
    PlayerRadiusStorage providePlayerRadiusStorage(
            Logger logger,
            KitsuneConfig config,
            KitsuneStorage storage,
            @PlayerRadiusExecutor ExecutorService executor) {
        DataSource dataSource = storage.getDataSource();
        return new PlayerRadiusStorage(logger, dataSource, executor, ((KitsuneConfig) config).search().radius());
    }
}