package org.aincraft.kitsune.model;

import com.google.common.base.Preconditions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a reference to a nested container with rich metadata.
 *
 * Example: A red shulker box in slot 12 of a chest.
 */
public record NestedContainerRef(
    String containerType,      // "shulker_box", "bundle", "chest", etc.
    @Nullable String color,    // "red", "blue", null for non-colored containers
    @Nullable String customName, // display name if the container was renamed
    int slotIndex              // slot position in parent container (0-based)
) {
    public NestedContainerRef {
        Preconditions.checkNotNull(containerType, "Container type cannot be null");
        Preconditions.checkArgument(!containerType.isBlank(), "Container type cannot be blank");
        Preconditions.checkArgument(slotIndex >= 0, "Slot index must be non-negative");
    }

    /**
     * Returns a display string like "Red Shulker Box (slot 12)" or "Bundle (slot 5)"
     */
    public String toDisplayString() {
        StringBuilder sb = new StringBuilder();

        // Build type with color if present
        if (color != null && !color.isBlank()) {
            sb.append(capitalizeFirst(color)).append(" ");
        }

        // Format container type: shulker_box -> Shulker Box
        String formattedType = formatContainerType(containerType);
        sb.append(formattedType);

        // Use custom name if available
        if (customName != null && !customName.isBlank()) {
            sb.append(" (\"").append(customName).append("\")");
        }

        sb.append(" (slot ").append(slotIndex).append(")");
        return sb.toString();
    }

    /**
     * Serializes to JSON object.
     * Example: {"type":"shulker_box","color":"red","customName":null,"slot":12}
     */
    public String toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", containerType);
        if (color != null) {
            obj.addProperty("color", color);
        }
        if (customName != null) {
            obj.addProperty("customName", customName);
        }
        obj.addProperty("slot", slotIndex);
        return obj.toString();
    }

    /**
     * Deserializes from JSON object.
     *
     * @param json JSON string representation
     * @return deserialized NestedContainerRef
     * @throws IllegalArgumentException if JSON is invalid
     */
    public static NestedContainerRef fromJson(String json) {
        Preconditions.checkNotNull(json, "JSON cannot be null");
        try {
            JsonElement elem = com.google.gson.JsonParser.parseString(json);
            Preconditions.checkArgument(elem.isJsonObject(), "Expected JSON object for NestedContainerRef");
            JsonObject obj = elem.getAsJsonObject();

            String type = obj.get("type").getAsString();
            String color = obj.has("color") && !obj.get("color").isJsonNull()
                ? obj.get("color").getAsString()
                : null;
            String customName = obj.has("customName") && !obj.get("customName").isJsonNull()
                ? obj.get("customName").getAsString()
                : null;
            int slot = obj.get("slot").getAsInt();

            return new NestedContainerRef(type, color, customName, slot);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON for NestedContainerRef: " + json, e);
        }
    }

    /**
     * Capitalizes first letter of a string.
     */
    private static String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    /**
     * Formats container type from snake_case to Title Case.
     * Example: "shulker_box" -> "Shulker Box"
     */
    private static String formatContainerType(String type) {
        return java.util.Arrays.stream(type.split("_"))
            .map(NestedContainerRef::capitalizeFirst)
            .collect(java.util.stream.Collectors.joining(" "));
    }
}
