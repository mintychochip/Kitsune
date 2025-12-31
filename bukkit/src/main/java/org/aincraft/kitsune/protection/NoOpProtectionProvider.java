package org.aincraft.kitsune.protection;

import java.util.UUID;
import org.aincraft.kitsune.api.LocationData;

public class NoOpProtectionProvider implements ProtectionProvider {
    @Override
    public boolean canAccess(UUID playerId, LocationData location) {
        return true;
    }
}
