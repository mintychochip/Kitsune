# Phase 3: NeoForge Event Handlers Implementation

## Overview
Implemented event handlers for the NeoForge module to monitor container interactions and block breaks, triggering reindexing as needed.

## Files Created

### 1. ContainerCloseHandler.java
**Location:** `neoforge/src/main/java/org/aincraft/chestfind/listener/ContainerCloseHandler.java`

**Purpose:** Monitors container open/close events and schedules reindexing when container contents change.

**Key Features:**
- Listens to `PlayerContainerEvent.Open` and `PlayerContainerEvent.Close` events
- Snapshots container contents when opened (for change detection)
- Compares snapshot vs current state on close
- Only reindexes if contents have changed (optimization)
- Uses `@Mod.EventBusSubscriber` for automatic event registration (NeoForge pattern)

**Implementation Notes:**
- Uses `BaseContainerBlockEntity` to access container inventory
- Reflection-based approach to extract BlockEntity from `AbstractContainerMenu`
- ItemSnapshot inner class compares item type, amount, and NBT data
- Static setter pattern for dependency injection (`setIndexer()`)

**TODO Items:**
- Extend `ContainerIndexer` with NeoForge-compatible `scheduleIndex()` method that accepts `LocationData`
- Current implementation has placeholder in `scheduleIndexing()` - needs completion once API extended

### 2. BlockBreakHandler.java
**Location:** `neoforge/src/main/java/org/aincraft/chestfind/listener/BlockBreakHandler.java`

**Purpose:** Removes containers from search index when they're broken.

**Key Features:**
- Listens to `BlockEvent.BreakEvent`
- Checks if broken block is a container (BaseContainerBlockEntity)
- Calls `VectorStorage.delete(LocationData)` to remove from index
- Uses NeoForgeLocationConverter for platform-agnostic location handling

**Implementation Notes:**
- Server-side only (DEDICATED_SERVER dist marker)
- Uses try-catch to handle async deletion errors gracefully
- Converts NeoForge block position to platform-agnostic LocationData

### 3. ItemTransferHandler.java
**Location:** `neoforge/src/main/java/org/aincraft/chestfind/listener/ItemTransferHandler.java`

**Purpose:** (Optional) Monitors item transfers via hopper and other mechanisms.

**Key Features:**
- Listens to `BlockEvent.NeighborNotifyEvent` for indirect transfer detection
- Checks container neighbor changes that indicate hopper activity
- Schedules reindexing when items move between containers
- Uses NeoForge capability system to detect item handlers

**Implementation Notes:**
- More conservative approach compared to Bukkit `InventoryMoveItemEvent`
- NeoForge doesn't have direct equivalent to InventoryMoveItemEvent
- Uses capability system and neighbor notification events instead
- Placeholder in `scheduleIndexing()` - needs completion once ContainerIndexer extended

### 4. NeoForgeLocationConverter.java
**Location:** `neoforge/src/main/java/org/aincraft/chestfind/listener/NeoForgeLocationConverter.java`

**Purpose:** Utility class for converting NeoForge BlockEntity positions to platform-agnostic LocationData.

**Key Features:**
- Static utility class (no instantiation)
- Converts `BlockEntity` + `ServerLevel` to `LocationData`
- Validates inputs with null checks
- Constructs world name from dimension location
- Extracts block coordinates from BlockEntity.getBlockPos()

**Implementation Notes:**
- Mirrors Bukkit's `LocationConverter` pattern for consistency
- Uses `ServerLevel.dimension().location().toString()` for world name

## Event Subscription Pattern

All handlers use NeoForge's `@Mod.EventBusSubscriber` annotation:

```java
@Mod.EventBusSubscriber(modid = "chestfind", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.DEDICATED_SERVER)
```

This provides:
- Automatic event subscription on mod load
- Static method event handlers (no instance needed)
- Server-side only filtering
- No manual registration required

## Dependency Injection Pattern

Handlers use static setters for dependency injection:

```java
public static void setIndexer(ContainerIndexer newIndexer)
public static void setVectorStorage(VectorStorage storage)
```

These must be called during plugin initialization (before events fire).

## Architecture Decisions

### 1. Location Handling
- Created `NeoForgeLocationConverter` to maintain platform abstraction
- Uses `LocationData` from common API for storage operations
- Dimension location format: `"minecraft:overworld"` or `"minecraft:the_nether"` etc.

### 2. Event Detection
- **ContainerCloseHandler:** Direct container events via `PlayerContainerEvent`
- **BlockBreakHandler:** Direct block events via `BlockEvent.BreakEvent`
- **ItemTransferHandler:** Indirect via `NeighborNotifyEvent` + capabilities (NeoForge doesn't have direct transfer events)

### 3. Snapshot Strategy
- ItemSnapshot stores: itemType, amount, NBT tag as string
- Comparison is deep (includes enchantments/attributes via NBT)
- Avoids unnecessary reindexing when no changes detected

## Known Limitations & TODOs

1. **ContainerIndexer API Gap**
   - Current `scheduleIndex(Location, ItemStack[])` requires Bukkit Location
   - Need to extend with NeoForge variant or create adapter
   - Affects both ContainerCloseHandler and ItemTransferHandler

2. **BlockEntity Extraction**
   - Reflection-based approach to find BlockEntity in AbstractContainerMenu
   - Some custom containers may not expose BlockEntity field
   - Alternative: Track containers via block position database

3. **NBT Comparison**
   - Currently uses `stack.getTag().toString()` for NBT comparison
   - May be inefficient for large NBT structures
   - Consider NBT.equals() or byte-level comparison

4. **Item Transfer Detection**
   - NeighborNotifyEvent is less precise than InventoryMoveItemEvent
   - May trigger reindex when no items actually transferred
   - Could use hopper tick events for more precision

## Integration Requirements

Before these handlers can be fully functional:

1. **Update ContainerIndexer** - Add method:
   ```java
   public void scheduleIndex(LocationData location, ItemStack[] items)
   ```
   Or create adapter layer in NeoForge module

2. **Update ChestFindPlugin** - Call during initialization:
   ```java
   ContainerCloseHandler.setIndexer(containerIndexer);
   BlockBreakHandler.setVectorStorage(vectorStorage);
   ItemTransferHandler.setIndexer(containerIndexer);
   ```

3. **Verify Event Registration** - Ensure NeoForge mod entry point is configured

## Testing Checklist

- [ ] Container open event fires and snapshots contents
- [ ] Container close event detects changes
- [ ] Breaking container removes from index
- [ ] Item transfers trigger reindexing (via hoppers)
- [ ] Null checks prevent NullPointerException
- [ ] Async deletion errors are handled gracefully
- [ ] Server-side only (no client-side execution)
- [ ] Performance: No excessive snapshots in memory

## References

- NeoForge 20.6.119 Event System
- Paper/Bukkit Listener patterns (for consistency)
- LocationData API from common module
- VectorStorage interface contract
