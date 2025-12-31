package org.aincraft.kitsune.listener;

import org.aincraft.kitsune.api.LocationData;
import org.aincraft.kitsune.storage.VectorStorage;
import org.aincraft.kitsune.util.LocationConverter;
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

        // Convert broken block location
        LocationData brokenLocation = LocationConverter.toLocationData(event.getBlock().getLocation());

        // Look up the primary location for this block (handles multi-block containers)
        vectorStorage.getPrimaryLocation(brokenLocation)
            .thenCompose(primaryOpt -> {
                LocationData primaryLocation = primaryOpt.orElse(brokenLocation);

                // Delete the container from the index
                return vectorStorage.delete(primaryLocation)
                    .thenCompose(unused -> {
                        // Clean up position mappings
                        return vectorStorage.deleteContainerPositions(primaryLocation);
                    });
            })
            .exceptionally(ex -> {
                event.getPlayer().sendMessage("§cFailed to remove container from index: " + ex.getMessage());
                return null;
            });
    }
}
