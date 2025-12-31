package org.aincraft.chestfind.client.keybind;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.aincraft.chestfind.client.screen.SearchScreen;
import org.lwjgl.glfw.GLFW;

/**
 * Manages keybindings for ChestFind.
 */
@Environment(EnvType.CLIENT)
public class ChestFindKeybindings {
    private static KeyBinding openSearchKey;

    private ChestFindKeybindings() {
    }

    /**
     * Register all keybindings.
     */
    public static void register() {
        openSearchKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chestfind.search",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "key.chestfind.category"
        ));
    }

    /**
     * Register the tick handler to process keybindings.
     */
    public static void registerTickHandler() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tick(client);
        });
    }

    private static void tick(MinecraftClient client) {
        while (openSearchKey.wasPressed()) {
            if (client.currentScreen == null) {
                client.setScreen(new SearchScreen());
            }
        }
    }
}
