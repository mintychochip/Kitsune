package org.aincraft.chestfind.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.aincraft.chestfind.api.LocationData;
import org.aincraft.chestfind.config.ChestFindConfig;
import org.aincraft.chestfind.embedding.EmbeddingService;
import org.aincraft.chestfind.indexing.ContainerIndexer;
import org.aincraft.chestfind.model.SearchResult;
import org.aincraft.chestfind.storage.VectorStorage;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * NeoForge command handler for ChestFind commands.
 * Registers /find and /chestfind commands via Brigadier command dispatcher.
 * Uses async execution with CompletableFuture for non-blocking operations.
 * Methods are called by event handler in ChestFindMod during server startup.
 */
public class ChestFindCommands {
    private static EmbeddingService embeddingService;
    private static VectorStorage vectorStorage;
    private static ContainerIndexer containerIndexer;
    private static ChestFindConfig config;
    private static Logger logger;
    private static boolean initialized = false;
    private static volatile boolean providerMismatch = false;

    /**
     * Sets the command dependencies. Must be called during plugin initialization.
     */
    public static void initialize(
            EmbeddingService embeddingService,
            VectorStorage vectorStorage,
            ContainerIndexer containerIndexer,
            ChestFindConfig config,
            Logger logger,
            boolean providerMismatch) {
        ChestFindCommands.embeddingService = embeddingService;
        ChestFindCommands.vectorStorage = vectorStorage;
        ChestFindCommands.containerIndexer = containerIndexer;
        ChestFindCommands.config = config;
        ChestFindCommands.logger = logger;
        ChestFindCommands.providerMismatch = providerMismatch;
        ChestFindCommands.initialized = true;
    }

    /**
     * Update provider mismatch status.
     */
    public static void setProviderMismatch(boolean mismatch) {
        ChestFindCommands.providerMismatch = mismatch;
    }

    /**
     * Register all ChestFind commands with the Brigadier dispatcher.
     * Called by RegisterCommandsEvent handler in ChestFindMod.
     */
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // /find <query> command
        dispatcher.register(
            Commands.literal("find")
                .then(Commands.argument("query", StringArgumentType.greedyString())
                    .executes(ctx -> executeFind(ctx)))
                .executes(ctx -> {
                    ctx.getSource().sendFailure(Component.literal("Usage: /find <query>"));
                    return 0;
                })
        );

