package org.aincraft.chestfind.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import org.aincraft.chestfind.api.LocationData;
import org.aincraft.chestfind.indexing.ContainerIndexer;

/**
 * NeoForge event handler for container open/close events.
 * Snapshots container contents when opened and schedules reindexing if changed.
 */
@EventBusSubscriber(modid = "chestfind", bus = EventBusSubscriber.Bus.GAME)
public class ContainerCloseHandler {
    private static final Map<UUID, ContainerSnapshot> openContainers = new HashMap<>();
    private static ContainerIndexer indexer;

    /**
     * Sets the ContainerIndexer instance. Must be called during plugin initialization.
     */
    public static void setIndexer(ContainerIndexer newIndexer) {
        indexer = newIndexer;
    }

    @SubscribeEvent
    static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (indexer == null) return;

        Player player = event.getEntity();
        AbstractContainerMenu menu = event.getContainer();

        // Get the BlockEntity from the container menu
        BlockEntity be = getBlockEntityFromMenu(menu);
        if (!(be instanceof BaseContainerBlockEntity container)) {
            return;
        }

        UUID playerUuid = player.getUUID();

        // Snapshot current container contents
        int size = container.getContainerSize();
        ItemSnapshot[] contents = new ItemSnapshot[size];
        for (int i = 0; i < size; i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                contents[i] = new ItemSnapshot(
                    stack.getItem().toString(),
                    stack.getCount(),
                    getComponentsHash(stack)
                );
            }
        }

        openContainers.put(playerUuid, new ContainerSnapshot(container, contents));
    }

    @SubscribeEvent
    static void onContainerClose(PlayerContainerEvent.Close event) {
        if (indexer == null) return;

        Player player = event.getEntity();
        AbstractContainerMenu menu = event.getContainer();

        // Get the BlockEntity from the container menu
        BlockEntity be = getBlockEntityFromMenu(menu);
        if (!(be instanceof BaseContainerBlockEntity container)) {
            return;
        }

        UUID playerUuid = player.getUUID();
        ContainerSnapshot snapshot = openContainers.remove(playerUuid);

        // If no snapshot, player didn't open it normally - re-index anyway
        if (snapshot == null) {
            scheduleIndexing(container, player.level());
            return;
        }

        // Compare current state vs snapshot
        int size = container.getContainerSize();
        ItemSnapshot[] current = new ItemSnapshot[size];
        for (int i = 0; i < size; i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                current[i] = new ItemSnapshot(
                    stack.getItem().toString(),
                    stack.getCount(),
                    getComponentsHash(stack)
                );
            }
        }

        // If contents changed, schedule reindexing
        if (!contentsEqual(snapshot.contents, current)) {
            scheduleIndexing(container, player.level());
        }
    }

    private static void scheduleIndexing(BaseContainerBlockEntity container, net.minecraft.world.level.Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // Get container items
        int size = container.getContainerSize();
        var items = new net.minecraft.world.item.ItemStack[size];
        for (int i = 0; i < size; i++) {
            items[i] = container.getItem(i);
        }

        // Convert to LocationData
        LocationData location = NeoForgeLocationConverter.toLocationData(container, serverLevel);

        // Schedule indexing via ContainerIndexer
        // Note: ContainerIndexer.scheduleIndex expects Bukkit Location
        // We need to adapt this - for now, we'll log the intention
        // TODO: Extend ContainerIndexer with NeoForge-compatible methods
        // or create a NeoForge-specific indexer adapter
    }

    private static BlockEntity getBlockEntityFromMenu(AbstractContainerMenu menu) {
        // AbstractContainerMenu doesn't directly expose BlockEntity in vanilla
        // Try various approaches to find the associated block entity
        try {
            // Attempt 1: Check for blockEntity field (some container implementations have this)
            var fields = AbstractContainerMenu.class.getDeclaredFields();
            for (var field : fields) {
                if (BlockEntity.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Object obj = field.get(menu);
                    if (obj instanceof BlockEntity) {
                        return (BlockEntity) obj;
                    }
                }
            }
        } catch (Exception e) {
            // Continue to next approach
        }

        return null;
    }

    private static boolean contentsEqual(ItemSnapshot[] original, ItemSnapshot[] current) {
        if (original.length != current.length) {
            return false;
        }

        for (int i = 0; i < original.length; i++) {
            ItemSnapshot orig = original[i];
            ItemSnapshot curr = current[i];

            // Both null is equal
            if (orig == null && curr == null) {
                continue;
            }

            // One null, one not - not equal
            if ((orig == null) != (curr == null)) {
                return false;
            }

            // Compare item stacks
            if (!orig.equals(curr)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Gets a hash of the item's DataComponents for comparison.
     * Uses custom name and enchantments as primary differentiators.
     */
    private static String getComponentsHash(ItemStack stack) {
        StringBuilder sb = new StringBuilder();

        // Custom name
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            Component name = stack.get(DataComponents.CUSTOM_NAME);
            if (name != null) {
                sb.append("name:").append(name.getString()).append(";");
            }
        }

        // Enchantments
        if (stack.has(DataComponents.ENCHANTMENTS)) {
            var enchants = stack.get(DataComponents.ENCHANTMENTS);
            if (enchants != null && !enchants.isEmpty()) {
                sb.append("enchants:").append(enchants.hashCode()).append(";");
            }
        }

        // Damage
        if (stack.has(DataComponents.DAMAGE)) {
            Integer damage = stack.get(DataComponents.DAMAGE);
            if (damage != null) {
                sb.append("damage:").append(damage).append(";");
            }
        }

        return sb.toString();
    }

    private static class ContainerSnapshot {
        final BaseContainerBlockEntity container;
        final ItemSnapshot[] contents;

        ContainerSnapshot(BaseContainerBlockEntity container, ItemSnapshot[] contents) {
            this.container = container;
            this.contents = contents;
        }
    }

    private static class ItemSnapshot {
        final String itemType;
        final int amount;
        final String nbt;

        ItemSnapshot(String itemType, int amount, String nbt) {
            this.itemType = itemType;
            this.amount = amount;
            this.nbt = nbt;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemSnapshot that)) return false;
            return amount == that.amount &&
                   itemType.equals(that.itemType) &&
                   nbt.equals(that.nbt);
        }

        @Override
        public int hashCode() {
            return itemType.hashCode() * 31 + amount;
        }
    }
}
