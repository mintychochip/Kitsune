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

public final class ItemDataExtractor {
    private static final Gson GSON = new Gson();

    private ItemDataExtractor() {}

    @Nullable
    public static String extractDisplayName(String jsonContent, @Nullable Logger logger) {
        if (jsonContent == null || jsonContent.isEmpty()) {
            return "Unknown Item";
        }
        try {
            JsonArray jsonArray = GSON.fromJson(jsonContent, JsonArray.class);
            if (jsonArray != null && jsonArray.size() > 0) {
                JsonObject itemObj = jsonArray.get(0).getAsJsonObject();

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
                    return formatMaterialName(itemObj.get("material").getAsString());
                }
            }
        } catch (Exception e) {
            logWarning(logger, "extractDisplayName: " + e.getMessage());
        }
        return "Unknown Item";
    }

    public static int extractSlotIndex(String jsonContent, @Nullable Logger logger) {
        if (jsonContent == null || jsonContent.isEmpty()) return -1;
        try {
            JsonArray jsonArray = GSON.fromJson(jsonContent, JsonArray.class);
            if (jsonArray != null && jsonArray.size() > 0) {
                JsonObject itemObj = jsonArray.get(0).getAsJsonObject();
                if (itemObj.has("slot")) {
                    return itemObj.get("slot").getAsInt();
                }
            }
        } catch (Exception e) {
            logWarning(logger, "extractSlotIndex: " + e.getMessage());
        }
        return -1;
    }

    public static int extractAmount(String jsonContent, @Nullable Logger logger) {
        if (jsonContent == null || jsonContent.isEmpty()) return 1;
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
            logWarning(logger, "extractAmount: " + e.getMessage());
        }
        return 1;
    }

    public static ContainerPath extractContainerPath(String jsonContent, @Nullable Logger logger) {
        if (jsonContent == null || jsonContent.isEmpty()) return ContainerPath.ROOT;
        try {
            JsonArray jsonArray = GSON.fromJson(jsonContent, JsonArray.class);
            if (jsonArray != null && jsonArray.size() > 0) {
                JsonObject itemObj = jsonArray.get(0).getAsJsonObject();
                if (itemObj.has("container_path")) {
                    var pathElement = itemObj.get("container_path");
                    if (pathElement.isJsonPrimitive() && pathElement.getAsJsonPrimitive().isString()) {
                        String pathString = pathElement.getAsString();
                        if (pathString.isEmpty()) return ContainerPath.ROOT;
                        return parseLegacyContainerPath(pathString);
                    }
                    return ContainerPath.fromJson(pathElement.toString());
                }
            }
        } catch (Exception e) {
            logWarning(logger, "extractContainerPath: " + e.getMessage());
        }
        return ContainerPath.ROOT;
    }

    private static ContainerPath parseLegacyContainerPath(String pathString) {
        String[] parts = pathString.split(" → ");
        List<ContainerNode> refs = new ArrayList<>();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            String containerType = "container";
            String color = null;
            String customName = null;

            if (part.toLowerCase().contains("shulker_box") || part.toLowerCase().contains("shulker box")) {
                containerType = "shulker_box";
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

    public static String formatMaterialName(@Nullable String material) {
        if (material == null) return "Unknown Item";
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

    public static String buildPathDisplay(ContainerPath path) {
        if (path.isRoot()) return "Chest";

        StringBuilder sb = new StringBuilder("Chest");
        for (var ref : path.containerRefs()) {
            sb.append(" → ");

            String containerName = ref.getCustomName();
            if (containerName == null || containerName.isEmpty()) {
                String type = ref.getContainerType();
                String color = ref.getColor();

                if ("shulker_box".equals(type)) {
                    containerName = (color != null && !color.isEmpty())
                        ? color.substring(0, 1).toUpperCase() + color.substring(1) + " Shulker Box"
                        : "Shulker Box";
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

    private static void logWarning(@Nullable Logger logger, String message) {
        if (logger != null) logger.warning(message);
    }
}
