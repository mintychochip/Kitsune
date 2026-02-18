package org.aincraft.kitsune.visualizer;

import org.aincraft.kitsune.config.KitsuneConfig;
import org.aincraft.kitsune.model.SearchResult;
import org.aincraft.kitsune.util.BukkitLocationFactory;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Visualizes container search results using ItemDisplay entities to show item icons.
 */
public class ContainerItemDisplay {
    private final Logger logger;
    private final KitsuneConfig config;
    private final JavaPlugin plugin;
    private final Map<UUID, List<ItemDisplay>> playerDisplays = new HashMap<>();

    public ContainerItemDisplay(Logger logger, KitsuneConfig config, JavaPlugin plugin) {
        this.logger = logger;
        this.config = config;
        this.plugin = plugin;
    }

    /**
     * Spawns ItemDisplay entities showing the top items from a container search result.
     */
    public void spawnItemDisplays(SearchResult result, Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        Location bukkitLoc = BukkitLocationFactory.toBukkitLocationOrNull(result.location());
        if (bukkitLoc == null) {
            return;
        }

        int displayCount = config.visualizer().itemDisplayCount();
        if (displayCount <= 0) {
            displayCount = 6;
        }

        World world = bukkitLoc.getWorld();
        if (world == null) {
            return;
        }

        List<ItemStack> topItems = getTopItemsFromResult(result, displayCount);
        if (topItems.isEmpty()) {
            return;
        }

        List<ItemDisplay> displays = new ArrayList<>();

        // Calculate positions in an arc above the container
        double radius = config.visualizer().displayRadius();
        double heightOffset = config.visualizer().displayHeight();
        double baseY = bukkitLoc.getY() + heightOffset;

        for (int i = 0; i < topItems.size(); i++) {
            // Spread items in a semicircle arc
            double angle = Math.PI * (i / (double) Math.max(1, topItems.size() - 1)); // 0 to PI
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            Location displayLoc = bukkitLoc.clone().add(x + 0.5, heightOffset, z + 0.5);

            // Create final copy of item for lambda
            final ItemStack itemToDisplay = topItems.get(i);

            try {
                // Spawn ItemDisplay entity
                ItemDisplay display = world.spawn(displayLoc, ItemDisplay.class, entity -> {
                    // Set the item to display
                    entity.setItemStack(itemToDisplay);

                    // Configure the display
                    entity.setGlowing(true);
                    entity.setBrightness(new Display.Brightness(15, 15));

                    // Set transformation for proper scale
                    Transformation transformation = new Transformation(
                        new Vector3f(0, 0, 0), // translation
                        new AxisAngle4f(0, 0, 0, 1), // left rotation (identity)
                        new Vector3f(0.5f, 0.5f, 0.5f), // scale - half size
                        new AxisAngle4f(0, 0, 0, 1) // right rotation (identity)
                    );
                    entity.setTransformation(transformation);

                    // Set billboard mode to always face the player
                    entity.setBillboard(Display.Billboard.CENTER);

                    // Set view range for visibility
                    entity.setViewRange(100f);
                });

                displays.add(display);
            } catch (Exception e) {
                logger.warning("Failed to spawn ItemDisplay: " + e.getMessage());
            }
        }

        if (!displays.isEmpty()) {
            playerDisplays.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).addAll(displays);

            // Schedule cleanup after configured duration
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                removeDisplays(displays);
                // Remove from player's list
                List<ItemDisplay> playerList = playerDisplays.get(player.getUniqueId());
                if (playerList != null) {
                    playerList.removeAll(displays);
                    if (playerList.isEmpty()) {
                        playerDisplays.remove(player.getUniqueId());
                    }
                }
            }, config.visualizer().displayDurationTicks());
        }
    }

    /**
     * Gets the top items from a search result.
     * TODO: In a full implementation, retrieve actual items from the container.
     */
    private List<ItemStack> getTopItemsFromResult(SearchResult result, int maxItems) {
        List<ItemStack> items = new ArrayList<>();

        try {
            // For now, create example items based on the search preview
            // TODO: Use vectorStorage to get actual container contents
            String preview = result.preview();
            if (preview == null) {
                preview = "";
            }
            String lowerPreview = preview.toLowerCase();

            if (lowerPreview.contains("diamond")) {
                items.add(new ItemStack(org.bukkit.Material.DIAMOND));
            }
            if (lowerPreview.contains("iron")) {
                items.add(new ItemStack(org.bukkit.Material.IRON_INGOT));
            }
            if (lowerPreview.contains("gold")) {
                items.add(new ItemStack(org.bukkit.Material.GOLD_INGOT));
            }
            if (lowerPreview.contains("emerald")) {
                items.add(new ItemStack(org.bukkit.Material.EMERALD));
            }
            if (lowerPreview.contains("netherite")) {
                items.add(new ItemStack(org.bukkit.Material.NETHERITE_INGOT));
            }
            if (lowerPreview.contains("redstone")) {
                items.add(new ItemStack(org.bukkit.Material.REDSTONE));
            }
            if (lowerPreview.contains("lapis")) {
                items.add(new ItemStack(org.bukkit.Material.LAPIS_LAZULI));
            }
            if (lowerPreview.contains("coal")) {
                items.add(new ItemStack(org.bukkit.Material.COAL));
            }
            if (lowerPreview.contains("chest") || lowerPreview.contains("container")) {
                items.add(new ItemStack(org.bukkit.Material.CHEST));
            }
            if (lowerPreview.contains("shulker")) {
                items.add(new ItemStack(org.bukkit.Material.SHULKER_BOX));
            }
            if (lowerPreview.contains("barrel")) {
                items.add(new ItemStack(org.bukkit.Material.BARREL));
            }
            if (lowerPreview.contains("sword")) {
                items.add(new ItemStack(org.bukkit.Material.DIAMOND_SWORD));
            }
            if (lowerPreview.contains("pickaxe")) {
                items.add(new ItemStack(org.bukkit.Material.DIAMOND_PICKAXE));
            }
            if (lowerPreview.contains("apple")) {
                items.add(new ItemStack(org.bukkit.Material.APPLE));
            }

            // If no items matched, add defaults based on common items
            if (items.isEmpty()) {
                items.add(new ItemStack(org.bukkit.Material.CHEST));
                items.add(new ItemStack(org.bukkit.Material.DIAMOND));
                if (maxItems > 2) {
                    items.add(new ItemStack(org.bukkit.Material.IRON_INGOT));
                }
                if (maxItems > 3) {
                    items.add(new ItemStack(org.bukkit.Material.GOLD_INGOT));
                }
            }

            // Limit to maxItems
            if (items.size() > maxItems) {
                return items.subList(0, maxItems);
            }

        } catch (Exception e) {
            logger.warning("Failed to get items from search result: " + e.getMessage());
            items.add(new ItemStack(org.bukkit.Material.CHEST));
        }

        return items;
    }

    /**
     * Removes a list of displays.
     */
    private void removeDisplays(List<ItemDisplay> displays) {
        for (ItemDisplay display : displays) {
            if (display != null && !display.isDead()) {
                display.remove();
            }
        }
    }

    /**
     * Removes all active displays for a specific player.
     */
    public void removeDisplaysForPlayer(Player player) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        List<ItemDisplay> displays = playerDisplays.remove(playerId);

        if (displays != null) {
            removeDisplays(displays);
        }
    }

    /**
     * Removes all displays when the plugin is disabled.
     */
    public void cleanupAll() {
        for (List<ItemDisplay> displays : playerDisplays.values()) {
            removeDisplays(displays);
        }
        playerDisplays.clear();
        logger.info("Cleaned up all ItemDisplay entities");
    }
}
