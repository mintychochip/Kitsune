package org.aincraft.chestfind.listener;

import org.aincraft.chestfind.storage.VectorStorage;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class ContainerBreakListener implements Listener {
    private final VectorStorage vectorStorage;

    public ContainerBreakListener(VectorStorage vectorStorage) {
        this.vectorStorage = vectorStorage;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!(event.getBlock().getState() instanceof Container)) {
            return;
        }

        vectorStorage.delete(event.getBlock().getLocation()).exceptionally(ex -> {
            event.getPlayer().sendMessage("§cFailed to remove container from index: " + ex.getMessage());
            return null;
        });
    }
}
