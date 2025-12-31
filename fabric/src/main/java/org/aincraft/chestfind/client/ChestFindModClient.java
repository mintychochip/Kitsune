package org.aincraft.chestfind.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.aincraft.chestfind.client.keybind.ChestFindKeybindings;
import org.aincraft.chestfind.client.render.ContainerHighlightRenderer;
import org.aincraft.chestfind.network.NetworkHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side mod initializer for ChestFind.
 * Handles keybindings, rendering, and client networking.
 */
@Environment(EnvType.CLIENT)
public class ChestFindModClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChestFind");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing ChestFind client...");

        // Register keybindings
        ChestFindKeybindings.register();

        // Register client tick handler for keybindings
        ChestFindKeybindings.registerTickHandler();

        // Register network receivers
        NetworkHandler.registerClientReceivers();

        // Register highlight renderer
        ContainerHighlightRenderer.register();

        LOGGER.info("ChestFind client initialized successfully!");
    }
}
