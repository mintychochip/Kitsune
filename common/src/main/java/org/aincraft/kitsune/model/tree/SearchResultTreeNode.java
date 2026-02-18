package org.aincraft.kitsune.model.tree;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aincraft.kitsune.Location;
import org.aincraft.kitsune.api.model.NestedContainerRef;
import org.jetbrains.annotations.Nullable;

/**
 * Platform-agnostic tree node data structure for organizing search results hierarchically.
 * Supports three node types: LOCATION (root), CONTAINER (intermediate), and ITEM (leaf).
 * This implementation has no platform-specific dependencies (no Bukkit, etc.).
 *
 * <p>Example hierarchy:
 * <pre>
 * LOCATION: "World: 100, 64, 200"
 *   ├─ ITEM: Diamond
 *   ├─ ITEM: Gold
 *   └─ CONTAINER: Red Shulker Box (slot 5)
 *       ├─ ITEM: Emerald
 *       └─ ITEM: Iron Ingot
 * </pre>
 */
public final class SearchResultTreeNode {

    /**
     * Enum representing the type of node in the search result tree.
     */
    public enum NodeType {
        /** Root node representing a chest location (world + coords) */
        LOCATION,
        /** Intermediate node for shulker boxes, bundles, or other containers */
        CONTAINER,
        /** Leaf node for actual search result items */
        ITEM
    }

    private final NodeType type;
    private final String displayName;
    private final List<SearchResultTreeNode> children;

    // LOCATION-specific fields
    @Nullable
    private final Location location;
    @Nullable
    private final String containerType;  // Block type for root container (e.g., "CHEST", "BARREL")

    // CONTAINER-specific fields
    @Nullable
    private final NestedContainerRef containerRef;
    @Nullable
    private final Integer containerScorePercent;  // Score if container itself is a search result

    // ITEM-specific fields
    @Nullable
    private final ItemResultData itemData;

    /**
     * Constructor for creating a node with all possible fields.
     * Most fields will be null depending on the node type.
     */
    private SearchResultTreeNode(
            NodeType type,
            String displayName,
            @Nullable Location location,
            @Nullable String containerType,
            @Nullable NestedContainerRef containerRef,
            @Nullable Integer containerScorePercent,
            @Nullable ItemResultData itemData) {
        this.type = Preconditions.checkNotNull(type, "Node type cannot be null");
        this.displayName = Preconditions.checkNotNull(displayName, "Display name cannot be null");
        this.children = new ArrayList<>();
        this.location = location;
        this.containerType = containerType;
        this.containerRef = containerRef;
        this.containerScorePercent = containerScorePercent;
        this.itemData = itemData;

        // Validate that required fields are present for each type
        switch (type) {
            case LOCATION:
                Preconditions.checkArgument(location != null, "LOCATION node requires location");
                Preconditions.checkArgument(
                        containerRef == null && containerScorePercent == null && itemData == null,
                        "LOCATION node must not have containerRef, score, or itemData");
                // containerType is optional for LOCATION nodes
                break;
            case CONTAINER:
                Preconditions.checkArgument(containerRef != null, "CONTAINER node requires containerRef");
                Preconditions.checkArgument(
                        location == null && containerType == null && itemData == null,
                        "CONTAINER node must not have location, containerType, or item data");
                // containerScorePercent is optional for CONTAINER nodes
                break;
            case ITEM:
                Preconditions.checkArgument(itemData != null, "ITEM node requires itemData");
                Preconditions.checkArgument(
                        location == null && containerType == null &&
                        containerRef == null && containerScorePercent == null,
                        "ITEM node must not have location, containerType, or container data");
                break;
        }
    }

    /**
     * Factory method to create a LOCATION node representing a chest location.
     *
     * @param location the platform-agnostic Location object
     * @return a new LOCATION node
     */
    public static SearchResultTreeNode locationNode(Location location) {
        Preconditions.checkNotNull(location, "Location cannot be null");
        String displayName = String.format("World: %s", location.asCoordinates());
        return new SearchResultTreeNode(
                NodeType.LOCATION,
                displayName,
                location,
                null,
                null,
                null,
                null);
    }

