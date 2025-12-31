package org.aincraft.kitsune;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.aincraft.kitsune.api.ItemTagProviderRegistry;
import org.aincraft.kitsune.command.KitsuneCommands;
import org.aincraft.kitsune.config.FabricConfigProvider;
import org.aincraft.kitsune.config.KitsuneConfig;
import org.aincraft.kitsune.embedding.EmbeddingService;
import org.aincraft.kitsune.embedding.EmbeddingServiceFactory;
import org.aincraft.kitsune.indexing.FabricContainerIndexer;
import org.aincraft.kitsune.indexing.ItemTagProviderRegistryImpl;
import org.aincraft.kitsune.listener.BlockBreakCallback;
import org.aincraft.kitsune.listener.ContainerCloseCallback;
import org.aincraft.kitsune.network.NetworkHandler;
import org.aincraft.kitsune.platform.FabricKitsunePlugin;
import org.aincraft.kitsune.storage.VectorStorage;
import org.aincraft.kitsune.storage.VectorStorageFactory;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Main mod initializer for Kitsune on Fabric.
 * Handles server-side initialization and lifecycle.
 */
public class KitsuneMod implements ModInitializer {
    public static final String MOD_ID = "kitsune";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KitsuneMod instance;

    private FabricConfigProvider configProvider;
    private FabricKitsunePlugin kitsunePlugin;
    private KitsuneConfig kitsuneConfig;
    private EmbeddingService embeddingService;
    private VectorStorage vectorStorage;
    private FabricContainerIndexer containerIndexer;
    private ItemTagProviderRegistry tagRegistry;

    private MinecraftServer server;
    private boolean initialized = false;

    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("Initializing Kitsune...");

        // Load configuration
        Path configDir = FabricLoader.getInstance().getConfigDir();
        configProvider = new FabricConfigProvider(configDir);
        kitsunePlugin = new FabricKitsunePlugin(configDir, configProvider);
        kitsuneConfig = new KitsuneConfig(configProvider);

        // Create tag registry early so other mods can register providers
        tagRegistry = new ItemTagProviderRegistryImpl();

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            KitsuneCommands.register(dispatcher, this);
        });

        // Register networking
        NetworkHandler.registerPayloads();

        // Register server lifecycle events
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        LOGGER.info("Kitsune common initialization complete.");
    }

    private void onServerStarted(MinecraftServer server) {
        this.server = server;
        LOGGER.info("Server started, initializing Kitsune services...");

        initializeServicesAsync().thenRun(() -> {
            registerListeners();
            NetworkHandler.registerServerReceivers(this);
            initialized = true;
            LOGGER.info("Kitsune enabled successfully!");
        }).exceptionally(ex -> {
            LOGGER.error("Failed to initialize Kitsune", ex);
            return null;
        });
    }

    private void onServerStopping(MinecraftServer server) {
        LOGGER.info("Server stopping, shutting down Kitsune...");
        shutdown();
    }

    private CompletableFuture<Void> initializeServicesAsync() {
        return CompletableFuture.supplyAsync(() -> {
            embeddingService = EmbeddingServiceFactory.create(kitsuneConfig, kitsunePlugin);
            vectorStorage = VectorStorageFactory.create(kitsuneConfig, kitsunePlugin);
            containerIndexer = new FabricContainerIndexer(
                    this, embeddingService, vectorStorage, kitsuneConfig, tagRegistry
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
        LOGGER.info("Kitsune disabled.");
    }

    /**
     * Reload configuration from disk.
     */
    public void reloadConfig() {
        configProvider.reload();
        kitsuneConfig = new KitsuneConfig(configProvider);
        LOGGER.info("Kitsune configuration reloaded.");
    }

    // Getters

    public static KitsuneMod getInstance() {
        return instance;
    }

    public boolean isInitialized() {
        return initialized;
    }

    @Nullable
    public MinecraftServer getServer() {
        return server;
    }

    public KitsuneConfig getKitsuneConfig() {
        return kitsuneConfig;
    }

    public FabricKitsunePlugin getKitsunePlugin() {
        return kitsunePlugin;
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
