package org.aincraft.chestfind.listener;

import org.aincraft.chestfind.indexing.ContainerIndexer;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;

public class HopperTransferListener implements Listener {
    private final ContainerIndexer containerIndexer;

    public HopperTransferListener(ContainerIndexer containerIndexer) {
        this.containerIndexer = containerIndexer;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        Inventory source = event.getSource();
        Inventory destination = event.getDestination();

        if (source.getHolder() instanceof Container sourceContainer) {
            containerIndexer.scheduleIndex(sourceContainer.getLocation(), source.getContents());
        }

        if (destination.getHolder() instanceof Container destContainer) {
            containerIndexer.scheduleIndex(destContainer.getLocation(), destination.getContents());
        }
    }
}
