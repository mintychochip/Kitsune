package org.aincraft.chestfind;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.aincraft.chestfind.config.ChestFindConfig;
import org.aincraft.chestfind.embedding.EmbeddingService;
import org.aincraft.chestfind.embedding.EmbeddingServiceFactory;
import org.aincraft.chestfind.indexing.ContainerIndexer;
import org.aincraft.chestfind.listener.ContainerBreakListener;
import org.aincraft.chestfind.listener.ContainerCloseListener;
import org.aincraft.chestfind.listener.HopperTransferListener;
import org.aincraft.chestfind.protection.ProtectionProvider;
import org.aincraft.chestfind.protection.ProtectionProviderFactory;
import org.aincraft.chestfind.storage.VectorStorage;
import org.aincraft.chestfind.storage.VectorStorageFactory;
import org.aincraft.chestfind.util.BukkitContainerLocationResolver;
import org.aincraft.chestfind.util.ContainerLocationResolver;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class ChestFindPlugin extends JavaPlugin {

    private ChestFindConfig chestFindConfig;
    private EmbeddingService embeddingService;
    private VectorStorage vectorStorage;
    private ProtectionProvider protectionProvider;
    private ContainerIndexer containerIndexer;
    private ContainerLocationResolver containerLocationResolver;
    private boolean initialized = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.chestFindConfig = new ChestFindConfig(getConfig());

        getLogger().info("Initializing ChestFind...");

        // Register Brigadier commands first (required before server starts)
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final CommandDispatcher<CommandSourceStack> dispatcher = event.registrar().getDispatcher();
            registerCommands(dispatcher);
        });

        // Initialize services async to avoid blocking startup
        initializeServicesAsync().thenRun(() -> {
            registerListeners();
            initialized = true;
            getLogger().info("ChestFind enabled successfully!");
        }).exceptionally(ex -> {
            getLogger().log(Level.SEVERE, "Failed to initialize ChestFind", ex);
            return null;
        });
    }

    private CompletableFuture<Void> initializeServicesAsync() {
        return CompletableFuture.supplyAsync(() -> {
            this.embeddingService = EmbeddingServiceFactory.create(chestFindConfig, this);
            this.vectorStorage = VectorStorageFactory.create(chestFindConfig, this);
            this.protectionProvider = ProtectionProviderFactory.create(chestFindConfig, this);
            this.containerLocationResolver = new BukkitContainerLocationResolver();
            this.containerIndexer = new ContainerIndexer(this, embeddingService, vectorStorage, chestFindConfig);
            return null;
        }).thenCompose(v -> embeddingService.initialize())
          .thenCompose(v -> vectorStorage.initialize());
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new ContainerCloseListener(containerIndexer, containerLocationResolver), this);
        pm.registerEvents(new HopperTransferListener(containerIndexer, containerLocationResolver), this);
        pm.registerEvents(new ContainerBreakListener(vectorStorage), this);
    }

    private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /find <query> [limit]
        dispatcher.register(
            Commands.literal("find")
                .then(
                    Commands.argument("query", StringArgumentType.greedyString())
                        .executes(context -> executeSearch(context.getSource(),
                            StringArgumentType.getString(context, "query"),
                            chestFindConfig.getDefaultSearchLimit()))
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
                    .requires(source -> source.getSender().hasPermission("chestfind.admin"))
                    .executes(context -> {
                        reloadConfig();
                        chestFindConfig = new ChestFindConfig(getConfig());
                        context.getSource().getSender().sendMessage("§aChestFind config reloaded.");
                        return 1;
                    }))
                .then(Commands.literal("stats")
                    .requires(source -> source.getSender().hasPermission("chestfind.admin"))
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
                    .requires(source -> source.getSender().hasPermission("chestfind.admin"))
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
                    .requires(source -> source.getSender().hasPermission("chestfind.admin"))
                    .executes(context -> {
                        if (!(context.getSource().getSender() instanceof Player player)) {
                            context.getSource().getSender().sendMessage("§cThis command can only be used by players.");
                            return 0;
                        }
                        return executeFillChest(player);
                    }))
                .executes(context -> {
                    context.getSource().getSender().sendMessage("§6ChestFind Commands:");
                    context.getSource().getSender().sendMessage("§7/find <query> §f- Search containers");
                    context.getSource().getSender().sendMessage("§7/chestfind reload §f- Reload config");
                    context.getSource().getSender().sendMessage("§7/chestfind stats §f- Show statistics");
                    context.getSource().getSender().sendMessage("§7/chestfind reindex <radius> §f- Reindex nearby");
                    context.getSource().getSender().sendMessage("§7/chestfind fill §f- Fill looked-at chest with random items");
                    return 1;
                })
        );
    }

    private int executeSearch(CommandSourceStack source, String query, int limit) {
        if (!source.getSender().hasPermission("chestfind.use")) {
            source.getSender().sendMessage("§cYou don't have permission to use this command.");
            return 0;
        }

        if (!initialized) {
            source.getSender().sendMessage("§cChestFind is still initializing. Please wait...");
            return 0;
        }

        Player player = source.getSender() instanceof Player p ? p : null;
        source.getSender().sendMessage("§7Searching for: §f" + query);

        // Log search query
        getLogger().info("Search query: " + query);

        // Expand query with related terms
        String expandedQuery = org.aincraft.chestfind.search.QueryExpander.expand(query);
        getLogger().info("Expanded query: '" + query + "' -> '" + expandedQuery + "'");

        embeddingService.embed(expandedQuery, "RETRIEVAL_QUERY").thenCompose(embedding ->
            vectorStorage.search(embedding, limit * 3, null) // Get 3x results for reranking
        ).thenAccept(results -> {
            if (results.isEmpty()) {
                source.getSender().sendMessage("§cNo containers found matching your query.");
                return;
            }

            // Apply hybrid reranking
            results = org.aincraft.chestfind.search.SearchScorer.hybridRerank(results, query, expandedQuery);

            // Take top results after reranking
            results = results.subList(0, Math.min(limit, results.size()));

            // Filter results by minimum similarity threshold
            var filteredResults = results.stream()
                .filter(r -> r.score() > 0.675) // Threshold: 67.5% minimum similarity
                .toList();

            if (filteredResults.isEmpty()) {
                source.getSender().sendMessage("§cNo containers found matching your query.");
                return;
            }

            source.getSender().sendMessage("§6Found " + filteredResults.size() + " containers:");
            for (var result : filteredResults) {
                // Filter by protection if player
                if (player != null && !protectionProvider.canAccess(player, result.location())) {
                    continue;
                }

                String coords = String.format("§7[§f%d, %d, %d§7]",
                    result.location().getBlockX(),
                    result.location().getBlockY(),
                    result.location().getBlockZ());
                String world = result.location().getWorld() != null
                    ? result.location().getWorld().getName() : "unknown";
                String distance = player != null
                    ? String.format("§7(%.1f blocks)", result.location().distance(player.getLocation()))
                    : "";
                String score = String.format("§a%.1f%%", result.score() * 100);

                source.getSender().sendMessage(coords + " §8" + world + " " + distance + " " + score);
                source.getSender().sendMessage("  §7" + result.preview());

                // Add visual highlight for players
                if (player != null && player.isOnline()) {
                    spawnHighlight(result.location(), player);
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
        player.sendMessage("§7Container will be indexed in " + chestFindConfig.getDebounceDelayMs() + "ms...");

        return 1;
    }

    @Override
    public void onDisable() {
        if (containerIndexer != null) {
            containerIndexer.shutdown();
        }
        if (vectorStorage != null) {
            vectorStorage.shutdown();
        }
        if (embeddingService != null) {
            embeddingService.shutdown();
        }
        getLogger().info("ChestFind disabled.");
    }

    public ChestFindConfig getChestFindConfig() {
        return chestFindConfig;
    }

    public ContainerIndexer getContainerIndexer() {
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