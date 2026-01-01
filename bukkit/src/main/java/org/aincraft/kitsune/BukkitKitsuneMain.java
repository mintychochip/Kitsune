package org.aincraft.kitsune;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.aincraft.kitsune.api.ItemTagProviderRegistry;
import org.aincraft.kitsune.cache.EmbeddingCache;
import org.aincraft.kitsune.cache.SqliteEmbeddingCache;
import org.aincraft.kitsune.config.BukkitConfigProvider;
import org.aincraft.kitsune.config.KitsuneConfig;
import org.aincraft.kitsune.embedding.EmbeddingService;
import org.aincraft.kitsune.embedding.EmbeddingServiceFactory;
import org.aincraft.kitsune.indexing.BukkitContainerIndexer;
import org.aincraft.kitsune.indexing.ItemTagProviderRegistryImpl;
import org.aincraft.kitsune.listener.ContainerBreakListener;
import org.aincraft.kitsune.listener.ContainerCloseListener;
import org.aincraft.kitsune.listener.HopperTransferListener;
import java.util.logging.Level;
import org.aincraft.kitsune.protection.ProtectionProvider;
import org.aincraft.kitsune.protection.ProtectionProviderFactory;
import org.aincraft.kitsune.storage.ProviderMetadata;
import org.aincraft.kitsune.storage.VectorStorage;
import org.aincraft.kitsune.storage.VectorStorageFactory;
import org.aincraft.kitsune.util.BukkitContainerLocationResolver;
import org.aincraft.kitsune.util.BukkitLocationFactory;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;

public final class BukkitKitsuneMain extends JavaPlugin {

    private KitsuneConfig kitsuneConfig;
    private KitsunePlatform platformPlugin;
    private EmbeddingService embeddingService;
    private EmbeddingCache embeddingCache;
    private VectorStorage vectorStorage;
    private ProtectionProvider protectionProvider;
    private BukkitContainerIndexer containerIndexer;
    private ItemTagProviderRegistry itemTagProviderRegistry;
    private ProviderMetadata providerMetadata;
    private boolean initialized = false;
    private volatile boolean providerMismatch = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Create platform abstractions
        var configProvider = new BukkitConfigProvider(getConfig());
        this.platformPlugin = new BukkitKitsunePlatform(this, configProvider);
        this.kitsuneConfig = new KitsuneConfig(configProvider);

        // Create the tag provider registry (available immediately for other plugins)
        this.itemTagProviderRegistry = new ItemTagProviderRegistryImpl();

        getLogger().info("Initializing Kitsune...");

