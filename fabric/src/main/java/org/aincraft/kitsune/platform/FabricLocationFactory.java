package org.aincraft.kitsune.platform;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.aincraft.kitsune.api.Location;
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
    public static Location toLocationData(ServerWorld world, BlockPos pos) {
        String worldName = world.getRegistryKey().getValue().toString();
        return Location.of(worldName, pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Create LocationData from raw values.
     *
     * @param worldName the world name
     * @param x         the x coordinate
     * @param y         the y coordinate
     * @param z         the z coordinate
     * @return LocationData
     */
    public static Location toLocationData(String worldName, int x, int y, int z) {
        return Location.of(worldName, x, y, z);
    }

    /**
     * Convert LocationData to a BlockPos.
     *
     * @param location the location data
     * @return a BlockPos for these coordinates
     */
    public static BlockPos toBlockPos(Location location) {
        return new BlockPos(location.blockX(), location.blockY(), location.blockZ());
    }

    /**
     * Get the center position of a block for rendering purposes.
     *
     * @param location the location data
     * @return Vec3d at the center of the block
     */
    public static Vec3d toBlockCenter(Location location) {
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
    public static ServerWorld getServerWorld(MinecraftServer server, Location location) {
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
    public static RegistryKey<World> getWorldKey(Location location) {
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
    public static boolean isWithinRadius(Location center, Location target, int radius) {
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
    public static double distance(Location from, Location to) {
        if (!from.worldName().equals(to.worldName())) {
            return -1;
        }
        int dx = from.blockX() - to.blockX();
        int dy = from.blockY() - to.blockY();
        int dz = from.blockZ() - to.blockZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
