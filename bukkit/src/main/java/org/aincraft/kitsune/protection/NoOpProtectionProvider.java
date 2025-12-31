package org.aincraft.kitsune.protection;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public class NoOpProtectionProvider implements ProtectionProvider {
    @Override
    public boolean canAccess(Player player, Location location) {
        return true;
    }
}
