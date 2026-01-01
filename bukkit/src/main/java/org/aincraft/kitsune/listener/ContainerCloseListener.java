package org.aincraft.kitsune.listener;

import org.aincraft.kitsune.api.ContainerLocations;
import org.aincraft.kitsune.indexing.BukkitContainerIndexer;
import org.aincraft.kitsune.util.ContainerLocationResolver;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

public class ContainerCloseListener implements Listener {
    private final BukkitContainerIndexer containerIndexer;
    private final ContainerLocationResolver locationResolver;

    public ContainerCloseListener(BukkitContainerIndexer containerIndexer, ContainerLocationResolver locationResolver) {
        this.containerIndexer = containerIndexer;
        this.locationResolver = locationResolver;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        // Handle both single containers and double chests
        if (!(holder instanceof Container) && !(holder instanceof DoubleChest)) {
            return;
        }

        // Resolve container locations (handles single and multi-block)
        ContainerLocations locations = locationResolver.resolveLocations(holder);
        if (locations == null) {
            return;
        }

        // Index unconditionally on close
        containerIndexer.scheduleIndex(locations, event.getInventory().getContents());
    }
}
