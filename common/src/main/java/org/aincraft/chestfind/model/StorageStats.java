package org.aincraft.chestfind.model;

public record StorageStats(
    long containerCount,
    String providerName
) {
    public StorageStats {
        if (providerName == null) {
            throw new IllegalArgumentException("Provider name cannot be null");
        }
    }
}
