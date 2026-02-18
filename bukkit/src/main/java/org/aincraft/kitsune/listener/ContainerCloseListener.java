package org.aincraft.kitsune.listener;

import org.aincraft.kitsune.api.ContainerLocations;
import org.aincraft.kitsune.api.indexing.ContainerLocationResolver;
import org.aincraft.kitsune.indexing.BukkitContainerIndexer;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class ContainerCloseListener implements Listener {
    private final BukkitContainerIndexer containerIndexer;
    private final ContainerLocationResolver locationResolver;
    private final Plugin plugin;
    private final Logger logger = LoggerFactory.getLogger(ContainerCloseListener.class);

    public ContainerCloseListener(BukkitContainerIndexer containerIndexer,
                               ContainerLocationResolver locationResolver,
                               Plugin plugin) {
        this.containerIndexer = containerIndexer;
        this.locationResolver = locationResolver;
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        // Handle both single containers and double chests
        if (!(holder instanceof Container) && !(holder instanceof DoubleChest)) {
            return;
        }

        // Resolve container locations (handles single and multi-block)
        ContainerLocations locations = locationResolver.resolveFromInventoryHolder(holder);
        if (locations == null) {
            return;
        }

        // Clone the inventory contents before leaving main thread
        ItemStack[] inventoryContents = event.getInventory().getContents().clone();

        // Schedule async indexing operation
        CompletableFuture.runAsync(() -> {
            try {
                // Perform indexing asynchronously
                containerIndexer.scheduleIndex(locations, inventoryContents);
            } catch (Exception e) {
                logger.error("Failed to index container asynchronously", e);
            }
        }).exceptionally(ex -> {
            logger.error("Error during container close indexing", ex);
            return null;
        });
    }
}
