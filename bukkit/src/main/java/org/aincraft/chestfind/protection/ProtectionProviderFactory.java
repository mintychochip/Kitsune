package org.aincraft.chestfind.protection;

import java.util.Optional;
import java.util.logging.Logger;
import org.aincraft.chestfind.config.ChestFindConfig;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Factory for creating ProtectionProvider instances.
 * Returns Optional.empty() when protection is disabled or not available.
 */
public final class ProtectionProviderFactory {
    private ProtectionProviderFactory() {
    }

    /**
     * Creates a ProtectionProvider based on configuration.
     *
     * @param config The plugin configuration
     * @param plugin The JavaPlugin instance
     * @param logger The logger for status messages
     * @return Optional containing the provider, or empty if protection is disabled
     */
    public static Optional<ProtectionProvider> create(
            ChestFindConfig config, JavaPlugin plugin, Logger logger) {
        if (!config.isProtectionEnabled()) {
            logger.info("Protection integration disabled");
            return Optional.empty();
        }

        String provider = config.getProtectionPlugin().toLowerCase();

        if ("auto".equals(provider)) {
            if (plugin.getServer().getPluginManager().getPlugin("Lockette") != null) {
                logger.info("Using Lockette protection integration");
                return Optional.of(new LocketteProtectionProvider());
            }
            if (plugin.getServer().getPluginManager().getPlugin("Bolt") != null) {
                logger.info("Using Bolt protection integration");
                return Optional.of(new BoltProtectionProvider());
            }
            logger.info("No protection plugin detected, protection checks disabled");
            return Optional.empty();
        }

        return switch (provider) {
            case "lockette" -> {
                logger.info("Using Lockette protection integration");
                yield Optional.of(new LocketteProtectionProvider());
            }
            case "bolt" -> {
                logger.info("Using Bolt protection integration");
                yield Optional.of(new BoltProtectionProvider());
            }
            case "none" -> {
                logger.info("Protection integration explicitly disabled");
                yield Optional.empty();
            }
            default -> {
                logger.warning("Unknown protection provider: " + provider + ", disabling protection");
                yield Optional.empty();
            }
        };
    }

    private static class LocketteProtectionProvider extends BukkitProtectionProvider {
        @Override
        protected boolean canAccessBukkit(Player player, Location location) {
            try {
                Class<?> locketteClass = Class.forName("org.yi.acru.bukkit.Lockette.Lockette");
                var method = locketteClass.getMethod("canAccessContainer", Player.class, Location.class);
                return (Boolean) method.invoke(null, player, location);
            } catch (Exception e) {
                return true;
            }
        }
    }

    private static class BoltProtectionProvider extends BukkitProtectionProvider {
        @Override
        protected boolean canAccessBukkit(Player player, Location location) {
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
