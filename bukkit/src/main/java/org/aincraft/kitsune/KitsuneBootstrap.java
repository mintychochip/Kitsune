package org.aincraft.kitsune;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.jetbrains.annotations.NotNull;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper plugin bootstrapper for Kitsune.
 * Handles early initialization before the plugin is fully loaded.
 */
@SuppressWarnings("UnstableApiUsage")
public class KitsuneBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        // Early bootstrap phase - runs before the server is fully started
        // Can be used for:
        // - Registering custom registry entries
        // - Setting up datapack discovery handlers
        // - Early command registration via LifecycleEvents.COMMANDS

        context.getLogger().info("Kitsune bootstrapping...");
    }

    @Override
    public @NotNull JavaPlugin createPlugin(@NotNull PluginProviderContext context) {
        // Create and return the main plugin instance
        // This allows passing bootstrap context to the plugin if needed
        return new KitsunePlugin();
    }
}
