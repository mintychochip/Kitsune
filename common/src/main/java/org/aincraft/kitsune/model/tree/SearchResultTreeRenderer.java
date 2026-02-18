package org.aincraft.kitsune.model.tree;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.aincraft.kitsune.Util;
import org.aincraft.kitsune.api.model.ContainerPath;
import org.aincraft.kitsune.api.model.ContainerNode;
import org.jetbrains.annotations.Nullable;

/**
 * Platform-agnostic renderer for search result trees. Converts SearchResultTreeNode hierarchies
 * into Adventure Components for display in consoles, logs, or UI systems.
 * <p>
 * No Bukkit-specific dependencies. Uses text-based hover events and generic container labels.
 *
 * <p>Example output:
 * <pre>
 * Container [100, 64, 200]
 * ├─ [0] Diamond (95%)
 * ├─ [1] Gold Ingot (87%)
 * └─ [5] Red Shulker Box (2 items)
 *    ├─ [0] Emerald (92%)
 *    └─ [1] Iron Ingot (78%)
 * </pre>
 */
public final class SearchResultTreeRenderer {

  private static final String BRANCH = "\u251C\u2500 "; // ├─
  private static final String LAST_BRANCH = "\u2514\u2500 "; // └─
  private static final String VERTICAL = "\u2502  "; // │ + 2 spaces
  private static final String SPACE = "   ";

  public static SearchResultTreeRenderer RENDERER = new SearchResultTreeRenderer();

  private SearchResultTreeRenderer() {

  }

  /**
   * Renders a list of root nodes (locations) as Adventure Components.
   *
   * @param roots the root location nodes to render
   * @return a list of components ready for display
   */
  public List<Component> render(List<SearchResultTreeNode> roots) {
    List<Component> lines = new ArrayList<>();

    for (int i = 0; i < roots.size(); i++) {
      SearchResultTreeNode root = roots.get(i);

      // Render location header
      if (root.getType() == SearchResultTreeNode.NodeType.LOCATION) {
        String coords = root.getCoords();
        String containerType = root.getContainerType();

        // Format container type nicely (CHEST -> Chest, TRAPPED_CHEST -> Trapped Chest)
        String containerName = "Container";
        if (containerType != null && !containerType.isEmpty()) {
          containerName = Util.fromMaterialToTitleCase(containerType);
        }

        Component header =
            Component.text(containerName, NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, false)
                .append(Component.text(" [", NamedTextColor.DARK_GRAY))
                .append(
                    Component.text(coords != null ? coords : "?", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.BOLD, false))
                .append(Component.text("]", NamedTextColor.DARK_GRAY));

        lines.add(header);

        // Render children with proper indentation
        List<SearchResultTreeNode> children = root.getChildren();
        for (int j = 0; j < children.size(); j++) {
          boolean isLast = (j == children.size() - 1);
          renderNode(children.get(j), "", isLast, lines);
        }

        // Add empty line between locations (except after the last one)
        if (i < roots.size() - 1) {
          lines.add(Component.empty());
        }
      }
    }

    return lines;
  }

  /**
   * Recursively renders a node and its children.
   *
   * @param node   the node to render
   * @param prefix the prefix to prepend to this line
   * @param isLast whether this is the last child of its parent
   * @param lines  the output list of components to append to
   */
  private void renderNode(
      SearchResultTreeNode node, String prefix, boolean isLast, List<Component> lines) {

    // Determine connector and child prefix
    String connector = isLast ? LAST_BRANCH : BRANCH;
    String childPrefix = prefix + (isLast ? SPACE : VERTICAL);

    // Build the component for this node
    Component connector_component = Component.text(connector, NamedTextColor.WHITE);

    Component nodeComponent;

    if (node.getType() == SearchResultTreeNode.NodeType.CONTAINER) {
      // Render container
      nodeComponent = buildContainerComponent(node, connector_component);
    } else if (node.getType() == SearchResultTreeNode.NodeType.ITEM) {
      // Render item
      nodeComponent = buildItemComponent(node, connector_component);
    } else {
      // Should not happen for non-root nodes
      nodeComponent = Component.text(node.getDisplayName());
    }

    // Add the line with prefix
    lines.add(Component.text(prefix, NamedTextColor.WHITE).append(nodeComponent));

    // Recursively render children
    List<SearchResultTreeNode> children = node.getChildren();
    for (int i = 0; i < children.size(); i++) {
      boolean isLast_child = (i == children.size() - 1);
      renderNode(children.get(i), childPrefix, isLast_child, lines);
    }
  }

