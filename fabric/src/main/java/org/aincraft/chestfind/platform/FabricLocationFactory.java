package org.aincraft.chestfind.platform;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.aincraft.chestfind.api.LocationData;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for converting between LocationData and Minecraft's location types.
 */
public final class FabricLocationFactory {
    private FabricLocationFactory() {
    }

    /**
     * Convert a Minecraft ServerWorld and BlockPos to LocationData.
     *
     * @param world the server world
     * @param pos   the block position
     * @return LocationData representing this location
     */
    public static LocationData toLocationData(ServerWorld world, BlockPos pos) {
        String worldName = world.getRegistryKey().getValue().toString();
        return LocationData.of(worldName, pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Convert LocationData to a BlockPos.
     *
     * @param location the location data
     * @return a BlockPos for these coordinates
     */
    public static BlockPos toBlockPos(LocationData location) {
        return new BlockPos(location.blockX(), location.blockY(), location.blockZ());
    }

    /**
     * Get the center position of a block for rendering purposes.
     *
     * @param location the location data
     * @return Vec3d at the center of the block
     */
    public static Vec3d toBlockCenter(LocationData location) {
        return new Vec3d(
                location.blockX() + 0.5,
                location.blockY() + 0.5,
                location.blockZ() + 0.5
        );
    }

    /**
     * Get the ServerWorld from LocationData.
     *
     * @param server   the Minecraft server
     * @param location the location data
     * @return the ServerWorld, or null if the world is not loaded
     */
    @Nullable
    public static ServerWorld getServerWorld(MinecraftServer server, LocationData location) {
        Identifier worldId = Identifier.tryParse(location.worldName());
        if (worldId == null) {
            return null;
        }

        RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, worldId);
        return server.getWorld(worldKey);
    }

    /**
     * Get the dimension registry key from LocationData.
     *
     * @param location the location data
     * @return the registry key for this dimension, or null if invalid
     */
    @Nullable
    public static RegistryKey<World> getWorldKey(LocationData location) {
        Identifier worldId = Identifier.tryParse(location.worldName());
        if (worldId == null) {
            return null;
        }
        return RegistryKey.of(RegistryKeys.WORLD, worldId);
    }

    /**
     * Check if a location is within a certain radius of another location.
     *
     * @param center the center location
     * @param target the target location to check
     * @param radius the radius in blocks
     * @return true if target is within radius blocks of center
     */
    public static boolean isWithinRadius(LocationData center, LocationData target, int radius) {
        if (!center.worldName().equals(target.worldName())) {
            return false;
        }
        int dx = center.blockX() - target.blockX();
        int dy = center.blockY() - target.blockY();
        int dz = center.blockZ() - target.blockZ();
        return (dx * dx + dy * dy + dz * dz) <= (radius * radius);
    }

    /**
     * Calculate the distance between two locations.
     *
     * @param from the starting location
     * @param to   the ending location
     * @return the distance in blocks, or -1 if in different worlds
     */
    public static double distance(LocationData from, LocationData to) {
        if (!from.worldName().equals(to.worldName())) {
            return -1;
        }
        int dx = from.blockX() - to.blockX();
        int dy = from.blockY() - to.blockY();
        int dz = from.blockZ() - to.blockZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
