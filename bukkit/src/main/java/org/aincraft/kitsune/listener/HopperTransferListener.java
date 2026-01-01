package org.aincraft.kitsune.listener;

import org.aincraft.kitsune.api.ContainerLocations;
import org.aincraft.kitsune.indexing.BukkitContainerIndexer;
import org.aincraft.kitsune.util.ContainerLocationResolver;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

public class HopperTransferListener implements Listener {
    private final BukkitContainerIndexer containerIndexer;
    private final ContainerLocationResolver locationResolver;
    private final Plugin plugin;

    public HopperTransferListener(BukkitContainerIndexer containerIndexer, ContainerLocationResolver locationResolver, Plugin plugin) {
        this.containerIndexer = containerIndexer;
        this.locationResolver = locationResolver;
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        Inventory source = event.getSource();
        Inventory destination = event.getDestination();

        // Delay by 1 tick - event fires BEFORE item moves
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Handle source container (single or double chest)
            InventoryHolder sourceHolder = source.getHolder();
            if (sourceHolder instanceof Container || sourceHolder instanceof DoubleChest) {
                ContainerLocations sourceLocations = locationResolver.resolveLocations(sourceHolder);
                if (sourceLocations != null) {
                    containerIndexer.scheduleIndex(sourceLocations, source.getContents());
                }
            }

            // Handle destination container (single or double chest)
            InventoryHolder destHolder = destination.getHolder();
            if (destHolder instanceof Container || destHolder instanceof DoubleChest) {
                ContainerLocations destLocations = locationResolver.resolveLocations(destHolder);
                if (destLocations != null) {
                    containerIndexer.scheduleIndex(destLocations, destination.getContents());
                }
            }
        }, 1L);
    }
}