  /**
   * Builds the component for a container node.
   *
   * @param node      the container node
   * @param connector the connector component (├─ or └─)
   * @return the complete component for the container
   */
  private Component buildContainerComponent(SearchResultTreeNode node, Component connector) {

    ContainerNode ref = node.getContainerRef();
    if (ref == null) {
      return connector.append(
          Component.text(node.getDisplayName(), NamedTextColor.AQUA)
              .decoration(TextDecoration.BOLD, false));
    }

    // Format container name without the slot suffix
    Component containerName = formatContainerNameWithoutSlot(ref);

    // Count items in this container
    int itemCount = countItems(node);
    Component itemCountComponent =
        Component.text(String.format(" [%d items]", itemCount), NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.BOLD, false);

    // Create hover event showing item count
    HoverEvent<?> hover =
        HoverEvent.showText(
            Component.text(String.format("Contains %d items", itemCount), NamedTextColor.GRAY));

    return connector
        .append(containerName)
        .append(itemCountComponent.hoverEvent(hover));
  }

  /**
   * Builds the component for an item node.
   *
   * @param node      the item node
   * @param connector the connector component (├─ or └─)
   * @return the complete component for the item
   */
  private Component buildItemComponent(SearchResultTreeNode node, Component connector) {

    ItemResultData itemData = node.getItemData();
    if (itemData == null) {
      return connector.append(
          Component.text(node.getDisplayName(), NamedTextColor.WHITE)
              .decoration(TextDecoration.BOLD, false));
    }

    // Build amount component "x64 "
    Component amountComponent =
        Component.text(String.format("x%d ", itemData.getAmount()), NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.BOLD, false);

    // Build item name component
    Component itemName =
        Component.text(itemData.getDisplayName(), NamedTextColor.WHITE)
            .decoration(TextDecoration.BOLD, false);

    // Build score component " (95%)"
    Component scoreComponent =
        Component.text(
                String.format(" (%d%%)", itemData.getScorePercent()),
                NamedTextColor.YELLOW)
            .decoration(TextDecoration.BOLD, false);

    // Build hover event
    HoverEvent<?> hover = buildItemHoverEvent(itemData);

    return connector
        .append(amountComponent)
        .append(itemName)
        .append(scoreComponent)
        .hoverEvent(hover);
  }

  /**
   * Builds the hover event for an item. If the live item context is available, uses its rich hover
   * event (with enchantments, lore, etc.). Otherwise, falls back to text-based hover with item
   * name, match percentage, and location details.
   *
   * @param itemData the item data
   * @return the hover event
   */
  private HoverEvent<?> buildItemHoverEvent(ItemResultData itemData) {
    // Check if live item context is available and use its hover event
    if (itemData.getItemContext() != null) {
      return itemData.getItemContext().asHoverEvent();
    }

    // Fallback to text hover with item name, score, and location details
    Component locationDetails = buildLocationDetailsComponent(itemData);
    Component hoverText =
        Component.text(itemData.getDisplayName(), NamedTextColor.WHITE)
            .append(Component.newline())
            .append(Component.text("Match: ", NamedTextColor.GRAY))
            .append(
                Component.text(
                    String.format("%d%%", itemData.getScorePercent()), NamedTextColor.YELLOW))
            .append(Component.newline())
            .append(locationDetails);
    return HoverEvent.showText(hoverText);
  }

