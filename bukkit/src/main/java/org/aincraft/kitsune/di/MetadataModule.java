package org.aincraft.kitsune.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.aincraft.kitsune.storage.ProviderMetadata;
import org.aincraft.kitsune.Platform;

import java.util.logging.Logger;

public class MetadataModule extends AbstractModule {
    @Provides @Singleton
    ProviderMetadata provideProviderMetadata(Logger logger, Platform platform) {
        return new ProviderMetadata(logger, platform.getDataFolder());
    }
}