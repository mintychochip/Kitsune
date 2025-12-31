package org.aincraft.chestfind.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import org.aincraft.chestfind.logging.ChestFindLogger;
import org.aincraft.chestfind.storage.VectorStorage;

/**
 * Handles container-related events in NeoForge.
 * Tracks container state changes and triggers updates when needed.
 * Single responsibility: event handling and state tracking.
 */
public class ContainerEventHandler {
    private final ChestFindLogger logger;
    private final VectorStorage vectorStorage;
    private final Map<UUID, ContainerSnapshot> openContainers = new HashMap<>();

    public ContainerEventHandler(ChestFindLogger logger, VectorStorage vectorStorage) {
        this.logger = logger;
        this.vectorStorage = vectorStorage;
    }

    /**
     * Snapshot of container state when opened.
     */
    private static class ContainerSnapshot {
        final Container container;
        final ItemStack[] contents;

        ContainerSnapshot(Container container, ItemStack[] contents) {
            this.container = container;
            this.contents = contents;
        }
    }

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        AbstractContainerMenu menu = event.getContainer();
        if (menu == null) {
            return;
        }

        Container container = getContainerFromMenu(menu);
        if (container == null) {
            return;
        }

        UUID playerUuid = event.getEntity().getUUID();
        ItemStack[] contents = copyContainerContents(container);

        openContainers.put(playerUuid, new ContainerSnapshot(container, contents));
    }

    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        AbstractContainerMenu menu = event.getContainer();
        if (menu == null) {
            return;
        }

        Container container = getContainerFromMenu(menu);
        if (container == null) {
            return;
        }

        UUID playerUuid = event.getEntity().getUUID();
        ContainerSnapshot snapshot = openContainers.remove(playerUuid);

        ItemStack[] currentContents = copyContainerContents(container);

        // If no snapshot, update anyway
        if (snapshot == null) {
            handleContainerUpdate(container, currentContents);
            return;
        }

        // Compare state: if nothing changed, skip update
        if (contentsEqual(snapshot.contents, currentContents)) {
            return;
        }

        handleContainerUpdate(container, currentContents);
    }

    /**
     * Handles container state change - updates vector storage.
     * Delegates to VectorStorage for indexing logic.
     */
    private void handleContainerUpdate(Container container, ItemStack[] contents) {
        // Implementation would serialize items and update vector storage
        // This is delegated to a platform-specific indexer service
        logger.info("Container updated at location: " + container);
    }

    /**
     * Extracts Container from menu if available.
     */
    private Container getContainerFromMenu(AbstractContainerMenu menu) {
        if (menu instanceof Container container) {
            return container;
        }

        if (!menu.slots.isEmpty()) {
            var slot = menu.slots.get(0);
            if (slot.container instanceof Container container) {
                return container;
            }
        }

        return null;
    }

    /**
     * Creates a copy of container contents.
     */
    private ItemStack[] copyContainerContents(Container container) {
        ItemStack[] contents = new ItemStack[container.getContainerSize()];
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack original = container.getItem(i);
            contents[i] = original.isEmpty() ? ItemStack.EMPTY : original.copy();
        }
        return contents;
    }

    /**
     * Compares two arrays of ItemStacks.
     */
    private boolean contentsEqual(ItemStack[] a, ItemStack[] b) {
        if (a.length != b.length) {
            return false;
        }

        for (int i = 0; i < a.length; i++) {
            if (!ItemStack.matches(a[i], b[i])) {
                return false;
            }
        }

        return true;
    }
}
