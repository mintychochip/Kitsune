package org.aincraft.kitsune.model.tree;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import org.aincraft.kitsune.Block;
import org.aincraft.kitsune.Inventory;
import org.aincraft.kitsune.Location;
import org.aincraft.kitsune.api.indexing.ItemLoader;
import org.aincraft.kitsune.api.model.ContainerPath;
import org.aincraft.kitsune.Item;
import org.aincraft.kitsune.api.model.NestedContainerRef;
import org.aincraft.kitsune.model.SearchResult;
import org.aincraft.kitsune.util.ItemDataExtractor;
import org.jetbrains.annotations.Nullable;

/**
 * Platform-agnostic builder for hierarchical tree structures from flat search result data.
 *
 * <p>Organizes results by location, with nested containers properly ordered within each location.
 *
 * <p>Example output structure:
 * <pre>
 * Location: World 100, 64, 200
 *   ├─ Diamond
 *   ├─ Gold
 *   └─ Red Shulker Box (slot 5)
 *       ├─ Emerald
 *       └─ Iron Ingot
 * </pre>
 */
public final class SearchResultTreeBuilder {

  private SearchResultTreeBuilder() {
    // Utility class - no instantiation
  }

  /**
   * Builds a tree structure from a flat list of search results.
   *
   * <p>Algorithm:
   * 1. Group results by location key (world:x,y,z) 2. For each location group, create a location
   * node 3. For each result in the group: - If containerPath is null or root -> add item directly
   * to location node - Else -> walk through containerPath and create/find container nodes - Add
   * item as child of deepest container
   *
   * @param results flat list of search results
   * @param logger  logger for warnings during item extraction (can be null)
   * @return list of LOCATION root nodes (one per unique location)
   * @throws NullPointerException if results is null
   */
  public static List<SearchResultTreeNode> buildTree(List<SearchResult> results, Logger logger) {
    return buildTree(results, logger, null);
  }

  /**
   * Builds a tree structure from a flat list of search results, optionally loading live items.
   *
   * <p>Algorithm:
   * 1. Group results by location key (world:x,y,z) 2. For each location group, create a location
   * node 3. For each result in the group: - If containerPath is null or root -> add item directly
   * to location node - Else -> walk through containerPath and create/find container nodes - Add
   * item as child of deepest container
   *
   * @param results    flat list of search results
   * @param logger     logger for warnings during item extraction (can be null)
   * @param itemLoader optional loader for loading live items (can be null)
   * @return list of LOCATION root nodes (one per unique location)
   * @throws NullPointerException if results is null
   */
  public static List<SearchResultTreeNode> buildTree(
      List<SearchResult> results,
      Logger logger,
      @Nullable ItemLoader itemLoader) {
    Preconditions.checkNotNull(results, "Results list cannot be null");

    // Group results by location (preserves insertion order)
    LinkedHashMap<String, List<SearchResult>> locationGroups = new LinkedHashMap<>();

    for (SearchResult result : results) {
      String locationKey = buildLocationKey(result.location());
      locationGroups.computeIfAbsent(locationKey, k -> new ArrayList<>()).add(result);
    }

    // Build location nodes with their children
    List<SearchResultTreeNode> locationNodes = new ArrayList<>();
    for (List<SearchResult> resultsAtLocation : locationGroups.values()) {
      SearchResultTreeNode locationNode = buildLocationNode(resultsAtLocation, logger, itemLoader);
      locationNodes.add(locationNode);
    }

    return locationNodes;
  }

  /**
   * Builds a location key in format "world:x,y,z".
   *
   * @param location the location
   * @return location key string
   */
  private static String buildLocationKey(Location location) {
    return String.format(
        "%s:%d,%d,%d",
        location.getWorld().getName(),
        location.blockX(),
        location.blockY(),
        location.blockZ());
  }

