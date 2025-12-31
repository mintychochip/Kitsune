package org.aincraft.kitsune.indexing;

import org.aincraft.kitsune.api.Location;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Platform-specific interface for scanning containers in a radius.
 * Implementations handle platform-specific block/container lookups.
 */
public interface ContainerScanner {
    /**
     * Scans for containers within a radius and returns their locations with contents.
     *
     * @param centerLocation the center location to scan from
     * @param radius the radius to scan
     * @return a CompletableFuture with a list of ContainerScan results
     */
    CompletableFuture<List<ContainerScan>> scanRadius(Location centerLocation, int radius);

    /**
     * Represents a container found during scanning.
     */
    record ContainerScan(Location location, Object itemArray) {
    }
}
