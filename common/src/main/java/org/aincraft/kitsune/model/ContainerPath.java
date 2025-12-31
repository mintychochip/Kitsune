package org.aincraft.kitsune.model;

import com.google.common.base.Preconditions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the path through nested containers to reach an item.
 * For example: ["Shulker Box A", "Shulker Box B"] means the item is inside "Shulker Box B"
 * which is inside "Shulker Box A".
 *
 * ROOT represents a top-level item (not in any container).
 */
public record ContainerPath(List<String> containerNames) {
    public static final ContainerPath ROOT = new ContainerPath(Collections.emptyList());

    public ContainerPath {
        Preconditions.checkNotNull(containerNames, "Container names cannot be null");
        // Make immutable
        containerNames = Collections.unmodifiableList(new ArrayList<>(containerNames));
    }

    /**
     * Returns true if this is the root path (no containers).
     */
    public boolean isRoot() {
        return containerNames.isEmpty();
    }

    /**
     * Returns the depth (number of nested containers).
     */
    public int depth() {
        return containerNames.size();
    }

    /**
     * Appends a container name to the path.
     * @param name the name of the container to push
     * @return a new ContainerPath with the name appended
     */
    public ContainerPath push(String name) {
        Preconditions.checkNotNull(name, "Container name cannot be null");
        Preconditions.checkArgument(!name.isBlank(), "Container name cannot be blank");
        List<String> newPath = new ArrayList<>(containerNames);
        newPath.add(name);
        return new ContainerPath(newPath);
    }

    /**
     * Returns the innermost container name, or null if root.
     */
    @Nullable
    public String getInnermost() {
        return containerNames.isEmpty() ? null : containerNames.get(containerNames.size() - 1);
    }

    /**
     * Returns a display string like "in X in Y" for the path.
     * For root, returns empty string.
     */
    public String toDisplayString() {
        if (containerNames.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String name : containerNames) {
            if (sb.length() > 0) {
                sb.append(" in ");
            } else {
                sb.append("in ");
            }
            sb.append(name);
        }
        return sb.toString();
    }

    /**
     * Serializes the path to JSON string.
     * Format: ["Container1", "Container2"] or "[]" for root.
     */
    public String toJson() {
        JsonArray arr = new JsonArray();
        for (String name : containerNames) {
            arr.add(name);
        }
        return arr.toString();
    }

    /**
     * Deserializes a path from JSON string.
     * @param json JSON array string like ["Container1", "Container2"]
     * @return the deserialized ContainerPath
     * @throws IllegalArgumentException if JSON is invalid
     */
    public static ContainerPath fromJson(@Nullable String json) {
        if (json == null || json.isEmpty() || "[]".equals(json)) {
            return ROOT;
        }
        try {
            JsonElement elem = JsonParser.parseString(json);
            if (!elem.isJsonArray()) {
                throw new IllegalArgumentException("Expected JSON array for ContainerPath");
            }
            JsonArray arr = elem.getAsJsonArray();
            List<String> names = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                names.add(arr.get(i).getAsString());
            }
            return new ContainerPath(names);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON for ContainerPath: " + json, e);
        }
    }
}
