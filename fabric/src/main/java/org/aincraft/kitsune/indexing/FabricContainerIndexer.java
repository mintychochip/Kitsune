package org.aincraft.kitsune.indexing;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.aincraft.kitsune.KitsuneMod;
import org.aincraft.kitsune.api.ContainerLocations;
import org.aincraft.kitsune.api.ItemTagProviderRegistry;
import org.aincraft.kitsune.config.KitsuneConfig;
import org.aincraft.kitsune.embedding.EmbeddingService;
import org.aincraft.kitsune.platform.FabricLocationFactory;
import org.aincraft.kitsune.storage.VectorStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Fabric implementation of container indexing with debouncing.
 * Extends the common ContainerIndexer for platform-agnostic core logic.
 */
public class FabricContainerIndexer extends ContainerIndexer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Kitsune");

    private final KitsuneMod mod;
    private final FabricItemSerializer itemSerializer;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private final Map<BlockPos, ScheduledFuture<?>> pendingIndexes = new HashMap<>();

    public FabricContainerIndexer(KitsuneMod mod, EmbeddingService embeddingService,
                                  VectorStorage vectorStorage, KitsuneConfig config,
                                  ItemTagProviderRegistry tagRegistry) {
        super(convertLogger(), embeddingService, vectorStorage, config);
        this.mod = mod;
        this.itemSerializer = new FabricItemSerializer(tagRegistry);
    }

    private static java.util.logging.Logger convertLogger() {
        return java.util.logging.Logger.getLogger("Kitsune");
    }

    /**
     * Fabric-specific wrapper for scheduling index by world and position.
     * Converts Fabric world/position to platform-agnostic ContainerLocations and serializes items.
     *
     * @param world the Fabric world
     * @param pos the block position
     * @param items the items to index
     */
    public void scheduleIndex(ServerWorld world, BlockPos pos, ItemStack[] items) {
        var locationData = FabricLocationFactory.toLocationData(world, pos);
        List<SerializedItem> serializedItems = itemSerializer.serializeItemsToChunks(items);

        // Call the parent scheduleIndex with container locations
        scheduleIndex(ContainerLocations.single(locationData), serializedItems);
    }

    /**
     * Schedule indexing of a container inventory.
     *
     * @param world the Fabric world
     * @param pos the block position
     * @param inventory the inventory to index
     */
    public void scheduleIndex(ServerWorld world, BlockPos pos, Inventory inventory) {
        ItemStack[] items = new ItemStack[inventory.size()];
        for (int i = 0; i < inventory.size(); i++) {
            items[i] = inventory.getStack(i).copy();
        }
        scheduleIndex(world, pos, items);
    }

    /**
     * Reindex all containers within a radius of the center position.
     *
     * @param world the Fabric world
     * @param center the center block position
     * @param radius the scan radius
     * @return CompletableFuture with count of reindexed containers
     */
    public CompletableFuture<Integer> reindexRadius(ServerWorld world, BlockPos center, int radius) {
        return CompletableFuture.supplyAsync(() -> {
            List<CompletableFuture<?>> scheduledTasks = new ArrayList<>();
            int count = 0;

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        BlockPos pos = center.add(dx, dy, dz);
                        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                        if (distance <= radius) {
                            BlockEntity blockEntity = world.getBlockEntity(pos);
                            if (blockEntity instanceof Inventory inventory) {
                                ItemStack[] items = new ItemStack[inventory.size()];
                                for (int i = 0; i < inventory.size(); i++) {
                                    items[i] = inventory.getStack(i).copy();
                                }

                                scheduleIndex(world, pos, items);
                                count++;
                            }
                        }
                    }
                }
            }

            return count;
        }, executor).thenApply(count -> count);
    }

    @Override
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        super.shutdown();
    }
}
