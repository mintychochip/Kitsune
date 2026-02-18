package org.aincraft.kitsune.model;

import com.google.common.base.Preconditions;
import java.util.UUID;

/**
 * Represents a single search history entry, recording a player's search query and its results.
 */
public record SearchHistoryEntry(
    UUID id,
    UUID playerId,
    String playerName,
    String query,
    int resultCount,
    long timestamp
) {
    public SearchHistoryEntry {
        Preconditions.checkNotNull(id, "ID cannot be null");
        Preconditions.checkNotNull(playerId, "Player ID cannot be null");
        Preconditions.checkNotNull(playerName, "Player name cannot be null");
        Preconditions.checkNotNull(query, "Query cannot be null");
        Preconditions.checkArgument(resultCount >= 0, "Result count must be non-negative");
        Preconditions.checkArgument(timestamp > 0, "Timestamp must be positive");
    }

    /**
     * Creates a new SearchHistoryEntry with a random UUID and current timestamp.
     *
     * @param playerId the player who performed the search
     * @param playerName the player's name for display
     * @param query the search query text
     * @param resultCount the number of results found
     * @return a new SearchHistoryEntry
     */
    public static SearchHistoryEntry of(UUID playerId, String playerName, String query, int resultCount) {
        return new SearchHistoryEntry(
            UUID.randomUUID(),
            playerId,
            playerName,
            query,
            resultCount,
            System.currentTimeMillis()
        );
    }
}
