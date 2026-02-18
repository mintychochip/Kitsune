package org.aincraft.kitsune.model.tree;

import com.google.common.base.Preconditions;
import org.aincraft.kitsune.api.model.ContainerPath;
import org.aincraft.kitsune.Item;
import org.jetbrains.annotations.Nullable;

/**
 * Platform-agnostic data holder for item nodes in the search result tree.
 * Contains the minimal information needed to display a search result item
 * without any platform-specific dependencies.
 *
 * <p>This represents a single item found in a container, with metadata about
 * where it is and how relevant it is to the search query.
 */
public final class ItemResultData {
    private final String displayName;
    private final int slotIndex;
    private final int amount;
    private final int scorePercent;
    @Nullable
    private final ContainerPath containerPath;
    @Nullable
    private final Item item;

    /**
     * Creates a new ItemResultData instance.
     *
     * @param displayName the display name of the item (never null)
     * @param slotIndex the slot index in the container (0-based, must be non-negative)
     * @param amount the stack size/count of the item (must be positive)
     * @param scorePercent the similarity score as a percentage (0-100)
     * @param containerPath the path through nested containers to reach this item,
     *                      or null if the item is in the root container
     * @param item the live item data context, or null if not available
     * @throws NullPointerException if displayName is null
     * @throws IllegalArgumentException if slotIndex is negative, amount is non-positive, or scorePercent is out of range
     */
    public ItemResultData(
            String displayName,
            int slotIndex,
            int amount,
            int scorePercent,
            @Nullable ContainerPath containerPath,
            @Nullable Item item) {
        this.displayName = Preconditions.checkNotNull(displayName, "Display name cannot be null");
        Preconditions.checkArgument(slotIndex >= 0, "Slot index must be non-negative");
        this.slotIndex = slotIndex;
        Preconditions.checkArgument(amount > 0, "Amount must be positive");
        this.amount = amount;
        Preconditions.checkArgument(scorePercent >= 0 && scorePercent <= 100,
                "Score percent must be between 0 and 100");
        this.scorePercent = scorePercent;
        this.containerPath = containerPath;
        this.item = item;
    }

    /**
     * Gets the display name of the item.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the slot index where this item is located.
     *
     * @return the slot index (0-based)
     */
    public int getSlotIndex() {
        return slotIndex;
    }

    /**
     * Gets the stack size/count of this item.
     *
     * @return the amount (1-64 typically)
     */
    public int getAmount() {
        return amount;
    }

    /**
     * Gets the similarity score as a percentage.
     *
     * @return the score percentage (0-100)
     */
    public int getScorePercent() {
        return scorePercent;
    }

    /**
     * Gets the path through nested containers to reach this item.
     *
     * @return the ContainerPath, or null if item is in the root container
     */
    @Nullable
    public ContainerPath getContainerPath() {
        return containerPath;
    }

    /**
     * Gets the live item data context if available.
     *
     * @return the ItemContext, or null if not available
     */
    @Nullable
    public Item getItemContext() {
        return item;
    }

    /**
     * Factory method to create an ItemResultData for an item in the root container.
     *
     * @param displayName the item display name
     * @param slotIndex the slot index
     * @param amount the stack size/count
     * @param scorePercent the similarity score percentage
     * @return a new ItemResultData with null containerPath
     */
    public static ItemResultData ofRootItem(
            String displayName,
            int slotIndex,
            int amount,
            int scorePercent) {
        return new ItemResultData(displayName, slotIndex, amount, scorePercent, null, null);
    }

    /**
     * Factory method to create an ItemResultData for an item in the root container.
     *
     * @param displayName the item display name
     * @param slotIndex the slot index
     * @param amount the stack size/count
     * @param scorePercent the similarity score percentage
     * @param item the live item data context, or null if not available
     * @return a new ItemResultData with null containerPath
     */
    public static ItemResultData ofRootItem(
            String displayName,
            int slotIndex,
            int amount,
            int scorePercent,
            @Nullable Item item) {
        return new ItemResultData(displayName, slotIndex, amount, scorePercent, null, item);
    }

    /**
     * Factory method to create an ItemResultData for an item in a nested container.
     *
     * @param displayName the item display name
     * @param slotIndex the slot index
     * @param amount the stack size/count
     * @param scorePercent the similarity score percentage
     * @param containerPath the path through nested containers
     * @return a new ItemResultData with the specified containerPath
     * @throws NullPointerException if containerPath is null
     */
    public static ItemResultData ofNestedItem(
            String displayName,
            int slotIndex,
            int amount,
            int scorePercent,
            ContainerPath containerPath) {
        Preconditions.checkNotNull(containerPath, "Container path cannot be null for nested item");
        return new ItemResultData(displayName, slotIndex, amount, scorePercent, containerPath, null);
    }

    /**
     * Factory method to create an ItemResultData for an item in a nested container.
     *
     * @param displayName the item display name
     * @param slotIndex the slot index
     * @param amount the stack size/count
     * @param scorePercent the similarity score percentage
     * @param containerPath the path through nested containers
     * @param item the live item data context, or null if not available
     * @return a new ItemResultData with the specified containerPath
     * @throws NullPointerException if containerPath is null
     */
    public static ItemResultData ofNestedItem(
            String displayName,
            int slotIndex,
            int amount,
            int scorePercent,
            ContainerPath containerPath,
            @Nullable Item item) {
        Preconditions.checkNotNull(containerPath, "Container path cannot be null for nested item");
        return new ItemResultData(displayName, slotIndex, amount, scorePercent, containerPath, item);
    }

    @Override
    public String toString() {
        return "ItemResultData{" +
                "displayName='" + displayName + '\'' +
                ", slotIndex=" + slotIndex +
                ", amount=" + amount +
                ", scorePercent=" + scorePercent +
                ", containerPath=" + containerPath +
                ", itemContext=" + item +
                '}';
    }
}
