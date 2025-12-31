package org.aincraft.chestfind;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.aincraft.chestfind.api.ItemTagProviderRegistry;
import org.aincraft.chestfind.command.ChestFindCommands;
import org.aincraft.chestfind.config.ChestFindConfig;
import org.aincraft.chestfind.config.FabricConfigProvider;
import org.aincraft.chestfind.embedding.EmbeddingService;
import org.aincraft.chestfind.embedding.EmbeddingServiceFactory;
import org.aincraft.chestfind.indexing.FabricContainerIndexer;
import org.aincraft.chestfind.indexing.ItemTagProviderRegistryImpl;
import org.aincraft.chestfind.listener.BlockBreakCallback;
import org.aincraft.chestfind.listener.ContainerCloseCallback;
import org.aincraft.chestfind.network.NetworkHandler;
import org.aincraft.chestfind.platform.FabricElfPlugin;
import org.aincraft.chestfind.storage.VectorStorage;
import org.aincraft.chestfind.storage.VectorStorageFactory;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Main mod initializer for ChestFind on Fabric.
 * Handles server-side initialization and lifecycle.
 */
public class ChestFindMod implements ModInitializer {
    public static final String MOD_ID = "chestfind";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ChestFindMod instance;

    private FabricConfigProvider configProvider;
    private FabricElfPlugin elfPlugin;
    private ChestFindConfig chestFindConfig;
    private EmbeddingService embeddingService;
    private VectorStorage vectorStorage;
    private FabricContainerIndexer containerIndexer;
    private ItemTagProviderRegistry tagRegistry;

    private MinecraftServer server;
    private boolean initialized = false;

    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("Initializing ChestFind...");

        // Load configuration
        Path configDir = FabricLoader.getInstance().getConfigDir();
        configProvider = new FabricConfigProvider(configDir);
        elfPlugin = new FabricElfPlugin(configDir, configProvider);
        chestFindConfig = new ChestFindConfig(configProvider);

        // Create tag registry early so other mods can register providers
        tagRegistry = new ItemTagProviderRegistryImpl();

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ChestFindCommands.register(dispatcher, this);
        });

        // Register networking
        NetworkHandler.registerPayloads();

        // Register server lifecycle events
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        LOGGER.info("ChestFind common initialization complete.");
    }

    private void onServerStarted(MinecraftServer server) {
        this.server = server;
        LOGGER.info("Server started, initializing ChestFind services...");

        initializeServicesAsync().thenRun(() -> {
            registerListeners();
            NetworkHandler.registerServerReceivers(this);
            initialized = true;
            LOGGER.info("ChestFind enabled successfully!");
        }).exceptionally(ex -> {
            LOGGER.error("Failed to initialize ChestFind", ex);
            return null;
        });
    }

    private void onServerStopping(MinecraftServer server) {
        LOGGER.info("Server stopping, shutting down ChestFind...");
        shutdown();
    }

    private CompletableFuture<Void> initializeServicesAsync() {
        return CompletableFuture.supplyAsync(() -> {
            embeddingService = EmbeddingServiceFactory.create(chestFindConfig, elfPlugin);
            vectorStorage = VectorStorageFactory.create(chestFindConfig, elfPlugin);
            containerIndexer = new FabricContainerIndexer(
                    this, embeddingService, vectorStorage, chestFindConfig, tagRegistry
            );
            return null;
        }).thenCompose(v -> embeddingService.initialize())
          .thenCompose(v -> vectorStorage.initialize());
    }

    private void registerListeners() {
        // Register block break callback
        BlockBreakCallback.register(vectorStorage);

        // Register container close callback
        ContainerCloseCallback.register(containerIndexer);
    }

    private void shutdown() {
        if (containerIndexer != null) {
            containerIndexer.shutdown();
        }
        if (vectorStorage != null) {
            vectorStorage.shutdown();
        }
        if (embeddingService != null) {
            embeddingService.shutdown();
        }
        initialized = false;
        LOGGER.info("ChestFind disabled.");
    }

    /**
     * Reload configuration from disk.
     */
    public void reloadConfig() {
        configProvider.reload();
        chestFindConfig = new ChestFindConfig(configProvider);
        LOGGER.info("ChestFind configuration reloaded.");
    }

    // Getters

    public static ChestFindMod getInstance() {
        return instance;
    }

    public boolean isInitialized() {
        return initialized;
    }

    @Nullable
    public MinecraftServer getServer() {
        return server;
    }

    public ChestFindConfig getChestFindConfig() {
        return chestFindConfig;
    }

    public FabricElfPlugin getElfPlugin() {
        return elfPlugin;
    }

    public EmbeddingService getEmbeddingService() {
        return embeddingService;
    }

    public VectorStorage getVectorStorage() {
        return vectorStorage;
    }

    public FabricContainerIndexer getContainerIndexer() {
        return containerIndexer;
    }

    public ItemTagProviderRegistry getTagRegistry() {
        return tagRegistry;
    }
}
