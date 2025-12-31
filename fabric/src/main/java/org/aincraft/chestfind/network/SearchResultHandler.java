package org.aincraft.chestfind.network;

import net.minecraft.client.MinecraftClient;
import org.aincraft.chestfind.client.render.ContainerHighlightRenderer;
import org.aincraft.chestfind.client.screen.SearchResultsScreen;
import org.aincraft.chestfind.model.SearchResult;
import org.aincraft.chestfind.platform.FabricLocationFactory;

import java.util.List;

/**
 * Handles search results on the client side.
 * Opens results screen and adds container highlights.
 */
public class SearchResultHandler {
    private static List<SearchResult> lastResults = List.of();

    private SearchResultHandler() {
    }

    /**
     * Handle search results from the server.
     */
    public static void handleResults(List<SearchResult> results) {
        lastResults = results;

        MinecraftClient client = MinecraftClient.getInstance();

        // Add highlights for each result
        for (SearchResult result : results) {
            var pos = FabricLocationFactory.toBlockPos(result.location());
            int color = getColorForScore(result.score());
            ContainerHighlightRenderer.addHighlight(pos, color, 10000); // 10 seconds
        }

        // Open results screen
        client.execute(() -> {
            client.setScreen(new SearchResultsScreen(results));
        });
    }

    /**
     * Get highlight color based on similarity score.
     */
    private static int getColorForScore(float score) {
        if (score >= 0.9f) {
            return 0x00FF00; // Bright green
        } else if (score >= 0.8f) {
            return 0x7FFF00; // Yellow-green
        } else if (score >= 0.7f) {
            return 0xFFD700; // Gold
        } else {
            return 0xFFA500; // Orange
        }
    }

    public static List<SearchResult> getLastResults() {
        return lastResults;
    }

    public static void clearResults() {
        lastResults = List.of();
        ContainerHighlightRenderer.clearHighlights();
    }
}
