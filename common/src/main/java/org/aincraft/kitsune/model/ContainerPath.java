package org.aincraft.kitsune.model;

import com.google.common.base.Preconditions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the path through nested containers to reach an item.
 * For example: [Red Shulker Box (slot 12), Bundle (slot 5)] means the item is inside
 * a Bundle at slot 5, which is inside a Red Shulker Box at slot 12 of the root container.
 *
 * ROOT represents a top-level item (not in any container).
 *
 * Supports both rich NestedContainerRef format and legacy string format for backward compatibility.
 */
public record ContainerPath(List<NestedContainerRef> containerRefs) {
    public static final ContainerPath ROOT = new ContainerPath(Collections.emptyList());

    public ContainerPath {
        Preconditions.checkNotNull(containerRefs, "Container refs cannot be null");
        // Make immutable
        containerRefs = Collections.unmodifiableList(new ArrayList<>(containerRefs));
    }

    /**
     * Returns true if this is the root path (no containers).
     */
    public boolean isRoot() {
        return containerRefs.isEmpty();
    }

    /**
     * Returns the depth (number of nested containers).
     */
    public int depth() {
        return containerRefs.size();
    }

    /**
     * Appends a container reference to the path.
     * @param ref the container reference to push
     * @return a new ContainerPath with the ref appended
     */
    public ContainerPath push(NestedContainerRef ref) {
        Preconditions.checkNotNull(ref, "Container ref cannot be null");
        List<NestedContainerRef> newPath = new ArrayList<>(containerRefs);
        newPath.add(ref);
        return new ContainerPath(newPath);
    }

    /**
     * Returns the innermost container ref, or null if root.
     */
    @Nullable
    public NestedContainerRef getInnermost() {
        return containerRefs.isEmpty() ? null : containerRefs.get(containerRefs.size() - 1);
    }

    /**
     * Returns a display string like "in Red Shulker Box (slot 12) in Bundle (slot 5)" for the path.
     * For root, returns empty string.
     */
    public String toDisplayString() {
        if (containerRefs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (NestedContainerRef ref : containerRefs) {
            if (sb.length() > 0) {
                sb.append(" in ");
            } else {
                sb.append("in ");
            }
            sb.append(ref.toDisplayString());
        }
        return sb.toString();
    }

    /**
     * Serializes the path to JSON string.
     * Format: [{ref1}, {ref2}] or "[]" for root.
     * Uses new rich format with NestedContainerRef objects.
     */
    public String toJson() {
        JsonArray arr = new JsonArray();
        for (NestedContainerRef ref : containerRefs) {
            arr.add(JsonParser.parseString(ref.toJson()));
        }
        return arr.toString();
    }

    /**
     * Deserializes a path from JSON string.
     * Supports both new rich format and legacy string format for backward compatibility.
     *
     * @param json JSON string - either rich format [{...}] or legacy format ["str1", "str2"]
     * @return the deserialized ContainerPath
     * @throws IllegalArgumentException if JSON is invalid
     */
    public static ContainerPath fromJson(@Nullable String json) {
        if (json == null || json.isEmpty() || "[]".equals(json)) {
            return ROOT;
        }
        try {
            JsonElement elem = JsonParser.parseString(json);
            Preconditions.checkArgument(elem.isJsonArray(), "Expected JSON array for ContainerPath");
            JsonArray arr = elem.getAsJsonArray();

            if (arr.size() == 0) {
                return ROOT;
            }

            List<NestedContainerRef> refs = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JsonElement item = arr.get(i);

                // Support both formats: legacy strings and new objects
                if (item.isJsonPrimitive()) {
                    // Legacy format: convert simple string to a NestedContainerRef
                    String name = item.getAsString();
                    refs.add(new NestedContainerRef("container", null, name, i));
                } else if (item.isJsonObject()) {
                    // New format: parse as NestedContainerRef
                    refs.add(NestedContainerRef.fromJson(item.toString()));
                } else {
                    throw new IllegalArgumentException("Expected string or object in container path array at index " + i);
                }
            }
            return new ContainerPath(refs);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON for ContainerPath: " + json, e);
        }
    }

    /**
     * Creates a ContainerPath from legacy string list for backward compatibility.
     * Each string becomes a container with that custom name.
     *
     * @param containerNames legacy container names
     * @return new ContainerPath
     */
    public static ContainerPath fromLegacyStrings(List<String> containerNames) {
        Preconditions.checkNotNull(containerNames, "Container names cannot be null");
        if (containerNames.isEmpty()) {
            return ROOT;
        }
        List<NestedContainerRef> refs = new ArrayList<>();
        for (int i = 0; i < containerNames.size(); i++) {
            refs.add(new NestedContainerRef("container", null, containerNames.get(i), i));
        }
        return new ContainerPath(refs);
    }
}