        // Register Brigadier commands first (required before server starts)
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final CommandDispatcher<CommandSourceStack> dispatcher = event.registrar().getDispatcher();
            registerCommands(dispatcher);
        });

        // Initialize services async to avoid blocking startup
        initializeServicesAsync().thenRun(() -> {
            registerListeners();
            initialized = true;
            getLogger().info("Kitsune enabled successfully!");
        }).exceptionally(ex -> {
            getLogger().log(Level.SEVERE, "Failed to initialize Kitsune", ex);
            return null;
        });
    }

    private CompletableFuture<Void> initializeServicesAsync() {
        return CompletableFuture.supplyAsync(() -> {
            this.embeddingService = EmbeddingServiceFactory.create(kitsuneConfig, platformPlugin);
            this.vectorStorage = VectorStorageFactory.create(kitsuneConfig, platformPlugin);
            this.protectionProvider = ProtectionProviderFactory.create(kitsuneConfig, this, getLogger()).orElse(null);

            // Create embedding cache
            String cachePath = platformPlugin.getDataFolder().resolve("embedding_cache.db").toString();
            this.embeddingCache = new SqliteEmbeddingCache(getLogger(), cachePath);

            // Create provider metadata tracker
            this.providerMetadata = new ProviderMetadata(getLogger(), platformPlugin.getDataFolder());
            this.providerMetadata.load();

            // Create ContainerIndexer
            this.containerIndexer = new BukkitContainerIndexer(
                getLogger(),
                embeddingService,
                vectorStorage,
                kitsuneConfig
            );
            return null;
        }).thenCompose(v -> embeddingService.initialize())
          .thenCompose(v -> embeddingCache.initialize())
          .thenCompose(v -> vectorStorage.initialize())
          .thenRun(this::checkProviderMismatch);
    }

    private void checkProviderMismatch() {
        String currentProvider = kitsuneConfig.getEmbeddingProvider();
        String currentModel = kitsuneConfig.getEmbeddingModel();

        providerMetadata.checkMismatch(currentProvider, currentModel).ifPresentOrElse(
            mismatch -> {
                providerMismatch = true;
                getLogger().warning("=".repeat(60));
                getLogger().warning("EMBEDDING PROVIDER CHANGED!");
                getLogger().warning(mismatch.message());
                getLogger().warning("Indexing and search are DISABLED until you run:");
                getLogger().warning("  /chestfind purge");
                getLogger().warning("=".repeat(60));
            },
            () -> {
                // Save current provider info (first run or same provider)
                providerMetadata.save(currentProvider, currentModel);
            }
        );
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        var locationResolver = new BukkitContainerLocationResolver();
        pm.registerEvents(new ContainerCloseListener(containerIndexer, locationResolver), this);
        pm.registerEvents(new HopperTransferListener(containerIndexer, locationResolver), this);
        pm.registerEvents(new ContainerBreakListener(vectorStorage, containerIndexer, this), this);
    }

    private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /find <query> [limit]
        dispatcher.register(
            Commands.literal("find")
                .then(
                    Commands.argument("query", StringArgumentType.greedyString())
                        .executes(context -> executeSearch(context.getSource(),
                            StringArgumentType.getString(context, "query"),
                            kitsuneConfig.getDefaultSearchLimit()))
                )
                .executes(context -> {
                    context.getSource().getSender().sendMessage("§cUsage: /find <query>");
                    return 0;
                })
        );

        // /chestfind <subcommand>
        dispatcher.register(
            Commands.literal("chestfind")
                .then(Commands.literal("reload")
                    .requires(source -> source.getSender().hasPermission("kitsune.admin"))
                    .executes(context -> {
                        reloadConfig();
                        var configProvider = new BukkitConfigProvider(getConfig());
                        kitsuneConfig = new KitsuneConfig(configProvider);
                        context.getSource().getSender().sendMessage("§aChestFind config reloaded.");
                        return 1;
                    }))
                .then(Commands.literal("stats")
                    .requires(source -> source.getSender().hasPermission("kitsune.admin"))
                    .executes(context -> {
                        if (!initialized) {
                            context.getSource().getSender().sendMessage("§cChestFind is still initializing...");
                            return 0;
                        }
                        vectorStorage.getStats().thenAccept(stats -> {
                            context.getSource().getSender().sendMessage("§6ChestFind Stats:");
                            context.getSource().getSender().sendMessage("§7Indexed containers: §f" + stats.containerCount());
                            context.getSource().getSender().sendMessage("§7Storage provider: §f" + stats.providerName());
                        });
                        return 1;
                    }))
                .then(Commands.literal("reindex")
                    .requires(source -> source.getSender().hasPermission("kitsune.admin"))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                        .executes(context -> {
                            if (!(context.getSource().getSender() instanceof Player player)) {
                                context.getSource().getSender().sendMessage("§cThis command can only be used by players.");
                                return 0;
                            }
                            int radius = IntegerArgumentType.getInteger(context, "radius");
                            context.getSource().getSender().sendMessage("§7Reindexing containers within " + radius + " blocks...");
                            containerIndexer.reindexRadius(player.getLocation(), radius)
                                .thenAccept(count -> player.sendMessage("§aReindexed " + count + " containers."));
                            return 1;
                        })))
                .then(Commands.literal("fill")
                    .requires(source -> source.getSender().hasPermission("kitsune.admin"))
                    .executes(context -> {
                        if (!(context.getSource().getSender() instanceof Player player)) {
                            context.getSource().getSender().sendMessage("§cThis command can only be used by players.");
                            return 0;
                        }
                        return executeFillChest(player);
                    }))
                .then(Commands.literal("purge")
                    .requires(source -> source.getSender().hasPermission("kitsune.admin"))
                    .executes(context -> {
                        if (!initialized) {
                            context.getSource().getSender().sendMessage("§cChestFind is still initializing...");
                            return 0;
                        }
                        return executePurge(context.getSource());
                    }))
                .executes(context -> {
                    context.getSource().getSender().sendMessage("§6ChestFind Commands:");
                    context.getSource().getSender().sendMessage("§7/find <query> §f- Search containers");
                    context.getSource().getSender().sendMessage("§7/chestfind reload §f- Reload config");
                    context.getSource().getSender().sendMessage("§7/chestfind stats §f- Show statistics");
                    context.getSource().getSender().sendMessage("§7/chestfind reindex <radius> §f- Reindex nearby");
                    context.getSource().getSender().sendMessage("§7/chestfind fill §f- Fill looked-at chest with random items");
                    context.getSource().getSender().sendMessage("§7/chestfind purge §f- Clear all vectors and cache");
                    return 1;
                })
        );
    }

    private int executeSearch(CommandSourceStack source, String query, int limit) {
        if (!source.getSender().hasPermission("kitsune.use")) {
            source.getSender().sendMessage("§cYou don't have permission to use this command.");
            return 0;
        }

        if (!initialized) {
            source.getSender().sendMessage("§cChestFind is still initializing. Please wait...");
            return 0;
        }

        if (providerMismatch) {
            source.getSender().sendMessage("§cEmbedding provider has changed. Run '/chestfind purge' first.");
            return 0;
        }

        Player player = source.getSender() instanceof Player p ? p : null;
        source.getSender().sendMessage("§7Searching for: §f" + query);

        embeddingService.embed(query, "RETRIEVAL_QUERY").thenCompose(embedding ->
            vectorStorage.search(embedding, limit * 3, null) // Get 3x results for reranking
        ).thenAccept(results -> {
            if (results.isEmpty()) {
                source.getSender().sendMessage("§cNo containers found matching your query.");
                return;
            }

            // Take top results
            results = results.subList(0, Math.min(limit, results.size()));

            // Filter results by minimum similarity threshold
            var filteredResults = results.stream()
                .filter(r -> r.score() > 0.675)
                .toList();

            if (filteredResults.isEmpty()) {
                source.getSender().sendMessage("§cNo containers found matching your query.");
                return;
            }

            source.getSender().sendMessage("§6Found " + filteredResults.size() + " containers:");
            for (var result : filteredResults) {
                // Convert LocationData to Bukkit Location
                Location bukkitLoc = BukkitLocationFactory.toBukkitLocation(result.location());
                if (bukkitLoc == null) {
                    continue; // World not loaded
                }

                // Filter by protection if player
                if (player != null && protectionProvider != null && !protectionProvider.canAccess(player.getUniqueId(), result.location())) {
                    continue;
                }

                String coords = String.format("§7[§f%d, %d, %d§7]",
                    result.location().blockX(),
                    result.location().blockY(),
                    result.location().blockZ());
                String world = result.location().worldName();
                String distance = player != null
                    ? String.format("§7(%.1f blocks)", bukkitLoc.distance(player.getLocation()))
                    : "";
                String score = String.format("§a%.1f%%", result.score() * 100);

                source.getSender().sendMessage(coords + " §8" + world + " " + distance + " " + score);
                source.getSender().sendMessage("  §7" + result.preview());

                // Add visual highlight for players
                if (player != null && player.isOnline()) {
                    spawnHighlight(bukkitLoc, player);
                }
            }
        }).exceptionally(ex -> {
            getLogger().log(Level.WARNING, "Search failed", ex);
            source.getSender().sendMessage("§cSearch failed: " + ex.getMessage());
            return null;
        });

        return 1;
    }

    private int executeFillChest(Player player) {
        // Get the block the player is looking at
        var block = player.getTargetBlockExact(5);
        if (block == null) {
            player.sendMessage("§cYou must be looking at a block");
            return 0;
        }

        var blockState = block.getState();
        if (!(blockState instanceof org.bukkit.block.Container container)) {
            player.sendMessage("§cYou must be looking at a container (chest, barrel, etc.)");
            return 0;
        }

        var inventory = container.getInventory();
        var random = new java.util.Random();

        player.sendMessage("§7Filling container at " + block.getLocation() + "...");

        // Common items for random selection
        org.bukkit.Material[] materials = {
            org.bukkit.Material.DIAMOND_PICKAXE,
            org.bukkit.Material.DIAMOND_SWORD,
            org.bukkit.Material.DIAMOND_AXE,
            org.bukkit.Material.IRON_PICKAXE,
            org.bukkit.Material.IRON_SWORD,
            org.bukkit.Material.IRON_INGOT,
            org.bukkit.Material.GOLD_INGOT,
            org.bukkit.Material.DIAMOND,
            org.bukkit.Material.EMERALD,
            org.bukkit.Material.NETHERITE_PICKAXE,
            org.bukkit.Material.NETHERITE_SWORD,
            org.bukkit.Material.ENCHANTED_BOOK,
            org.bukkit.Material.OAK_LOG,
            org.bukkit.Material.BIRCH_LOG,
            org.bukkit.Material.STONE,
            org.bukkit.Material.COBBLESTONE,
            org.bukkit.Material.DIRT,
            org.bukkit.Material.OAK_PLANKS,
            org.bukkit.Material.BIRCH_PLANKS,
            org.bukkit.Material.REDSTONE,
            org.bukkit.Material.COAL,
            org.bukkit.Material.DIAMOND_ORE,
            org.bukkit.Material.IRON_ORE,
            org.bukkit.Material.GOLDEN_APPLE,
            org.bukkit.Material.BREAD,
            org.bukkit.Material.COOKED_BEEF
        };

        // Clear existing items
        inventory.clear();

        // Fill all slots sequentially instead of random
        int itemCount = 0;
        for (int slot = 0; slot < Math.min(20, inventory.getSize()); slot++) {
            org.bukkit.Material material = materials[random.nextInt(materials.length)];
            int amount = 1 + random.nextInt(64);

            org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(material, amount);

            // 20% chance to add enchantments
            if (random.nextDouble() < 0.2 && (material.name().contains("PICKAXE") || material.name().contains("SWORD"))) {
                if (material.name().contains("PICKAXE")) {
                    item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.EFFICIENCY, 1 + random.nextInt(5));
                } else if (material.name().contains("SWORD")) {
                    item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS, 1 + random.nextInt(5));
                }
            }

            // Handle enchanted books specially
            if (material == org.bukkit.Material.ENCHANTED_BOOK) {
                var meta = (org.bukkit.inventory.meta.EnchantmentStorageMeta) item.getItemMeta();
                if (meta != null) {
                    org.bukkit.enchantments.Enchantment[] enchants = {
                        org.bukkit.enchantments.Enchantment.SHARPNESS,
                        org.bukkit.enchantments.Enchantment.EFFICIENCY,
                        org.bukkit.enchantments.Enchantment.PROTECTION,
                        org.bukkit.enchantments.Enchantment.FORTUNE,
                        org.bukkit.enchantments.Enchantment.LOOTING
                    };
                    org.bukkit.enchantments.Enchantment ench = enchants[random.nextInt(enchants.length)];
                    meta.addStoredEnchant(ench, 1 + random.nextInt(5), true);
                    item.setItemMeta(meta);
                }
            }

            inventory.setItem(slot, item);
            itemCount++;
        }

        player.sendMessage("§aFilled container with " + itemCount + " random items!");

        // Auto-index the chest
        containerIndexer.scheduleIndex(block.getLocation(), inventory.getContents());
        player.sendMessage("§7Container will be indexed in " + kitsuneConfig.getDebounceDelayMs() + "ms...");

        return 1;
    }

    private int executePurge(CommandSourceStack source) {
        source.getSender().sendMessage("§7Purging all vectors and cache...");

        // Purge vectors, cache, and metadata in sequence
        vectorStorage.purgeAll()
            .thenCompose(v -> embeddingCache.clear())
            .thenRun(() -> {
                // Delete provider metadata and save new one
                providerMetadata.delete();
                providerMetadata.save(
                    kitsuneConfig.getEmbeddingProvider(),
                    kitsuneConfig.getEmbeddingModel()
                );
                providerMismatch = false;

                source.getSender().sendMessage("§aPurge complete! All vectors and cache cleared.");
                source.getSender().sendMessage("§7Provider metadata reset to: §f" +
                    kitsuneConfig.getEmbeddingProvider() + "/" +
                    kitsuneConfig.getEmbeddingModel());
            })
            .exceptionally(ex -> {
                getLogger().log(Level.SEVERE, "Failed to purge", ex);
                source.getSender().sendMessage("§cPurge failed: " + ex.getMessage());
                return null;
            });

        return 1;
    }

    @Override
    public void onDisable() {
        if (containerIndexer != null) {
            containerIndexer.shutdown();
        }
        if (embeddingCache != null) {
            embeddingCache.shutdown();
        }
        if (vectorStorage != null) {
            vectorStorage.shutdown();
        }
        if (embeddingService != null) {
            embeddingService.shutdown();
        }
        getLogger().info("ChestFind disabled.");
    }

    public KitsuneConfig getKitsuneConfig() {
        return kitsuneConfig;
    }

    public BukkitContainerIndexer getContainerIndexer() {
        return containerIndexer;
    }

    public VectorStorage getVectorStorage() {
        return vectorStorage;
    }

    public EmbeddingService getEmbeddingService() {
        return embeddingService;
    }

    public ProtectionProvider getProtectionProvider() {
        return protectionProvider;
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Get the tag provider registry for external plugins to register custom tag providers.
     * This registry is available immediately after plugin enable, even before async initialization completes.
     *
     * @return The tag provider registry
     */
    public ItemTagProviderRegistry getItemTagProviderRegistry() {
        return itemTagProviderRegistry;
    }

    private void spawnHighlight(Location location, Player player) {
        // Spawn glowing particles at the chest location for 10 seconds
        Location particleLoc = location.clone().add(0.5, 0.5, 0.5); // Center of block

        // Schedule repeating task to show particles
        BukkitTask task = getServer().getScheduler().runTaskTimer(this, () -> {
            if (player == null || !player.isOnline()) {
                return;
            }

            // Spawn multiple particle types for visibility
            player.spawnParticle(
                Particle.END_ROD,
                particleLoc,
                10,  // count
                0.3, // offset X
                0.3, // offset Y
                0.3, // offset Z
                0.02 // speed
            );

            player.spawnParticle(
                Particle.GLOW,
                particleLoc,
                5,
                0.3,
                0.3,
                0.3,
                0.01
            );

            // Add a colored dust particle ring
            Color goldColor = Color.fromRGB(255, 215, 0); // Gold color
            Particle.DustOptions dust = new Particle.DustOptions(goldColor, 1.5f);

            for (int i = 0; i < 8; i++) {
                double angle = (Math.PI * 2 / 8) * i;
                double x = Math.cos(angle) * 0.5;
                double z = Math.sin(angle) * 0.5;
                player.spawnParticle(
                    Particle.DUST,
                    particleLoc.clone().add(x, 0, z),
                    1,
                    dust
                );
            }
        }, 0L, 10L); // Run every 10 ticks (0.5 seconds)

        // Cancel after 10 seconds (200 ticks)
        getServer().getScheduler().runTaskLater(this, task::cancel, 200L);
    }
}
