package org.aincraft.chestfind.util;

import com.google.common.base.Preconditions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.aincraft.chestfind.api.LocationData;

/**
 * Utility for converting NeoForge location types to platform-agnostic LocationData.
 */
public class LocationConverter {
    private LocationConverter() {
    }

    /**
     * Convert a NeoForge BlockPos and Level to LocationData.
     *
     * @param pos the block position
     * @param level the level/world
     * @return LocationData representing the same location
     * @throws NullPointerException if pos or level is null
     */
    public static LocationData toLocationData(BlockPos pos, Level level) {
        Preconditions.checkNotNull(pos, "BlockPos cannot be null");
        Preconditions.checkNotNull(level, "Level cannot be null");

        String worldName = level.dimension().location().toString();
        return LocationData.of(worldName, pos.getX(), pos.getY(), pos.getZ());
    }
}
