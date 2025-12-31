package org.aincraft.kitsune.listener;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.aincraft.kitsune.indexing.FabricContainerIndexer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles container close events to trigger indexing.
 * Uses snapshot comparison to only index changed containers.
 */
public class ContainerCloseCallback {
    private static final Logger LOGGER = LoggerFactory.getLogger("Kitsune");

    // Track container snapshots per player
    private static final Map<UUID, ContainerSnapshot> openContainers = new ConcurrentHashMap<>();

    /**
     * Custom event for when a container screen is closed.
     * This should be fired from a mixin into ScreenHandler.onClosed
     */
    public static final Event<ContainerClosed> CONTAINER_CLOSED = EventFactory.createArrayBacked(
            ContainerClosed.class,
            callbacks -> (player, handler) -> {
                for (ContainerClosed callback : callbacks) {
                    callback.onContainerClosed(player, handler);
                }
            }
    );

    @FunctionalInterface
    public interface ContainerClosed {
        void onContainerClosed(ServerPlayerEntity player, ScreenHandler handler);
    }

    private static FabricContainerIndexer indexer;

    private ContainerCloseCallback() {
    }

    /**
     * Register the container close callback.
     */
    public static void register(FabricContainerIndexer containerIndexer) {
        indexer = containerIndexer;

        CONTAINER_CLOSED.register((player, handler) -> {
            if (indexer == null) {
                return;
            }

            UUID playerId = player.getUuid();
            ContainerSnapshot snapshot = openContainers.remove(playerId);

            if (snapshot == null) {
                return;
            }

            // Get current container state
            ServerWorld world = player.getServerWorld();
            BlockEntity blockEntity = world.getBlockEntity(snapshot.pos);

            if (!(blockEntity instanceof Inventory inventory)) {
                return;
            }

            // Compare with snapshot
            if (hasChanged(snapshot.items, inventory)) {
                LOGGER.debug("Container changed at {}, scheduling index", snapshot.pos);
                indexer.scheduleIndex(world, snapshot.pos, inventory);
            }
        });
    }

    /**
     * Track when a player opens a container.
     * This should be called from a mixin or event when a container screen opens.
     */
    public static void onContainerOpened(ServerPlayerEntity player, BlockPos pos, Inventory inventory) {
        ItemStack[] snapshot = new ItemStack[inventory.size()];
        for (int i = 0; i < inventory.size(); i++) {
            snapshot[i] = inventory.getStack(i).copy();
        }
        openContainers.put(player.getUuid(), new ContainerSnapshot(pos, snapshot));
    }

    private static boolean hasChanged(ItemStack[] snapshot, Inventory current) {
        if (snapshot.length != current.size()) {
            return true;
        }

        for (int i = 0; i < snapshot.length; i++) {
            ItemStack snapshotStack = snapshot[i];
            ItemStack currentStack = current.getStack(i);

            if (!ItemStack.areEqual(snapshotStack, currentStack)) {
                return true;
            }
        }

        return false;
    }

    private record ContainerSnapshot(BlockPos pos, ItemStack[] items) {
    }
}
