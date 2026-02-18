package org.aincraft.kitsune.listener;

import org.aincraft.kitsune.visualizer.ContainerItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Cleans up visualizations when players quit the server.
 */
public class PlayerQuitListener implements Listener {

    private final ContainerItemDisplay itemDisplayVisualizer;

    public PlayerQuitListener(ContainerItemDisplay itemDisplayVisualizer) {
        this.itemDisplayVisualizer = itemDisplayVisualizer;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up all displays for the player who quit
        itemDisplayVisualizer.removeDisplaysForPlayer(event.getPlayer());
    }
}