package org.aincraft.kitsune.listener;

import org.aincraft.kitsune.api.ContainerLocations;
import org.aincraft.kitsune.indexing.BukkitContainerIndexer;
import org.aincraft.kitsune.util.ContainerLocationResolver;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;

public class HopperTransferListener implements Listener {
    private final BukkitContainerIndexer containerIndexer;
    private final ContainerLocationResolver locationResolver;

    public HopperTransferListener(BukkitContainerIndexer containerIndexer, ContainerLocationResolver locationResolver) {
        this.containerIndexer = containerIndexer;
        this.locationResolver = locationResolver;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        Inventory source = event.getSource();
        Inventory destination = event.getDestination();

        // Handle source container (pass full ContainerLocations for multi-block support)
        if (source.getHolder() instanceof Container) {
            ContainerLocations sourceLocations = locationResolver.resolveLocations(source.getHolder());
            if (sourceLocations != null) {
                containerIndexer.scheduleIndex(sourceLocations, source.getContents());
            }
        }

        // Handle destination container (pass full ContainerLocations for multi-block support)
        if (destination.getHolder() instanceof Container) {
            ContainerLocations destLocations = locationResolver.resolveLocations(destination.getHolder());
            if (destLocations != null) {
                containerIndexer.scheduleIndex(destLocations, destination.getContents());
            }
        }
    }
}
