package org.aincraft.kitsune.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.aincraft.kitsune.api.model.ContainerPath;
import org.aincraft.kitsune.api.model.ContainerNode;
import org.jetbrains.annotations.Nullable;

/**
 * Platform-agnostic utility for extracting item data from JSON fullContent.
 *
 * Provides static methods to parse JSON and extract:
 * - Display names
 * - Slot indices
 * - Container paths
 * - Formatted material names
 *
 * This utility is designed to be reusable across different platform implementations
 * (Bukkit, Sponge, etc.) without any platform-specific dependencies.
 */
public final class ItemDataExtractor {
    private static final Gson GSON = new Gson();

    private ItemDataExtractor() {
        // Utility class - prevent instantiation
    }

    /**
     * Extract display name from JSON content.
     * Falls back to item name if display_name not found, then to formatted material name.
     *
     * @param jsonContent the JSON content string from fullContent field
     * @param logger logger for warnings (can be null)
     * @return the display name, or "Unknown Item" if extraction fails
     */
    @Nullable
    public static String extractDisplayName(String jsonContent, @Nullable Logger logger) {
        if (jsonContent == null || jsonContent.isEmpty()) {
            logFine(logger, "extractDisplayName: content is null or empty");
            return "Unknown Item";
        }
        try {
            JsonArray jsonArray = GSON.fromJson(jsonContent, JsonArray.class);
            if (jsonArray != null && jsonArray.size() > 0) {
                JsonObject itemObj = jsonArray.get(0).getAsJsonObject();

                // Priority 1: display_name
                if (itemObj.has("display_name")) {
                    String displayName = itemObj.get("display_name").getAsString();
                    if (displayName != null && !displayName.isEmpty()) {
                        return displayName;
                    }
                }

                // Priority 2: name
                if (itemObj.has("name")) {
                    String name = itemObj.get("name").getAsString();
                    if (name != null && !name.isEmpty()) {
                        return name;
                    }
                }

                // Priority 3: material (formatted)
                if (itemObj.has("material")) {
                    String material = itemObj.get("material").getAsString();
                    return formatMaterialName(material);
                }
            }
            logFine(logger, "extractDisplayName: no valid name found in JSON");
        } catch (Exception e) {
            logWarning(logger, "extractDisplayName: failed to parse JSON: " + e.getMessage());
        }
        return "Unknown Item";
    }

