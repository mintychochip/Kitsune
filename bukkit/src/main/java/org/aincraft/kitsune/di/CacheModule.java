package org.aincraft.kitsune.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.aincraft.kitsune.cache.ItemDataCache;

public class CacheModule extends AbstractModule {
    @Provides @Singleton
    ItemDataCache provideItemDataCache() {
        return new ItemDataCache();
    }
}