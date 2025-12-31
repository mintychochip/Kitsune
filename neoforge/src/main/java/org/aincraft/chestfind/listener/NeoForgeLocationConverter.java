package org.aincraft.chestfind.listener;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.aincraft.chestfind.api.LocationData;

/**
 * Utility class for converting NeoForge block positions to platform-agnostic LocationData.
 */
public final class NeoForgeLocationConverter {

    private NeoForgeLocationConverter() {
        // Utility class - no instantiation
    }

    /**
     * Converts a BlockEntity to LocationData.
     *
     * @param blockEntity the NeoForge BlockEntity
     * @param level the ServerLevel containing the block entity
     * @return LocationData representing the block entity's position
     * @throws IllegalArgumentException if blockEntity or level is null
     */
    public static LocationData toLocationData(BlockEntity blockEntity, ServerLevel level) {
        if (blockEntity == null) {
            throw new IllegalArgumentException("BlockEntity cannot be null");
        }
        if (level == null) {
            throw new IllegalArgumentException("ServerLevel cannot be null");
        }

        String worldName = level.dimension().location().toString();
        int x = blockEntity.getBlockPos().getX();
        int y = blockEntity.getBlockPos().getY();
        int z = blockEntity.getBlockPos().getZ();

        return LocationData.of(worldName, x, y, z);
    }
}
