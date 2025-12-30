package org.aincraft.chestfind.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.aincraft.chestfind.indexing.ContainerIndexer;
import org.bukkit.Location;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ContainerCloseListener implements Listener {
    private final Map<UUID, ContainerSnapshot> openContainers = new HashMap<>();

    private final ContainerIndexer containerIndexer;

    public ContainerCloseListener(ContainerIndexer containerIndexer) {
        this.containerIndexer = containerIndexer;
    }

    @EventHandler
    public void onInventoryOpen(final InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder() instanceof Container container)) {
            return;
        }

        UUID playerUuid = event.getPlayer().getUniqueId();
        Inventory inventory = event.getInventory();

        // Snapshot the current state when opening
        openContainers.put(playerUuid, new ContainerSnapshot(
            container.getLocation(),
            copyInventory(inventory.getContents())
        ));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Container container)) {
            return;
        }

        UUID playerUuid = event.getPlayer().getUniqueId();
        ContainerSnapshot snapshot = openContainers.remove(playerUuid);

        // If no snapshot, player didn't open it normally - re-index anyway
        if (snapshot == null) {
            containerIndexer.scheduleIndex(container.getLocation(), event.getInventory().getContents());
            return;
        }

        // Compare state: if nothing changed, don't re-embed
        ItemStack[] currentContents = event.getInventory().getContents();
        if (inventoriesEqual(snapshot.contents, currentContents)) {
            return; // No changes, skip indexing
        }

        // State changed - re-index
        containerIndexer.scheduleIndex(container.getLocation(), currentContents);
    }

    private boolean inventoriesEqual(ItemStack[] original, ItemStack[] current) {
        if (original.length != current.length) {
            return false;
        }

        for (int i = 0; i < original.length; i++) {
            ItemStack orig = original[i];
            ItemStack curr = current[i];

            // Both null is equal
            if (orig == null && curr == null) {
                continue;
            }

            // One null, one not - not equal
            if ((orig == null) != (curr == null)) {
                return false;
            }

            // Different type or amount
            if (orig.getType() != curr.getType() || orig.getAmount() != curr.getAmount()) {
                return false;
            }

            // Check if enchantments or other metadata changed
            if (!orig.equals(curr)) {
                return false;
            }
        }

        return true;
    }

    private ItemStack[] copyInventory(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] != null ? contents[i].clone() : null;
        }
        return copy;
    }

    private static class ContainerSnapshot {
        final Location location;
        final ItemStack[] contents;

        ContainerSnapshot(Location location, ItemStack[] contents) {
            this.location = location;
            this.contents = contents;
        }
    }
}