  /**
   * Builds a component with detailed location information for hover text.
   *
   * @param itemData the item data
   * @return component containing slot index and container path
   */
  private Component buildLocationDetailsComponent(ItemResultData itemData) {
    Component component = Component.empty();

    // Add slot index
    component =
        component
            .append(Component.text("Slot: ", NamedTextColor.GRAY))
            .append(
                Component.text(String.valueOf(itemData.getSlotIndex()), NamedTextColor.YELLOW));

    // Add location/path information
    ContainerPath containerPath = itemData.getContainerPath();
    if (containerPath == null || containerPath.isRoot()) {
      // Root item: show location as Chest
      component =
          component
              .append(Component.newline())
              .append(Component.text("Location: ", NamedTextColor.GRAY))
              .append(Component.text("Chest", NamedTextColor.YELLOW));
    } else {
      // Nested item: show formatted path
      String pathDisplay = buildPathDisplay(containerPath);
      component =
          component
              .append(Component.newline())
              .append(Component.text("Path: ", NamedTextColor.GRAY))
              .append(Component.text(pathDisplay, NamedTextColor.YELLOW));
    }

    return component;
  }

  /**
   * Builds a formatted path string like "Chest → Red Shulker Box → Bundle".
   *
   * @param path the container path
   * @return formatted path string
   */
  private String buildPathDisplay(ContainerPath path) {
    if (path.isRoot()) {
      return "Chest";
    }

    StringBuilder sb = new StringBuilder("Chest");
    for (ContainerNode ref : path.containerRefs()) {
      sb.append(" → ");

      // Format the container name nicely
      String containerName = ref.getCustomName();
      if (containerName == null || containerName.isEmpty()) {
        // Use type with color if available
        String type = ref.getContainerType();
        String color = ref.getColor();

        if ("shulker_box".equals(type)) {
          if (color != null && !color.isEmpty()) {
            // Capitalize first letter: "red" -> "Red"
            containerName =
                color.substring(0, 1).toUpperCase()
                    + color.substring(1).toLowerCase()
                    + " Shulker Box";
          } else {
            containerName = "Shulker Box";
          }
        } else if ("bundle".equals(type)) {
          containerName = "Bundle";
        } else {
          // Format container type: shulker_box -> Shulker Box
          String[] parts = type.split("_");
          StringBuilder typeSb = new StringBuilder();
          for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
              typeSb.append(" ");
            }
            String part = parts[i];
            if (!part.isEmpty()) {
              typeSb
                  .append(Character.toUpperCase(part.charAt(0)))
                  .append(part.substring(1).toLowerCase());
            }
          }
          containerName = typeSb.toString();
        }
      }
      sb.append(containerName);
    }
    return sb.toString();
  }

  /**
   * Formats a container name without the slot suffix. E.g., "Yellow Shulker Box" instead of "Yellow
   * Shulker Box (slot 12)"
   *
   * @param ref the nested container reference
   * @return the formatted component
   */
  private Component formatContainerNameWithoutSlot(ContainerNode ref) {
    StringBuilder sb = new StringBuilder();

    // Add color if present
    if (ref.getColor() != null && !ref.getColor().isBlank()) {
      String color = ref.getColor();
      sb.append(Character.toUpperCase(color.charAt(0)))
          .append(color.substring(1).toLowerCase())
          .append(" ");
    }

    // Format container type: shulker_box -> Shulker Box
    String type = ref.getContainerType();
    String[] parts = type.split("_");
    for (int i = 0; i < parts.length; i++) {
      if (i > 0) {
        sb.append(" ");
      }
      String part = parts[i];
      if (!part.isEmpty()) {
        sb.append(Character.toUpperCase(part.charAt(0)))
            .append(part.substring(1).toLowerCase());
      }
    }

    // Add custom name if present
    if (ref.getCustomName() != null && !ref.getCustomName().isBlank()) {
      sb.append(" (\"").append(ref.getCustomName()).append("\")");
    }

    return Component.text(sb.toString(), NamedTextColor.AQUA)
        .decoration(TextDecoration.BOLD, false);
  }

  /**
   * Recursively counts all item children of a node.
   *
   * @param node the node to count items in
   * @return the total number of item nodes in this subtree
   */
  private int countItems(SearchResultTreeNode node) {
    int count = 0;

    for (SearchResultTreeNode child : node.getChildren()) {
      if (child.getType() == SearchResultTreeNode.NodeType.ITEM) {
        count++;
      } else if (child.getType() == SearchResultTreeNode.NodeType.CONTAINER) {
        count += countItems(child);
      }
    }

    return count;
  }
}
