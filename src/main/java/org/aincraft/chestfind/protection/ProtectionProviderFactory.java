package org.aincraft.chestfind.protection;

import org.aincraft.chestfind.config.ChestFindConfig;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class ProtectionProviderFactory {
    private ProtectionProviderFactory() {
    }

    public static ProtectionProvider create(ChestFindConfig config, JavaPlugin plugin) {
        if (!config.isProtectionEnabled()) {
            return new NoOpProtectionProvider();
        }

        String provider = config.getProtectionPlugin().toLowerCase();

        if ("auto".equals(provider)) {
            if (plugin.getServer().getPluginManager().getPlugin("Lockette") != null) {
                plugin.getLogger().info("Using Lockette protection integration");
                return new LocketteProtectionProvider();
            }
            if (plugin.getServer().getPluginManager().getPlugin("Bolt") != null) {
                plugin.getLogger().info("Using Bolt protection integration");
                return new BoltProtectionProvider();
            }
            plugin.getLogger().info("No protection plugin detected, allowing all access");
            return new NoOpProtectionProvider();
        }

        return switch (provider) {
            case "lockette" -> new LocketteProtectionProvider();
            case "bolt" -> new BoltProtectionProvider();
            case "none" -> new NoOpProtectionProvider();
            default -> {
                plugin.getLogger().warning("Unknown protection provider: " + provider + ", using no protection");
                yield new NoOpProtectionProvider();
            }
        };
    }

    private static class LocketteProtectionProvider implements ProtectionProvider {
        @Override
        public boolean canAccess(Player player, Location location) {
            try {
                Class<?> locketteClass = Class.forName("org.yi.acru.bukkit.Lockette.Lockette");
                var method = locketteClass.getMethod("canAccessContainer", Player.class, Location.class);
                return (Boolean) method.invoke(null, player, location);
            } catch (Exception e) {
                return true;
            }
        }
    }

    private static class BoltProtectionProvider implements ProtectionProvider {
        @Override
        public boolean canAccess(Player player, Location location) {
            try {
                Class<?> boltClass = Class.forName("nl.rutgerdevries.bolt.api.Bolt");
                var method = boltClass.getMethod("canAccess", Player.class, Location.class);
                return (Boolean) method.invoke(null, player, location);
            } catch (Exception e) {
                return true;
            }
        }
    }
}
