package org.aincraft.kitsune.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.aincraft.kitsune.KitsuneMod;
import org.aincraft.kitsune.model.SearchResult;
import org.aincraft.kitsune.platform.FabricLocationFactory;

import java.util.List;
import java.util.logging.Level;

/**
 * Brigadier commands for Kitsune.
 */
public class KitsuneCommands {
    private KitsuneCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, KitsuneMod mod) {
        // /find <query>
        dispatcher.register(
                CommandManager.literal("find")
                        .then(CommandManager.argument("query", StringArgumentType.greedyString())
                                .executes(ctx -> executeSearch(ctx, mod,
                                        StringArgumentType.getString(ctx, "query"),
                                        mod.getKitsuneConfig().getDefaultSearchLimit())))
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(() ->
                                    Text.literal("Usage: /find <query>").formatted(Formatting.RED), false);
                            return 0;
                        })
        );

        // /kitsune <subcommand>
        dispatcher.register(
                CommandManager.literal("kitsune")
                        .then(CommandManager.literal("reload")
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(ctx -> {
                                    mod.reloadConfig();
                                    ctx.getSource().sendFeedback(() ->
                                            Text.literal("Kitsune config reloaded.").formatted(Formatting.GREEN), true);
                                    return 1;
                                }))
                        .then(CommandManager.literal("stats")
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(ctx -> executeStats(ctx, mod)))
                        .then(CommandManager.literal("reindex")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(CommandManager.argument("radius", IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> executeReindex(ctx, mod,
                                                IntegerArgumentType.getInteger(ctx, "radius")))))
                        .then(CommandManager.literal("purge")
                                .requires(source -> source.hasPermissionLevel(4))
                                .executes(ctx -> executePurge(ctx, mod)))
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(() -> Text.literal("Kitsune Commands:")
                                    .formatted(Formatting.GOLD), false);
                            ctx.getSource().sendFeedback(() -> Text.literal("/find <query>")
                                    .formatted(Formatting.GRAY)
                                    .append(Text.literal(" - Search containers").formatted(Formatting.WHITE)), false);
                            ctx.getSource().sendFeedback(() -> Text.literal("/kitsune reload")
                                    .formatted(Formatting.GRAY)
                                    .append(Text.literal(" - Reload config").formatted(Formatting.WHITE)), false);
                            ctx.getSource().sendFeedback(() -> Text.literal("/kitsune stats")
                                    .formatted(Formatting.GRAY)
                                    .append(Text.literal(" - Show statistics").formatted(Formatting.WHITE)), false);
                            ctx.getSource().sendFeedback(() -> Text.literal("/kitsune reindex <radius>")
                                    .formatted(Formatting.GRAY)
                                    .append(Text.literal(" - Reindex nearby").formatted(Formatting.WHITE)), false);
                            ctx.getSource().sendFeedback(() -> Text.literal("/kitsune purge")
                                    .formatted(Formatting.GRAY)
                                    .append(Text.literal(" - Clear all indexed data").formatted(Formatting.WHITE)), false);
                            return 1;
                        })
        );
    }

    private static int executeSearch(CommandContext<ServerCommandSource> ctx, KitsuneMod mod, String query, int limit) {
        ServerCommandSource source = ctx.getSource();

        if (!mod.isInitialized()) {
            source.sendFeedback(() ->
                    Text.literal("Kitsune is still initializing. Please wait...").formatted(Formatting.RED), false);
            return 0;
        }

        ServerPlayerEntity player = source.getPlayer();
        source.sendFeedback(() ->
                Text.literal("Searching for: ").formatted(Formatting.GRAY)
                        .append(Text.literal(query).formatted(Formatting.WHITE)), false);

        mod.getEmbeddingService().embed(query, "RETRIEVAL_QUERY")
                .thenCompose(embedding ->
                        mod.getVectorStorage().search(embedding, limit, null))
                .thenAccept(results -> {
                    if (results.isEmpty()) {
                        source.sendFeedback(() ->
                                Text.literal("No containers found matching your query.")
                                        .formatted(Formatting.RED), false);
                        return;
                    }

                    // Filter results by minimum similarity threshold
                    List<SearchResult> filteredResults = results.stream()
                            .filter(r -> r.score() > 0.675)
                            .toList();

                    if (filteredResults.isEmpty()) {
                        source.sendFeedback(() ->
                                Text.literal("No containers found matching your query.")
                                        .formatted(Formatting.RED), false);
                        return;
                    }

                    source.sendFeedback(() ->
                            Text.literal("Found " + filteredResults.size() + " containers:")
                                    .formatted(Formatting.GOLD), false);

                    for (SearchResult result : filteredResults) {
                        String coords = String.format("[%d, %d, %d]",
                                result.location().blockX(),
                                result.location().blockY(),
                                result.location().blockZ());

                        String distance = "";
                        if (player != null) {
                            double dist = FabricLocationFactory.distance(
                                    FabricLocationFactory.toLocationData(
                                            player.getServerWorld(),
                                            player.getBlockPos()),
                                    result.location()
                            );
                            if (dist >= 0) {
                                distance = String.format(" (%.1f blocks)", dist);
                            }
                        }

                        String score = String.format("%.1f%%", result.score() * 100);
                        String finalDistance = distance;

                        source.sendFeedback(() ->
                                Text.literal(coords).formatted(Formatting.GRAY)
                                        .append(Text.literal(" " + result.location().worldName())
                                                .formatted(Formatting.DARK_GRAY))
                                        .append(Text.literal(finalDistance).formatted(Formatting.GRAY))
                                        .append(Text.literal(" " + score).formatted(Formatting.GREEN)), false);

                        source.sendFeedback(() ->
                                Text.literal("  " + result.preview()).formatted(Formatting.GRAY), false);
                    }
                })
                .exceptionally(ex -> {
                    mod.getKitsunePlugin().getLogger().log(Level.WARNING, "Search failed", ex);
                    source.sendFeedback(() ->
                            Text.literal("Search failed: " + ex.getMessage()).formatted(Formatting.RED), false);
                    return null;
                });

        return 1;
    }

    private static int executeStats(CommandContext<ServerCommandSource> ctx, KitsuneMod mod) {
        ServerCommandSource source = ctx.getSource();

        if (!mod.isInitialized()) {
            source.sendFeedback(() ->
                    Text.literal("Kitsune is still initializing...").formatted(Formatting.RED), false);
            return 0;
        }

        mod.getVectorStorage().getStats().thenAccept(stats -> {
            source.sendFeedback(() -> Text.literal("Kitsune Stats:").formatted(Formatting.GOLD), false);
            source.sendFeedback(() -> Text.literal("Indexed containers: ")
                    .formatted(Formatting.GRAY)
                    .append(Text.literal(String.valueOf(stats.containerCount())).formatted(Formatting.WHITE)), false);
            source.sendFeedback(() -> Text.literal("Storage provider: ")
                    .formatted(Formatting.GRAY)
                    .append(Text.literal(stats.providerName()).formatted(Formatting.WHITE)), false);
        });

        return 1;
    }

    private static int executeReindex(CommandContext<ServerCommandSource> ctx, KitsuneMod mod, int radius) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendFeedback(() ->
                    Text.literal("This command can only be used by players.").formatted(Formatting.RED), false);
            return 0;
        }

        if (!mod.isInitialized()) {
            source.sendFeedback(() ->
                    Text.literal("Kitsune is still initializing...").formatted(Formatting.RED), false);
            return 0;
        }

        source.sendFeedback(() ->
                Text.literal("Reindexing containers within " + radius + " blocks...")
                        .formatted(Formatting.GRAY), false);

        mod.getContainerIndexer().reindexRadius(player.getServerWorld(), player.getBlockPos(), radius)
                .thenAccept(count -> source.sendFeedback(() ->
                        Text.literal("Reindexed " + count + " containers.").formatted(Formatting.GREEN), false));

        return 1;
    }

    private static int executePurge(CommandContext<ServerCommandSource> ctx, KitsuneMod mod) {
        ServerCommandSource source = ctx.getSource();

        if (!mod.isInitialized()) {
            source.sendFeedback(() ->
                    Text.literal("Kitsune is still initializing...").formatted(Formatting.RED), false);
            return 0;
        }

        mod.getVectorStorage().purgeAll().thenRun(() ->
                source.sendFeedback(() ->
                        Text.literal("All indexed containers have been purged.").formatted(Formatting.GREEN), true));

        return 1;
    }
}