    /**
     * Factory method to create a LOCATION node with a specific container type.
     *
     * @param location the platform-agnostic Location object
     * @param containerType the block type (e.g., "CHEST", "BARREL", "TRAPPED_CHEST")
     * @return a new LOCATION node with container type
     */
    public static SearchResultTreeNode locationNode(Location location, @Nullable String containerType) {
        Preconditions.checkNotNull(location, "Location cannot be null");
        String displayName = String.format("World: %s", location.asCoordinates());
        return new SearchResultTreeNode(
                NodeType.LOCATION,
                displayName,
                location,
                containerType,
                null,
                null,
                null);
    }

    /**
     * Factory method to create a CONTAINER node for nested containers.
     *
     * @param ref the nested container reference
     * @return a new CONTAINER node with display name from the reference
     */
    public static SearchResultTreeNode containerNode(NestedContainerRef ref) {
        Preconditions.checkNotNull(ref, "NestedContainerRef cannot be null");
        return new SearchResultTreeNode(
                NodeType.CONTAINER,
                ref.toDisplayString(),
                null,
                null,
                ref,
                null,
                null);
    }

    /**
     * Factory method to create a CONTAINER node with a similarity score.
     * Used when the container itself is a search result.
     *
     * @param ref the nested container reference
     * @param scorePercent the similarity score percentage
     * @return a new CONTAINER node with score
     */
    public static SearchResultTreeNode containerNodeWithScore(NestedContainerRef ref, int scorePercent) {
        Preconditions.checkNotNull(ref, "NestedContainerRef cannot be null");
        return new SearchResultTreeNode(
                NodeType.CONTAINER,
                ref.toDisplayString(),
                null,
                null,
                ref,
                scorePercent,
                null);
    }

    /**
     * Factory method to create an ITEM node for search result items.
     *
     * @param data the item result data
     * @return a new ITEM node with display name from the data
     */
    public static SearchResultTreeNode itemNode(ItemResultData data) {
        Preconditions.checkNotNull(data, "ItemResultData cannot be null");
        return new SearchResultTreeNode(
                NodeType.ITEM,
                data.getDisplayName(),
                null,
                null,
                null,
                null,
                data);
    }

    /**
     * Gets the type of this node.
     *
     * @return the NodeType
     */
    public NodeType getType() {
        return type;
    }

    /**
     * Gets the display name for this node.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Adds a child node to this node.
     * No restrictions on node types are enforced at this level.
     *
     * @param child the child node to add
     * @throws NullPointerException if child is null
     */
    public void addChild(SearchResultTreeNode child) {
        Preconditions.checkNotNull(child, "Child node cannot be null");
        children.add(child);
    }

    /**
     * Gets the children of this node as an unmodifiable list.
     *
     * @return an unmodifiable list of children
     */
    public List<SearchResultTreeNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /**
     * Gets the world name if this is a LOCATION node.
     * Derives the value from the location object.
     *
     * @return the world name, or null if this is not a LOCATION node
     */
    @Nullable
    public String getWorld() {
        return location != null ? location.getWorld().getName() : null;
    }

    /**
     * Gets the coordinate string if this is a LOCATION node.
     * Derives the value from the location object.
     *
     * @return the coordinate string, or null if this is not a LOCATION node
     */
    @Nullable
    public String getCoords() {
        return location != null ? location.asCoordinates() : null;
    }

    /**
     * Gets the platform-agnostic Location if this is a LOCATION node.
     *
     * @return the Location, or null if this is not a LOCATION node
     */
    @Nullable
    public Location getLocation() {
        return location;
    }

    /**
     * Gets the container type if this is a LOCATION node.
     *
     * @return the container type (e.g., "CHEST", "BARREL"), or null if not set or not a LOCATION node
     */
    @Nullable
    public String getContainerType() {
        return containerType;
    }

    /**
     * Gets the nested container reference if this is a CONTAINER node.
     *
     * @return the NestedContainerRef, or null if this is not a CONTAINER node
     */
    @Nullable
    public NestedContainerRef getContainerRef() {
        return containerRef;
    }

    /**
     * Gets the similarity score percent if this is a CONTAINER node with a score.
     *
     * @return the score percentage (0-100), or null if no score
     */
    @Nullable
    public Integer getContainerScorePercent() {
        return containerScorePercent;
    }

    /**
     * Gets the item result data if this is an ITEM node.
     *
     * @return the ItemResultData, or null if this is not an ITEM node
     */
    @Nullable
    public ItemResultData getItemData() {
        return itemData;
    }

    @Override
    public String toString() {
        return "SearchResultTreeNode{" +
                "type=" + type +
                ", displayName='" + displayName + '\'' +
                ", children=" + children.size() +
                '}';
    }
}
