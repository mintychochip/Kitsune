package org.aincraft.kitsune.protection;

import java.util.UUID;
import org.aincraft.kitsune.api.Location;

public class NoOpProtectionProvider implements ProtectionProvider {
    @Override
    public boolean canAccess(UUID playerId, Location location) {
        return true;
    }
}
