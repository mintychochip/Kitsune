package org.aincraft.chestfind;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.aincraft.chestfind.config.ChestFindConfig;
import org.aincraft.chestfind.embedding.EmbeddingService;
import org.aincraft.chestfind.embedding.EmbeddingServiceFactory;
import org.aincraft.chestfind.logging.ChestFindLogger;
import org.aincraft.chestfind.platform.DataFolderProvider;
import org.aincraft.chestfind.platform.PlatformContext;
import org.aincraft.chestfind.storage.VectorStorage;
import org.aincraft.chestfind.storage.VectorStorageFactory;
import org.aincraft.chestfind.config.ConfigProvider;

import org.aincraft.chestfind.logging.NeoForgeLogger;
import org.aincraft.chestfind.config.NeoForgeConfigProvider;
import org.aincraft.chestfind.platform.NeoForgeDataFolderProvider;
import org.aincraft.chestfind.event.ContainerEventHandler;
import org.aincraft.chestfind.command.ChestFindCommands;
import org.aincraft.chestfind.indexing.ContainerIndexer;
import org.aincraft.chestfind.listener.BlockBreakHandler;
import org.aincraft.chestfind.listener.ContainerCloseHandler;
import org.aincraft.chestfind.listener.ItemTransferHandler;

/**
 * Main NeoForge mod entry point for ChestFind.
 * Orchestrates service initialization and event handler registration.
 * Follows SOLID principles by delegating to specialized components.
 */
@Mod("chestfind")
public class ChestFindMod {
    public static final String MODID = "chestfind";
    private static final Logger LOGGER = LoggerFactory.getLogger(ChestFindMod.class);

    // Shared state for services (initialized once on server start)
    private static EmbeddingService embeddingService;
    private static VectorStorage vectorStorage;
    private static ChestFindLogger logger;
    private static ContainerIndexer containerIndexer;
    private static ChestFindConfig chestFindConfig;
    private static boolean initialized = false;

    public ChestFindMod(IEventBus modBus, ModContainer container) {
        LOGGER.info("ChestFind mod initializing...");

        // Register mod lifecycle events
        modBus.addListener(this::onCommonSetup);

        // Register game events
        NeoForge.EVENT_BUS.register(this);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("ChestFind common setup completed");
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("ChestFind initializing services on server start...");

        // Create platform context with NeoForge adapters
        logger = new NeoForgeLogger(LOGGER);
        ConfigProvider configProvider = new NeoForgeConfigProvider(MODID);
        DataFolderProvider dataFolder = new NeoForgeDataFolderProvider(MODID);

        PlatformContext platform = new PlatformContext(logger, configProvider, dataFolder);

        // Initialize config
        chestFindConfig = new ChestFindConfig(configProvider);

        // Initialize services asynchronously
        embeddingService = EmbeddingServiceFactory.create(chestFindConfig, platform);
        vectorStorage = VectorStorageFactory.create(chestFindConfig, platform);

        // Create ContainerIndexer
        containerIndexer = new ContainerIndexer(logger, embeddingService, vectorStorage, chestFindConfig);

        embeddingService.initialize()
            .thenCompose(v -> vectorStorage.initialize())
            .thenRun(() -> {
                logger.info("ChestFind services initialized successfully");

                // Initialize event handlers with dependencies
                BlockBreakHandler.setVectorStorage(vectorStorage);
                ContainerCloseHandler.setIndexer(containerIndexer);
                ItemTransferHandler.setIndexer(containerIndexer);

                // Register event handlers after initialization completes
                NeoForge.EVENT_BUS.register(new ContainerEventHandler(logger, vectorStorage));

                initialized = true;
                logger.info("ChestFind event handlers registered");
            })
            .exceptionally(ex -> {
                logger.log(ChestFindLogger.LogLevel.SEVERE, "Failed to initialize ChestFind services", ex);
                return null;
            });
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("ChestFind shutting down services...");

        if (vectorStorage != null) {
            vectorStorage.shutdown();
        }
        if (embeddingService != null) {
            embeddingService.shutdown();
        }
        if (containerIndexer != null) {
            containerIndexer.shutdown();
        }
    }

    // Static accessors for event handlers
    public static EmbeddingService getEmbeddingService() {
        return embeddingService;
    }

    public static VectorStorage getVectorStorage() {
        return vectorStorage;
    }

    public static ChestFindLogger getLogger() {
        return logger;
    }

    public static ContainerIndexer getContainerIndexer() {
        return containerIndexer;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Event handler for command registration.
     * Registers all ChestFind commands with the server's Brigadier command dispatcher.
     */
    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.GAME)
    public static class CommandEvents {
        @SubscribeEvent
        public static void onRegisterCommands(RegisterCommandsEvent event) {
            ChestFindCommands.onRegisterCommands(event);
        }
    }
}