    /**
     * Extract slot index from JSON content.
     *
     * @param jsonContent the JSON content string from fullContent field
     * @param logger logger for warnings (can be null)
     * @return the slot index, or -1 if not found or extraction fails
     */
    public static int extractSlotIndex(String jsonContent, @Nullable Logger logger) {
        if (jsonContent == null || jsonContent.isEmpty()) {
            return -1;
        }
        try {
            JsonArray jsonArray = GSON.fromJson(jsonContent, JsonArray.class);
            if (jsonArray != null && jsonArray.size() > 0) {
                JsonObject itemObj = jsonArray.get(0).getAsJsonObject();
                if (itemObj.has("slot")) {
                    return itemObj.get("slot").getAsInt();
                }
            }
        } catch (Exception e) {
            logWarning(logger, "extractSlotIndex: failed to parse JSON: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Extract item amount/count from JSON content.
     *
     * @param jsonContent the JSON content string from fullContent field
     * @param logger logger for warnings (can be null)
     * @return the item amount, or 1 if not found or extraction fails (default stack size)
     */
    public static int extractAmount(String jsonContent, @Nullable Logger logger) {
        if (jsonContent == null || jsonContent.isEmpty()) {
            return 1;
        }
        try {
            JsonArray jsonArray = GSON.fromJson(jsonContent, JsonArray.class);
            if (jsonArray != null && jsonArray.size() > 0) {
                JsonObject itemObj = jsonArray.get(0).getAsJsonObject();
                if (itemObj.has("amount")) {
                    int amount = itemObj.get("amount").getAsInt();
                    return amount > 0 ? amount : 1;
                }
            }
        } catch (Exception e) {
            logWarning(logger, "extractAmount: failed to parse JSON: " + e.getMessage());
        }
        return 1;
    }

    /**
     * Extract container path from JSON content.
     * Supports both legacy string format and new JSON array format.
     *
     * Legacy format: "Yellow shulker_box" or "Red shulker_box → Bundle"
     * New format: [{"type":"shulker_box","color":"yellow","slot":5}]
     *
     * @param jsonContent the JSON content string from fullContent field
     * @param logger logger for warnings (can be null)
     * @return the ContainerPath, or ContainerPath.ROOT if extraction fails
     */
    public static ContainerPath extractContainerPath(String jsonContent, @Nullable Logger logger) {
        if (jsonContent == null || jsonContent.isEmpty()) {
            return ContainerPath.ROOT;
        }
        try {
            JsonArray jsonArray = GSON.fromJson(jsonContent, JsonArray.class);
            if (jsonArray != null && jsonArray.size() > 0) {
                JsonObject itemObj = jsonArray.get(0).getAsJsonObject();
                if (itemObj.has("container_path")) {
                    var pathElement = itemObj.get("container_path");

                    // Handle string format (legacy): "Yellow shulker_box" or "Red shulker_box → Bundle"
                    if (pathElement.isJsonPrimitive() && pathElement.getAsJsonPrimitive().isString()) {
                        String pathString = pathElement.getAsString();
                        if (pathString.isEmpty()) {
                            return ContainerPath.ROOT;
                        }
                        return parseLegacyContainerPath(pathString);
                    }

                    // Handle JSON array format (new): [{"type":"shulker_box","color":"yellow","slot":5}]
                    String pathJson = pathElement.toString();
                    return ContainerPath.fromJson(pathJson);
                }
            }
        } catch (Exception e) {
            logWarning(logger, "extractContainerPath: failed to parse JSON: " + e.getMessage());
        }
        return ContainerPath.ROOT;
    }

    /**
     * Parse legacy container path string format.
     * Splits by " → " and creates ContainerNode objects.
     *
     * @param pathString legacy path string like "Red shulker_box → Bundle"
     * @return parsed ContainerPath
     */
    private static ContainerPath parseLegacyContainerPath(String pathString) {
        // Split by " → " and create container refs
        String[] parts = pathString.split(" → ");
        List<ContainerNode> refs = new ArrayList<>();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();

            // Parse "Yellow shulker_box" or "Bundle" format
            String containerType = "container";
            String color = null;
            String customName = null;

            if (part.toLowerCase().contains("shulker_box") || part.toLowerCase().contains("shulker box")) {
                containerType = "shulker_box";
                // Extract color if present (e.g., "Yellow shulker_box" -> color="yellow")
                String lowerPart = part.toLowerCase();
                int shulkerIdx = lowerPart.indexOf("shulker");
                if (shulkerIdx > 0) {
                    color = part.substring(0, shulkerIdx).trim().toLowerCase();
                }
            } else if (part.toLowerCase().contains("bundle")) {
                containerType = "bundle";
            } else {
                customName = part;
            }

            refs.add(new ContainerNode(containerType, color, customName, i, null, null));
        }
        return new ContainerPath(refs);
    }

    /**
     * Format material name from constant to human-readable format.
     * Example: DIAMOND_PICKAXE -> Diamond Pickaxe
     *
     * @param material the material constant (e.g., "DIAMOND_PICKAXE")
     * @return formatted material name, or "Unknown Item" if null
     */
    public static String formatMaterialName(@Nullable String material) {
        if (material == null) {
            return "Unknown Item";
        }

        // Convert DIAMOND_PICKAXE to Diamond Pickaxe
        String[] words = material.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return sb.toString();
    }

    /**
     * Build path display string like "Chest → Red Shulker Box → Bundle".
     *
     * @param path the container path to format
     * @return formatted path display string
     */
    public static String buildPathDisplay(ContainerPath path) {
        if (path.isRoot()) {
            return "Chest";
        }

        StringBuilder sb = new StringBuilder("Chest");
        for (var ref : path.containerRefs()) {
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
                        containerName = color.substring(0, 1).toUpperCase() + color.substring(1) + " Shulker Box";
                    } else {
                        containerName = "Shulker Box";
                    }
                } else if ("bundle".equals(type)) {
                    containerName = "Bundle";
                } else {
                    containerName = "Container";
                }
            }
            sb.append(containerName);
        }
        return sb.toString();
    }

    // Logger helpers (null-safe)

    private static void logFine(@Nullable Logger logger, String message) {
        if (logger != null) {
            logger.fine(message);
        }
    }

    private static void logWarning(@Nullable Logger logger, String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }
}
