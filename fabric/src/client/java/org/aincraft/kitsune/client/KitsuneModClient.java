package org.aincraft.kitsune.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.aincraft.kitsune.client.keybind.KitsuneKeybindings;
import org.aincraft.kitsune.client.render.ContainerHighlightRenderer;
import org.aincraft.kitsune.network.ClientNetworkHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side mod initializer for Kitsune.
 * Handles keybindings, rendering, and client networking.
 */
@Environment(EnvType.CLIENT)
public class KitsuneModClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Kitsune");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Kitsune client...");

        // Register keybindings
        KitsuneKeybindings.register();

        // Register client tick handler for keybindings
        KitsuneKeybindings.registerTickHandler();

        // Register network receivers
        ClientNetworkHandler.registerClientReceivers();

        // Register highlight renderer
        ContainerHighlightRenderer.register();

        LOGGER.info("Kitsune client initialized successfully!");
    }
}
