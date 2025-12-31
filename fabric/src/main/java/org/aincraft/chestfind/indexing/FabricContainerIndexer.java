package org.aincraft.chestfind.indexing;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.aincraft.chestfind.ChestFindMod;
import org.aincraft.chestfind.api.ItemTagProviderRegistry;
import org.aincraft.chestfind.config.ChestFindConfig;
import org.aincraft.chestfind.embedding.EmbeddingService;
import org.aincraft.chestfind.model.ContainerChunk;
import org.aincraft.chestfind.platform.FabricLocationFactory;
import org.aincraft.chestfind.storage.VectorStorage;
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

/**
 * Fabric implementation of container indexing with debouncing.
 */
public class FabricContainerIndexer {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChestFind");

    private final ChestFindMod mod;
    private final EmbeddingService embeddingService;
    private final VectorStorage vectorStorage;
    private final ChestFindConfig config;
    private final FabricItemSerializer itemSerializer;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private final Map<BlockPos, ScheduledFuture<?>> pendingIndexes = new HashMap<>();
    private final int debounceDelayMs;

    public FabricContainerIndexer(ChestFindMod mod, EmbeddingService embeddingService,
                                  VectorStorage vectorStorage, ChestFindConfig config,
                                  ItemTagProviderRegistry tagRegistry) {
        this.mod = mod;
        this.embeddingService = embeddingService;
        this.vectorStorage = vectorStorage;
        this.config = config;
        this.itemSerializer = new FabricItemSerializer(tagRegistry);
        this.debounceDelayMs = config.getDebounceDelayMs();
    }

    /**
     * Schedule indexing of a container at the given position.
     */
    public void scheduleIndex(ServerWorld world, BlockPos pos, ItemStack[] items) {
        synchronized (pendingIndexes) {
            ScheduledFuture<?> existing = pendingIndexes.get(pos);
            if (existing != null && !existing.isDone()) {
                existing.cancel(false);
            }

            ScheduledFuture<?> future = executor.schedule(
                    () -> performIndex(world, pos, items),
                    debounceDelayMs,
                    TimeUnit.MILLISECONDS
            );

            pendingIndexes.put(pos, future);
        }
    }

    /**
     * Schedule indexing of a container inventory.
     */
    public void scheduleIndex(ServerWorld world, BlockPos pos, Inventory inventory) {
        ItemStack[] items = new ItemStack[inventory.size()];
        for (int i = 0; i < inventory.size(); i++) {
            items[i] = inventory.getStack(i).copy();
        }
        scheduleIndex(world, pos, items);
    }

    private void performIndex(ServerWorld world, BlockPos pos, ItemStack[] items) {
        var locationData = FabricLocationFactory.toLocationData(world, pos);
        List<SerializedItem> serializedItems = itemSerializer.serializeItemsToChunks(items);

        if (serializedItems.isEmpty()) {
            vectorStorage.deleteByLocation(locationData).exceptionally(ex -> {
                LOGGER.warn("Failed to delete empty container at {}", pos, ex);
                return null;
            });
            return;
        }

        // Create embedding for each chunk
        long timestamp = System.currentTimeMillis();
        List<CompletableFuture<ContainerChunk>> chunkFutures = new ArrayList<>();

        for (int i = 0; i < serializedItems.size(); i++) {
            final int chunkIndex = i;
            final SerializedItem serialized = serializedItems.get(i);

            // Normalize to lowercase for better embedding consistency
            String embeddingText = serialized.embeddingText().toLowerCase();

            LOGGER.debug("Indexing chunk {} at {}: embedding=\"{}\"", chunkIndex, pos, embeddingText);

            CompletableFuture<ContainerChunk> chunkFuture = embeddingService.embed(embeddingText, "RETRIEVAL_DOCUMENT")
                    .thenApply(embedding -> new ContainerChunk(
                            locationData,
                            chunkIndex,
                            embeddingText,
                            embedding,
                            timestamp
                    ));

            chunkFutures.add(chunkFuture);
        }

        // Wait for all chunks to be embedded, then index them
        CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<ContainerChunk> chunks = new ArrayList<>();
                    for (CompletableFuture<ContainerChunk> future : chunkFutures) {
                        try {
                            chunks.add(future.get());
                        } catch (Exception e) {
                            LOGGER.warn("Failed to get chunk embedding", e);
                        }
                    }
                    return chunks;
                })
                .thenCompose(chunks -> vectorStorage.indexChunks(chunks))
                .thenRun(() -> {
                    synchronized (pendingIndexes) {
                        pendingIndexes.remove(pos);
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.warn("Failed to index container at {}", pos, ex);
                    return null;
                });
    }

    /**
     * Reindex all containers within a radius of the center position.
     */
    public CompletableFuture<Integer> reindexRadius(ServerWorld world, BlockPos center, int radius) {
        return CompletableFuture.supplyAsync(() -> {
            List<ScheduledFuture<?>> scheduledTasks = new ArrayList<>();

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

                                synchronized (pendingIndexes) {
                                    ScheduledFuture<?> existing = pendingIndexes.get(pos);
                                    if (existing != null && !existing.isDone()) {
                                        existing.cancel(false);
                                    }

                                    ScheduledFuture<?> future = executor.schedule(
                                            () -> performIndex(world, pos, items),
                                            debounceDelayMs,
                                            TimeUnit.MILLISECONDS
                                    );

                                    pendingIndexes.put(pos, future);
                                    scheduledTasks.add(future);
                                }
                            }
                        }
                    }
                }
            }

            return scheduledTasks;
        }, executor).thenCompose(scheduledTasks -> {
            CompletableFuture<?>[] taskFutures = scheduledTasks.stream()
                    .map(this::toCompletableFuture)
                    .toArray(CompletableFuture[]::new);

            return CompletableFuture.allOf(taskFutures)
                    .thenApply(v -> scheduledTasks.size());
        });
    }

    private CompletableFuture<Void> toCompletableFuture(ScheduledFuture<?> scheduledFuture) {
        CompletableFuture<Void> cf = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                scheduledFuture.get();
                cf.complete(null);
            } catch (Exception e) {
                cf.completeExceptionally(e);
            }
        });
        return cf;
    }

    public void shutdown() {
        synchronized (pendingIndexes) {
            for (ScheduledFuture<?> future : pendingIndexes.values()) {
                if (future != null && !future.isDone()) {
                    future.cancel(false);
                }
            }
            pendingIndexes.clear();
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