  /**
   * Builds a location node with all results at that location as children.
   *
   * <p>For each result, checks containerPath:
   * - If null or root: add as direct child of location - Else: walk path, building container nodes
   * and deduplicating via cache
   *
   * @param resultsAtLocation results all at same location
   * @param logger            logger for warnings during item extraction (can be null)
   * @param itemLoader        optional loader for loading live items (can be null)
   * @return a LOCATION node with children
   */
  private static SearchResultTreeNode buildLocationNode(
      List<SearchResult> resultsAtLocation,
      Logger logger,
      @Nullable ItemLoader itemLoader) {
    Preconditions.checkArgument(
        !resultsAtLocation.isEmpty(),
        "Results at location cannot be empty");

    // Use first result to get location info
    SearchResult firstResult = resultsAtLocation.get(0);
    Location loc = firstResult.location();

    // Get block type from location
    String containerType = loc.getBlock() != null ? loc.getBlock().getType() : null;

    SearchResultTreeNode locationNode = SearchResultTreeNode.locationNode(loc, containerType);

    // Pre-process: find containers that are also search results
    // Map: slot index -> score percent
    Map<Integer, Integer> containerScores = new HashMap<>();
    List<SearchResult> itemsToProcess = new ArrayList<>();

    for (SearchResult result : resultsAtLocation) {
      ContainerPath containerPath = result.containerPath();
      if (containerPath == null || containerPath.isRoot()) {
        // Root-level item - check if it's a container type
        if (isContainerType(result.preview())) {
          // Check if any other result has this container in its path
          boolean hasChildResults = resultsAtLocation.stream()
              .anyMatch(r -> r.containerPath() != null &&
                  !r.containerPath().isRoot() &&
                  !r.containerPath().containerRefs().isEmpty() &&
                  r.containerPath().containerRefs().get(0).slotIndex() == extractSlotIndex(result));

          if (hasChildResults) {
            // Store container's score, don't add as item
            containerScores.put(extractSlotIndex(result), (int) Math.round(result.score() * 100));
            continue;
          }
        }
        // Not a container with children, add as regular item
        itemsToProcess.add(result);
      } else {
        // Nested item
        itemsToProcess.add(result);
      }
    }

    // Cache of container nodes by path key to avoid duplicates
    // Key: cache key for container path built so far
    // Value: the container node to use as parent for next items
    Map<String, SearchResultTreeNode> containerCache = new HashMap<>();

    // Process each result
    for (SearchResult result : itemsToProcess) {
      ContainerPath containerPath = result.containerPath();
      int scorePercent = (int) Math.round(result.score() * 100);

      // If no container path or root path, add item directly to location
      if (containerPath == null || containerPath.isRoot()) {
        int slotIndex = ItemDataExtractor.extractSlotIndex(result.fullContent(), logger);
        int amount = ItemDataExtractor.extractAmount(result.fullContent(), logger);
        Item item = null;

        Block block = loc.getBlock();
        if (!block.isAir()) {
          Optional<Inventory> inventory = block.getInventory();
          if (inventory.isPresent()) {
            Inventory platformInventory = inventory.get();
            item = platformInventory.getItem(slotIndex);
          }
        }

        // Load live item if loader is available
        if (itemLoader != null) {
            item = itemLoader.loadItem(loc, slotIndex, containerPath);
        }

        ItemResultData itemData = ItemResultData.ofRootItem(
            ItemDataExtractor.extractDisplayName(result.fullContent(), logger),
            slotIndex,
            amount,
            scorePercent,
            item);
        SearchResultTreeNode itemNode = SearchResultTreeNode.itemNode(itemData);
        locationNode.addChild(itemNode);
      } else {
        // Walk through container path, building nodes and caching
        SearchResultTreeNode parentNode = locationNode;

        // Build nodes for each container in the path
        List<NestedContainerRef> containerRefs = containerPath.containerRefs();
        for (int i = 0; i < containerRefs.size(); i++) {
          NestedContainerRef ref = containerRefs.get(i);

          // Build cache key for path up to this point
          String cacheKey = buildCacheKey(containerRefs, i);

          // Check if we've already created this container node
          SearchResultTreeNode containerNode = containerCache.get(cacheKey);

          if (containerNode == null) {
            // Check if this container has a score (is also a search result)
            Integer score = (i == 0) ? containerScores.get(ref.slotIndex()) : null;

            // Create new container node (with or without score)
            if (score != null) {
              containerNode = SearchResultTreeNode.containerNodeWithScore(ref, score);
            } else {
              containerNode = SearchResultTreeNode.containerNode(ref);
            }
            containerCache.put(cacheKey, containerNode);

            // Add to parent (location or previous container)
            parentNode.addChild(containerNode);
          }

          // Move parent pointer for next iteration
          parentNode = containerNode;
        }

        // Add item as child of deepest container
        int slotIndex = ItemDataExtractor.extractSlotIndex(result.fullContent(), logger);
        int amount = ItemDataExtractor.extractAmount(result.fullContent(), logger);
        Item item = null;

        // Load live item if loader is available
        if (itemLoader != null) {
          item = itemLoader.loadItem(loc, slotIndex, result.containerPath());
        }

        ItemResultData itemData = ItemResultData.ofNestedItem(
            ItemDataExtractor.extractDisplayName(result.fullContent(), logger),
            slotIndex,
            amount,
            scorePercent,
            result.containerPath(),
            item);
        SearchResultTreeNode itemNode = SearchResultTreeNode.itemNode(itemData);
        parentNode.addChild(itemNode);
      }
    }

    return locationNode;
  }

  /**
   * Check if an item display name indicates it's a container type.
   */
  private static boolean isContainerType(String displayName) {
      if (displayName == null) {
          return false;
      }
    String lower = displayName.toLowerCase();
    return lower.contains("shulker") || lower.contains("bundle");
  }

  /**
   * Extracts slot index from search result's full content JSON.
   *
   * @param result the search result
   * @return slot index extracted from JSON, or -1 if not found
   */
  private static int extractSlotIndex(SearchResult result) {
    return ItemDataExtractor.extractSlotIndex(result.fullContent(), null);
  }

  /**
   * Builds a cache key for the container path up to index (inclusive).
   *
   * <p>Key format: "containerType1|slot1,containerType2|slot2,..."
   * This uniquely identifies a specific nested container path.
   *
   * @param containerRefs list of container refs
   * @param upToIndex     index of last ref to include (inclusive)
   * @return cache key string
   */
  private static String buildCacheKey(List<NestedContainerRef> containerRefs, int upToIndex) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i <= upToIndex; i++) {
      if (i > 0) {
        sb.append(",");
      }
      NestedContainerRef ref = containerRefs.get(i);
      sb.append(ref.containerType()).append("|").append(ref.slotIndex());
    }
    return sb.toString();
  }
}
