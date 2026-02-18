package org.aincraft.kitsune.visualizer;

import org.aincraft.kitsune.config.KitsuneConfig;
import org.aincraft.kitsune.model.SearchResult;
import org.aincraft.kitsune.util.BukkitLocationFactory;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Visualizes container search results using ItemDisplay entities to show item icons.
 * Tracks displays per-container location so multiple chests can show items simultaneously.
 */
public class ContainerItemDisplay {
    private final Logger logger;
    private final KitsuneConfig config;
    private final JavaPlugin plugin;

    // Track displays per location key (world:x:y:z)
    private final Map<String, List<ItemDisplay>> locationDisplays = new HashMap<>();
    private final Map<String, Map<ItemDisplay, Double>> locationInitialAngles = new HashMap<>();
    private final Map<String, Integer> locationAnimationTasks = new HashMap<>();
    private final Map<String, Location> locationCenters = new HashMap<>();
    private final Map<String, TextDisplay> locationTextDisplays = new HashMap<>();

    public ContainerItemDisplay(Logger logger, KitsuneConfig config, JavaPlugin plugin) {
        this.logger = logger;
        this.config = config;
        this.plugin = plugin;
    }

    /**
     * Generates a unique key for a block location.
     */
    private String locationKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    /**
     * Spawns ItemDisplay entities showing the top items from a container search result.
     */
    public void spawnItemDisplays(SearchResult result, Player player) {
        if (player == null || !player.isOnline()) {
            logger.info("[ItemDisplay] Player is null or offline");
            return;
        }

        Location bukkitLoc = BukkitLocationFactory.toBukkitLocationOrNull(result.location());
        if (bukkitLoc == null) {
            logger.info("[ItemDisplay] BukkitLocation is null");
            return;
        }

        String locKey = locationKey(bukkitLoc);

        // Clean up existing displays for THIS location only
        clearLocationDisplays(locKey);

        int displayCount = config.visualizer().itemDisplayCount();
        if (displayCount <= 0) {
            displayCount = 6;
        }

        World world = bukkitLoc.getWorld();
        if (world == null) {
            logger.info("[ItemDisplay] World is null");
            return;
        }

        List<ItemStack> topItems = getTopItemsFromResult(result, displayCount);
        logger.info("[ItemDisplay] Preview: " + result.preview() + ", Items found: " + topItems.size());
        if (topItems.isEmpty()) {
            return;
        }

        List<ItemDisplay> displays = new ArrayList<>();
        Map<ItemDisplay, Double> initialAngles = new HashMap<>();

        double radius = config.visualizer().displayRadius();
        double heightOffset = config.visualizer().displayHeight();

        for (int i = 0; i < topItems.size(); i++) {
            double angle = (2 * Math.PI * i) / topItems.size(); // Spread evenly in circle
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            Location displayLoc = bukkitLoc.clone().add(x + 0.5, heightOffset, z + 0.5);
            final ItemStack itemToDisplay = topItems.get(i);

            try {
                ItemDisplay display = world.spawn(displayLoc, ItemDisplay.class, entity -> {
                    entity.setItemStack(itemToDisplay);
                    entity.setGlowing(true);
                    entity.setBrightness(new Display.Brightness(15, 15));

                    Transformation transformation = new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 0, 1),
                        new Vector3f(0.5f, 0.5f, 0.5f),
                        new AxisAngle4f(0, 0, 0, 1)
                    );
                    entity.setTransformation(transformation);
                    entity.setBillboard(Display.Billboard.CENTER);
                    entity.setViewRange(100f);
                });

                initialAngles.put(display, angle);
                displays.add(display);
            } catch (Exception e) {
                logger.warning("Failed to spawn ItemDisplay: " + e.getMessage());
            }
        }

        if (!displays.isEmpty()) {
            locationDisplays.put(locKey, displays);
            locationInitialAngles.put(locKey, initialAngles);
            locationCenters.put(locKey, bukkitLoc.clone());

            // Spawn TextDisplay showing item count
            int itemCount = topItems.size();
            Location textLoc = bukkitLoc.clone().add(0.5, heightOffset + 0.8, 0.5);
            try {
                TextDisplay textDisplay = world.spawn(textLoc, TextDisplay.class, entity -> {
                    entity.text(net.kyori.adventure.text.Component.text()
                        .append(net.kyori.adventure.text.Component.text("x" + itemCount,
                            net.kyori.adventure.text.format.NamedTextColor.YELLOW))
                        .append(net.kyori.adventure.text.Component.text(" items",
                            net.kyori.adventure.text.format.NamedTextColor.GRAY))
                        .build());
                    entity.setBillboard(Display.Billboard.CENTER);
                    entity.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                    entity.setBrightness(new Display.Brightness(15, 15));
                    entity.setViewRange(100f);
                });
                locationTextDisplays.put(locKey, textDisplay);
            } catch (Exception e) {
                logger.warning("Failed to spawn TextDisplay: " + e.getMessage());
            }

            // Start animation for this location
            if (config.visualizer().spinEnabled()) {
                startAnimation(locKey);
            }

            // Schedule cleanup for this location
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                clearLocationDisplays(locKey);
            }, config.visualizer().displayDurationTicks());
        }
    }

    private List<ItemStack> getTopItemsFromResult(SearchResult result, int maxItems) {
        List<ItemStack> items = new ArrayList<>();

        try {
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
     * Clears displays for a specific location.
     */
    private void clearLocationDisplays(String locKey) {
        // Cancel animation task
        Integer taskId = locationAnimationTasks.remove(locKey);
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }

        // Remove item displays
        List<ItemDisplay> displays = locationDisplays.remove(locKey);
        if (displays != null) {
            for (ItemDisplay display : displays) {
                if (display != null && !display.isDead()) {
                    display.remove();
                }
            }
        }

        // Remove text display
        TextDisplay textDisplay = locationTextDisplays.remove(locKey);
        if (textDisplay != null && !textDisplay.isDead()) {
            textDisplay.remove();
        }

        // Clear angles and center
        locationInitialAngles.remove(locKey);
        locationCenters.remove(locKey);
    }

    /**
     * Removes all displays for a specific player (cleanup on quit).
     */
    public void removeDisplaysForPlayer(Player player) {
        // This is now a no-op since we track by location, not player
        // Displays will naturally expire via their scheduled cleanup
    }

    /**
     * Removes all displays when the plugin is disabled.
     */
    public void cleanupAll() {
        for (List<ItemDisplay> displays : locationDisplays.values()) {
            for (ItemDisplay display : displays) {
                if (display != null && !display.isDead()) {
                    display.remove();
                }
            }
        }
        locationDisplays.clear();
        locationInitialAngles.clear();
        locationCenters.clear();

        for (TextDisplay textDisplay : locationTextDisplays.values()) {
            if (textDisplay != null && !textDisplay.isDead()) {
                textDisplay.remove();
            }
        }
        locationTextDisplays.clear();

        for (Integer taskId : locationAnimationTasks.values()) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
        locationAnimationTasks.clear();

        logger.info("Cleaned up all ItemDisplay entities");
    }

    /**
     * Starts the animation task for a specific location.
     */
    private void startAnimation(String locKey) {
        Location centerLoc = locationCenters.get(locKey);
        if (centerLoc == null) return;

        BukkitRunnable task = new BukkitRunnable() {
            private int tick = 0;

            @Override
            public void run() {
                List<ItemDisplay> displays = locationDisplays.get(locKey);
                Map<ItemDisplay, Double> angles = locationInitialAngles.get(locKey);

                if (displays == null || angles == null || displays.isEmpty()) {
                    cancel();
                    return;
                }

                double spinSpeed = config.visualizer().spinSpeed();
                double radius = config.visualizer().displayRadius();
                double heightOffset = config.visualizer().displayHeight();

                for (ItemDisplay display : displays) {
                    if (display == null || display.isDead()) {
                        continue;
                    }

                    Double initialAngle = angles.get(display);
                    if (initialAngle == null) {
                        continue;
                    }

                    // Keep items in fixed circle above chest, add gentle bobbing
                    double x = Math.cos(initialAngle) * radius;
                    double z = Math.sin(initialAngle) * radius;

                    // Bobbing motion (up/down oscillation)
                    double bobOffset = Math.sin(tick * 0.1) * 0.15;
                    double itemBobOffset = Math.sin(tick * 0.1 + initialAngle) * 0.1; // Slight phase offset per item

                    // Update position
                    Location newLoc = centerLoc.clone().add(x + 0.5, heightOffset + bobOffset + itemBobOffset, z + 0.5);
                    display.teleport(newLoc);

                    // Apply spin
                    if (config.visualizer().spinEnabled()) {
                        double spinAngle = Math.toRadians(spinSpeed * tick);
                        Transformation transformation = display.getTransformation();

                        AxisAngle4f spinRotation = new AxisAngle4f((float) spinAngle, 0, 1, 0);
                        transformation = new Transformation(
                            transformation.getTranslation(),
                            spinRotation,
                            transformation.getScale(),
                            new AxisAngle4f(transformation.getRightRotation())
                        );

                        display.setTransformation(transformation);
                    }
                }

                tick++;
            }
        };

        int taskId = task.runTaskTimer(plugin, 0, 1).getTaskId();
        locationAnimationTasks.put(locKey, taskId);
    }
}
