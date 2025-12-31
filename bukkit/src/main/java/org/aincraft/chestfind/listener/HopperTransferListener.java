package org.aincraft.chestfind.listener;

import org.aincraft.chestfind.api.ContainerLocations;
import org.aincraft.chestfind.indexing.ContainerIndexer;
import org.aincraft.chestfind.util.ContainerLocationResolver;
import org.aincraft.chestfind.util.LocationConverter;
import org.bukkit.Location;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;

public class HopperTransferListener implements Listener {
    private final ContainerIndexer containerIndexer;
    private final ContainerLocationResolver locationResolver;

    public HopperTransferListener(ContainerIndexer containerIndexer, ContainerLocationResolver locationResolver) {
        this.containerIndexer = containerIndexer;
        this.locationResolver = locationResolver;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        Inventory source = event.getSource();
        Inventory destination = event.getDestination();

        // Handle source container
        if (source.getHolder() instanceof Container) {
            ContainerLocations sourceLocations = locationResolver.resolveLocations(source.getHolder());
            if (sourceLocations != null) {
                Location sourceLocation = LocationConverter.toBukkitLocation(sourceLocations.primaryLocation());
                if (sourceLocation != null) {
                    containerIndexer.scheduleIndex(sourceLocation, source.getContents());
                }
            }
        }

        // Handle destination container
        if (destination.getHolder() instanceof Container) {
            ContainerLocations destLocations = locationResolver.resolveLocations(destination.getHolder());
            if (destLocations != null) {
                Location destLocation = LocationConverter.toBukkitLocation(destLocations.primaryLocation());
                if (destLocation != null) {
                    containerIndexer.scheduleIndex(destLocation, destination.getContents());
                }
            }
        }
    }
}
