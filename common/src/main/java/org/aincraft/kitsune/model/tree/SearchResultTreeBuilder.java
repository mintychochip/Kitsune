package org.aincraft.kitsune.model.tree;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
import org.aincraft.kitsune.cache.ItemDataCache;
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

  private static final Gson GSON = new Gson();

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
    return buildTree(results, logger, itemLoader, null);
  }

  /**
   * Builds a tree structure from a flat list of search results, optionally loading live items.
   * Uses a cache for item data extraction to avoid redundant JSON parsing.
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
   * @param itemCache  optional cache for item data extraction (can be null)
   * @return list of LOCATION root nodes (one per unique location)
   * @throws NullPointerException if results is null
   */
  public static List<SearchResultTreeNode> buildTree(
      List<SearchResult> results,
      Logger logger,
      @Nullable ItemLoader itemLoader,
      @Nullable ItemDataCache itemCache) {
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
      SearchResultTreeNode locationNode = buildLocationNode(resultsAtLocation, logger, itemLoader, itemCache);
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
   * @param itemCache         optional cache for item data extraction (can be null)
   * @return a LOCATION node with children
   */
  private static SearchResultTreeNode buildLocationNode(
      List<SearchResult> resultsAtLocation,
      Logger logger,
      @Nullable ItemLoader itemLoader,
      @Nullable ItemDataCache itemCache) {
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
                  r.containerPath().containerRefs().get(0).slotIndex() == extractSlotIndex(result, itemCache));

          if (hasChildResults) {
            // Store container's score, don't add as item
            containerScores.put(extractSlotIndex(result, itemCache), (int) Math.round(result.score() * 100));
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
        ItemDataCache.ExtractedItemData itemData = itemCache != null
            ? itemCache.getOrExtract(result.fullContent(), logger)
            : extractItemDataLegacy(result.fullContent(), logger);

        int slotIndex = itemData.getSlotIndex();
        int amount = itemData.getAmount();
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

        ItemResultData itemResultData = ItemResultData.ofRootItem(
            itemData.getDisplayName(),
            slotIndex,
            amount,
            scorePercent,
            item);
        SearchResultTreeNode itemNode = SearchResultTreeNode.itemNode(itemResultData);
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
        ItemDataCache.ExtractedItemData itemData = itemCache != null
            ? itemCache.getOrExtract(result.fullContent(), logger)
            : extractItemDataLegacy(result.fullContent(), logger);

        int slotIndex = itemData.getSlotIndex();
        int amount = itemData.getAmount();
        Item item = null;

        // Load live item if loader is available
        if (itemLoader != null) {
          item = itemLoader.loadItem(loc, slotIndex, result.containerPath());
        }

        ItemResultData itemResultData = ItemResultData.ofNestedItem(
            itemData.getDisplayName(),
            slotIndex,
            amount,
            scorePercent,
            result.containerPath(),
            item);
        SearchResultTreeNode itemNode = SearchResultTreeNode.itemNode(itemResultData);
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
    return extractSlotIndex(result, null);
  }

  /**
   * Extracts slot index from search result's full content JSON, using cache if available.
   *
   * @param result the search result
   * @param itemCache optional cache for item data extraction
   * @return slot index extracted from JSON, or -1 if not found
   */
  private static int extractSlotIndex(SearchResult result, ItemDataCache itemCache) {
    if (itemCache != null) {
      ItemDataCache.ExtractedItemData itemData = itemCache.getOrExtract(result.fullContent(), null);
      return itemData.getSlotIndex();
    }
    return ItemDataExtractor.extractSlotIndex(result.fullContent(), null);
  }

  /**
   * Legacy item data extraction method for when cache is not available.
   */
  private static ItemDataCache.ExtractedItemData extractItemDataLegacy(String fullContent, Logger logger) {
    if (fullContent == null || fullContent.isEmpty()) {
      return new ItemDataCache.ExtractedItemData("Unknown Item", -1, 1, ContainerPath.ROOT);
    }

    try {
      JsonArray jsonArray = GSON.fromJson(fullContent, JsonArray.class);
      if (jsonArray != null && jsonArray.size() > 0) {
        JsonObject itemObj = jsonArray.get(0).getAsJsonObject();

        // Extract display name
        String displayName = extractDisplayName(itemObj);

        // Extract slot index
        int slotIndex = extractSlotIndex(itemObj);

        // Extract amount
        int amount = extractAmount(itemObj);

        // Extract container path
        ContainerPath containerPath = extractContainerPath(itemObj, logger);

        return new ItemDataCache.ExtractedItemData(displayName, slotIndex, amount, containerPath);
      }
    } catch (Exception e) {
      if (logger != null) {
        logger.warning("extractItemDataLegacy: failed to parse JSON: " + e.getMessage());
      }
    }

    return new ItemDataCache.ExtractedItemData("Unknown Item", -1, 1, ContainerPath.ROOT);
  }

  /**
   * Helper method to extract display name from JsonObject.
   */
  private static String extractDisplayName(JsonObject itemObj) {
    if (itemObj.has("display_name")) {
      String displayName = itemObj.get("display_name").getAsString();
      if (displayName != null && !displayName.isEmpty()) {
        return displayName;
      }
    }
    if (itemObj.has("name")) {
      String name = itemObj.get("name").getAsString();
      if (name != null && !name.isEmpty()) {
        return name;
      }
    }
    if (itemObj.has("material")) {
      String material = itemObj.get("material").getAsString();
      return formatMaterialName(material);
    }
    return "Unknown Item";
  }

  /**
   * Helper method to extract slot index from JsonObject.
   */
  private static int extractSlotIndex(JsonObject itemObj) {
    if (itemObj.has("slot")) {
      return itemObj.get("slot").getAsInt();
    }
    return -1;
  }

  /**
   * Helper method to extract amount from JsonObject.
   */
  private static int extractAmount(JsonObject itemObj) {
    if (itemObj.has("amount")) {
      int amount = itemObj.get("amount").getAsInt();
      return Math.max(amount, 1);
    }
    return 1;
  }

  /**
   * Helper method to extract container path from JsonObject.
   */
  private static ContainerPath extractContainerPath(JsonObject itemObj, Logger logger) {
    if (itemObj.has("container_path")) {
      var pathElement = itemObj.get("container_path");

      // Handle string format (legacy)
      if (pathElement.isJsonPrimitive() && pathElement.getAsJsonPrimitive().isString()) {
        String pathString = pathElement.getAsString();
        if (pathString.isEmpty()) {
          return ContainerPath.ROOT;
        }
        return parseLegacyContainerPath(pathString);
      }

      // Handle JSON array format
      String pathJson = pathElement.toString();
      try {
        return ContainerPath.fromJson(pathJson);
      } catch (Exception e) {
        if (logger != null) {
          logger.warning("extractContainerPath: failed to parse container path: " + e.getMessage());
        }
        return ContainerPath.ROOT;
      }
    }
    return ContainerPath.ROOT;
  }

  /**
   * Helper method to format material name.
   */
  private static String formatMaterialName(String material) {
    if (material == null) return "Unknown Item";

    // Convert DIAMOND_PICKAXE to Diamond Pickaxe
    String[] words = material.toLowerCase().split("_");
    StringBuilder sb = new StringBuilder();
    for (String word : words) {
      if (!word.isEmpty()) {
        if (sb.length() > 0) sb.append(" ");
        sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
      }
    }
    return sb.toString();
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