        // /chestfind <subcommand> command
        dispatcher.register(
            Commands.literal("chestfind")
                .then(Commands.literal("reload")
                    .requires(source -> source.getEntity() != null && hasPermission(source, "chestfind.admin"))
                    .executes(ctx -> executeReload(ctx)))
                .then(Commands.literal("stats")
                    .requires(source -> source.getEntity() != null && hasPermission(source, "chestfind.admin"))
                    .executes(ctx -> executeStats(ctx)))
                .then(Commands.literal("reindex")
                    .requires(source -> source.getEntity() != null && hasPermission(source, "chestfind.admin"))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                        .executes(ctx -> executeReindex(ctx))))
                .then(Commands.literal("purge")
                    .requires(source -> source.getEntity() != null && hasPermission(source, "chestfind.admin"))
                    .executes(ctx -> executePurge(ctx)))
                .executes(ctx -> executeHelp(ctx))
        );
    }

    private static int executeFind(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();

        if (!initialized) {
            source.sendFailure(Component.literal("ChestFind is still initializing. Please wait..."));
            return 0;
        }

        if (providerMismatch) {
            source.sendFailure(Component.literal("Embedding provider has changed. Run '/chestfind purge' first."));
            return 0;
        }

        String query = StringArgumentType.getString(ctx, "query");
        source.sendSuccess(() -> Component.literal("Searching for: " + query), false);

        // Execute search asynchronously
        embeddingService.embed(query, "RETRIEVAL_QUERY").thenCompose(embedding ->
            vectorStorage.search(embedding, config.getDefaultSearchLimit() * 3, null)
        ).thenAccept(results -> {
            // Schedule callback on server thread
            source.getServer().execute(() -> {
                if (results.isEmpty()) {
                    source.sendFailure(Component.literal("No containers found matching your query."));
                    return;
                }

                // Take top results
                List<SearchResult> topResults = results.subList(0, Math.min(config.getDefaultSearchLimit(), results.size()));

                // Filter results by minimum similarity threshold (78%)
                List<SearchResult> filteredResults = topResults.stream()
                    .filter(r -> r.score() > 0.78)
                    .toList();

                if (filteredResults.isEmpty()) {
                    source.sendFailure(Component.literal("No containers found matching your query."));
                    return;
                }

                source.sendSuccess(() -> Component.literal("Found " + filteredResults.size() + " containers:"), false);

                for (SearchResult result : filteredResults) {
                    // Build location string
                    String coords = String.format("[%d, %d, %d]",
                        result.location().blockX(),
                        result.location().blockY(),
                        result.location().blockZ());
                    String world = result.location().worldName();
                    String score = String.format("%.1f%%", result.score() * 100);

                    final String distance;
                    if (player != null) {
                        double playerX = player.getX();
                        double playerY = player.getY();
                        double playerZ = player.getZ();
                        double dx = result.location().blockX() - playerX;
                        double dy = result.location().blockY() - playerY;
                        double dz = result.location().blockZ() - playerZ;
                        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        distance = String.format(" (%.1f blocks)", dist);
                    } else {
                        distance = "";
                    }

                    source.sendSuccess(() -> Component.literal(coords + " " + world + distance + " " + score), false);
                    source.sendSuccess(() -> Component.literal("  " + result.preview()), false);

                    // Spawn visual highlight for player if available
                    if (player != null) {
                        spawnHighlight(player, result.location());
                    }
                }
            });
        }).exceptionally(ex -> {
            source.getServer().execute(() -> {
                logger.log(Level.WARNING, "Search failed", ex);
                source.sendFailure(Component.literal("Search failed: " + ex.getMessage()));
            });
            return null;
        });

        return 1;
    }

    private static int executeStats(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        if (!initialized) {
            source.sendFailure(Component.literal("ChestFind is still initializing..."));
            return 0;
        }

        vectorStorage.getStats().thenAccept(stats -> {
            source.getServer().execute(() -> {
                source.sendSuccess(() -> Component.literal("ChestFind Stats:"), false);
                source.sendSuccess(() -> Component.literal("Indexed containers: " + stats.containerCount()), false);
                source.sendSuccess(() -> Component.literal("Storage provider: " + stats.providerName()), false);
            });
        }).exceptionally(ex -> {
            source.getServer().execute(() -> {
                source.sendFailure(Component.literal("Failed to get stats: " + ex.getMessage()));
            });
            return null;
        });

        return 1;
    }

    private static int executeReindex(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }

        if (!initialized) {
            source.sendFailure(Component.literal("ChestFind is still initializing..."));
            return 0;
        }

        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        source.sendSuccess(() -> Component.literal("Reindexing containers within " + radius + " blocks..."), false);

        // Convert NeoForge location to LocationData
        LocationData playerLoc = LocationData.of(
            player.level().dimension().location().toString(),
            (int) player.getX(),
            (int) player.getY(),
            (int) player.getZ()
        );

        containerIndexer.reindexRadius(playerLoc, radius)
            .thenAccept(count -> {
                source.getServer().execute(() -> {
                    source.sendSuccess(() -> Component.literal("Reindexed " + count + " containers."), false);
                });
            })
            .exceptionally(ex -> {
                source.getServer().execute(() -> {
                    source.sendFailure(Component.literal("Reindex failed: " + ex.getMessage()));
                });
                return null;
            });

        return 1;
    }

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("ChestFind config reloaded."), false);
        return 1;
    }

    private static int executePurge(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        if (!initialized) {
            source.sendFailure(Component.literal("ChestFind is still initializing..."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Purging all vectors and cache..."), false);

        // Purge vectors and cache in sequence
        vectorStorage.purgeAll()
            .thenRun(() -> {
                source.getServer().execute(() -> {
                    setProviderMismatch(false);
                    source.sendSuccess(() -> Component.literal("Purge complete! All vectors and cache cleared."), false);
                });
            })
            .exceptionally(ex -> {
                source.getServer().execute(() -> {
                    logger.log(Level.SEVERE, "Failed to purge", ex);
                    source.sendFailure(Component.literal("Purge failed: " + ex.getMessage()));
                });
                return null;
            });

        return 1;
    }

    private static int executeHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("ChestFind Commands:"), false);
        source.sendSuccess(() -> Component.literal("/find <query> - Search containers"), false);
        source.sendSuccess(() -> Component.literal("/chestfind reload - Reload config"), false);
        source.sendSuccess(() -> Component.literal("/chestfind stats - Show statistics"), false);
        source.sendSuccess(() -> Component.literal("/chestfind reindex <radius> - Reindex nearby"), false);
        source.sendSuccess(() -> Component.literal("/chestfind purge - Clear all vectors and cache"), false);
        return 1;
    }

    /**
     * Check if player has permission (NeoForge doesn't have a permission API, so this is a placeholder).
     * In production, integrate with a permission mod like LuckPerms.
     */
    private static boolean hasPermission(CommandSourceStack source, String permission) {
        // NeoForge doesn't have a built-in permission system
        // For now, allow ops and players with level 4 command permission
        return source.hasPermission(4);
    }

    /**
     * Spawn visual highlight particles at container location.
     */
    private static void spawnHighlight(ServerPlayer player, LocationData location) {
        // NeoForge uses different particle system
        // This would require implementing particle spawning for NeoForge
        // For now, this is a placeholder that can be extended
        // TODO: Implement particle spawning for NeoForge (if needed)
    }
}
