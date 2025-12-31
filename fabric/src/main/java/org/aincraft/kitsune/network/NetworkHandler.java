package org.aincraft.kitsune.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.aincraft.kitsune.KitsuneMod;
import org.aincraft.kitsune.api.Location;
import org.aincraft.kitsune.model.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Server-side network handler for client-server communication.
 * Handles search requests from clients and sends results back.
 */
public class NetworkHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("Kitsune");

    // Packet identifiers
    public static final Identifier SEARCH_REQUEST = new Identifier(KitsuneMod.MOD_ID, "search_request");
    public static final Identifier SEARCH_RESULTS = new Identifier(KitsuneMod.MOD_ID, "search_results");

    private NetworkHandler() {
    }

    /**
     * Register payload types (called early in mod init).
     */
    public static void registerPayloads() {
        // For 1.20.1, payloads are registered implicitly via the channel identifier
        LOGGER.info("Kitsune network channels registered");
    }

    /**
     * Register server-side packet receivers.
     */
    public static void registerServerReceivers(KitsuneMod mod) {
        ServerPlayNetworking.registerGlobalReceiver(SEARCH_REQUEST, (server, player, handler, buf, responseSender) -> {
            String query = buf.readString();
            int limit = buf.readVarInt();

            server.execute(() -> handleSearchRequest(mod, player, query, limit));
        });

        LOGGER.info("Kitsune server packet receivers registered");
    }

    private static void handleSearchRequest(KitsuneMod mod, ServerPlayerEntity player, String query, int limit) {
        if (!mod.isInitialized()) {
            LOGGER.warn("Search request from {} ignored - mod not initialized", player.getName().getString());
            return;
        }

        mod.getEmbeddingService().embed(query, "RETRIEVAL_QUERY")
                .thenCompose(embedding ->
                        mod.getVectorStorage().search(embedding, limit, null))
                .thenAccept(results -> {
                    // Filter by threshold
                    List<SearchResult> filtered = results.stream()
                            .filter(r -> r.score() > 0.675)
                            .toList();

                    // Send results to client
                    sendSearchResults(player, filtered);
                })
                .exceptionally(ex -> {
                    mod.getKitsunePlugin().getLogger().log(Level.WARNING, "Search failed for " + player.getName().getString(), ex);
                    sendSearchResults(player, List.of());
                    return null;
                });
    }

    /**
     * Send search results to a player.
     */
    public static void sendSearchResults(ServerPlayerEntity player, List<SearchResult> results) {
        PacketByteBuf buf = PacketByteBufs.create();
        writeSearchResults(buf, results);
        ServerPlayNetworking.send(player, SEARCH_RESULTS, buf);
    }

    /**
     * Write search results to a packet buffer.
     * Public so client can reuse for reading.
     */
    public static void writeSearchResults(PacketByteBuf buf, List<SearchResult> results) {
        buf.writeVarInt(results.size());
        for (SearchResult result : results) {
            // Write location
            buf.writeString(result.location().worldName());
            buf.writeInt(result.location().blockX());
            buf.writeInt(result.location().blockY());
            buf.writeInt(result.location().blockZ());

            // Write score and preview
            buf.writeFloat((float) result.score());
            buf.writeString(result.preview());
            buf.writeString(result.fullContent() != null ? result.fullContent() : "");
        }
    }

    /**
     * Read search results from a packet buffer.
     * Public so client can use.
     */
    public static List<SearchResult> readSearchResults(PacketByteBuf buf) {
        int count = buf.readVarInt();
        List<SearchResult> results = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            String worldName = buf.readString();
            int x = buf.readInt();
            int y = buf.readInt();
            int z = buf.readInt();
            float score = buf.readFloat();
            String preview = buf.readString();
            String fullContent = buf.readString();

            Location location = Location.of(worldName, x, y, z);
            results.add(new SearchResult(location, score, preview, fullContent));
        }

        return results;
    }
}
