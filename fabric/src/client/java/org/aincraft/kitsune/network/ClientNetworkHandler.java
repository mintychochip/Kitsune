package org.aincraft.kitsune.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import org.aincraft.kitsune.model.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Client-side network handler for Kitsune.
 * Handles receiving search results and sending search requests.
 */
@Environment(EnvType.CLIENT)
public class ClientNetworkHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("Kitsune");

    private ClientNetworkHandler() {
    }

    /**
     * Register client-side packet receivers.
     * Called from ClientModInitializer.
     */
    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.SEARCH_RESULTS, (client, handler, buf, responseSender) -> {
            List<SearchResult> results = NetworkHandler.readSearchResults(buf);

            client.execute(() -> {
                // Handle results on client - update UI and highlights
                ClientSearchResultHandler.handleResults(results);
            });
        });

        LOGGER.info("Kitsune client packet receivers registered");
    }

    /**
     * Send a search request from client to server.
     */
    public static void sendSearchRequest(String query, int limit) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(query);
        buf.writeVarInt(limit);
        ClientPlayNetworking.send(NetworkHandler.SEARCH_REQUEST, buf);
    }
}
