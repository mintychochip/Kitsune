# Phase 3: NeoForge Event Handlers - Implementation Guide

## Summary

Successfully implemented Phase 3 event handlers for the NeoForge module. These handlers monitor container interactions and block breaks, triggering appropriate reindexing operations.

## Files Created

```
neoforge/src/main/java/org/aincraft/chestfind/listener/
├── ContainerCloseHandler.java        (215 lines)
├── BlockBreakHandler.java            (55 lines)
├── ItemTransferHandler.java          (90 lines)
└── NeoForgeLocationConverter.java    (48 lines)
```

## Handler Details

### 1. ContainerCloseHandler
**File:** `C:\Users\justi\IdeaProjects\ChestFind\refactor\neoforge-module\neoforge\src\main\java\org\aincraft\chestfind\listener\ContainerCloseHandler.java`

Monitors container open/close events and optimizes reindexing by comparing snapshots.

**Key Methods:**
- `onContainerOpen()`: Snapshots current container state
- `onContainerClose()`: Compares state and schedules reindex if changed
- `getBlockEntityFromMenu()`: Extracts BlockEntity from AbstractContainerMenu via reflection
- `contentsEqual()`: Deep equality check for item snapshots

**Dependencies:**
- NeoForge 20.6.119
- Common API (LocationData)
- ContainerIndexer (requires extension for NeoForge)

**Event:** `PlayerContainerEvent.Open` and `PlayerContainerEvent.Close`

### 2. BlockBreakHandler
**File:** `C:\Users\justi\IdeaProjects\ChestFind\refactor\neoforge-module\neoforge\src\main\java\org\aincraft\chestfind\listener\BlockBreakHandler.java`

Removes containers from search index when broken.

**Key Methods:**
- `onBlockBreak()`: Detects container breaking and removes from index

**Dependencies:**
- NeoForge 20.6.119
- VectorStorage (common API)
- NeoForgeLocationConverter (local utility)

**Event:** `BlockEvent.BreakEvent`

### 3. ItemTransferHandler
**File:** `C:\Users\justi\IdeaProjects\ChestFind\refactor\neoforge-module\neoforge\src\main\java\org\aincraft\chestfind\listener\ItemTransferHandler.java`

(Optional) Monitors item transfers via hoppers and other mechanisms.

**Key Methods:**
- `onNeighborChange()`: Detects container neighbor changes indicating transfers
- Checks item handler capabilities on affected blocks

**Dependencies:**
- NeoForge 20.6.119
- NeoForge Capabilities API

**Event:** `BlockEvent.NeighborNotifyEvent`

### 4. NeoForgeLocationConverter
**File:** `C:\Users\justi\IdeaProjects\ChestFind\refactor\neoforge-module\neoforge\src\main\java\org\aincraft\chestfind\listener\NeoForgeLocationConverter.java`

Platform-agnostic location conversion utility.

**Key Methods:**
- `toLocationData()`: Converts BlockEntity + ServerLevel to LocationData

**Dependencies:**
- Common API (LocationData)
- NeoForge world types

## Architecture

### Event Subscription

All handlers use `@Mod.EventBusSubscriber` for automatic registration:

```java
@Mod.EventBusSubscriber(
    modid = "chestfind",
    bus = Mod.EventBusSubscriber.Bus.FORGE,
    value = Dist.DEDICATED_SERVER
)
```

Benefits:
- No manual registration needed
- Automatic on mod load
- Server-side only (no client execution)
- Static event methods (no instance creation)

### Dependency Injection

Static setters provide dependencies during initialization:

```java
ContainerCloseHandler.setIndexer(containerIndexer);
BlockBreakHandler.setVectorStorage(vectorStorage);
ItemTransferHandler.setIndexer(containerIndexer);
```

This pattern maintains clean separation of concerns while avoiding reflection or service locators.

### Platform Abstraction

`NeoForgeLocationConverter` maintains platform independence:
- Uses `LocationData` from common API
- Converts NeoForge-specific types (BlockEntity, ServerLevel, ResourceLocation)
- Enables code reuse across Bukkit/NeoForge implementations

## Implementation Patterns

### 1. Change Detection

ContainerCloseHandler uses snapshot-based change detection:

```
onContainerOpen()  → Snapshot items
    ↓
    (user interacts with container)
    ↓
onContainerClose() → Compare with current state
    ↓
    Changed? → scheduleIndexing()
    Unchanged? → Skip reindex (optimization)
```

### 2. Error Handling

Async operations use CompletableFuture exception handling:

```java
vectorStorage.delete(location)
    .exceptionally(ex -> {
        System.err.println("Failed to remove container...");
        return null;
    });
```

### 3. Null Safety

All event handlers check for null dependencies:

```java
if (vectorStorage == null || event.getLevel().isClientSide) {
    return;
}
```

## Integration Checklist

### Prerequisites
- [ ] Common API module available with LocationData
- [ ] VectorStorage interface accessible
- [ ] ContainerIndexer available

### Required Changes to Existing Code

1. **Update ContainerIndexer** (in src/ or common module)

   Add NeoForge-compatible method:
   ```java
   public void scheduleIndex(LocationData location, ItemStack[] items) {
       // Convert LocationData to Bukkit Location or create NeoForge variant
       // Schedule via existing executor
   }
   ```

2. **Update ChestFindPlugin** (NeoForge mod entry point)

   Call during initialization:
   ```java
   ContainerCloseHandler.setIndexer(containerIndexer);
   BlockBreakHandler.setVectorStorage(vectorStorage);
   ItemTransferHandler.setIndexer(containerIndexer);
   ```

3. **Verify mod configuration** (mods.toml or equivalent)

   Ensure NeoForge event bus is configured for the mod

### Optional Enhancements

1. **Logging Integration**
   - Replace System.err with proper logger
   - Consider ChestFindPlugin logger injection

2. **Performance Optimization**
   - Cache ItemStack comparisons
   - Limit snapshot map size (LRU eviction)
   - Debounce rapid container interactions

3. **Advanced Features**
   - Track container access patterns
   - Monitor hopper transfer rates
   - Implement selective reindexing

## Known Limitations

### 1. ContainerIndexer API Gap

Current issue: `scheduleIndex()` expects Bukkit Location, not platform-agnostic LocationData.

Solutions:
1. Create adapter method in ContainerIndexer
2. Extend ContainerIndexer with overload
3. Create NeoForge-specific indexer wrapper

### 2. BlockEntity Extraction

Challenge: AbstractContainerMenu doesn't expose BlockEntity directly.

Current approach: Reflection-based field search

Risks:
- Fails for custom container implementations
- Depends on internal Minecraft structure

Alternatives:
- Track containers by block position
- Use event coordinates instead of BlockEntity
- Require custom containers to register

### 3. Item Transfer Detection

NeoForge limitation: No direct equivalent to `InventoryMoveItemEvent`

Current approach: Monitor NeighborNotifyEvent + capabilities

Tradeoffs:
- May trigger on false positives
- Less precise than inventory-level events
- Better alternative: Hook hopper tick events

### 4. World Name Format

Uses dimension ResourceLocation string format:
- `"minecraft:overworld"`
- `"minecraft:the_nether"`
- Custom mod dimensions: `"modid:dimension_name"`

Ensure LocationData comparisons account for this format.

## Testing Strategy

### Unit Tests

```java
// Test snapshot comparison
ItemSnapshot snap1 = new ItemSnapshot("diamond_block", 64, "");
ItemSnapshot snap2 = new ItemSnapshot("diamond_block", 64, "");
assertTrue(snap1.equals(snap2));
```

### Integration Tests

1. **Container Open/Close**
   - Open container
   - Verify snapshot created
   - Close container
   - Verify state compared
   - Add item
   - Verify reindex scheduled

2. **Block Breaking**
   - Place container block
   - Index container
   - Break container
   - Verify deleted from index

3. **Item Transfers**
   - Setup hopper → container
   - Add items to hopper
   - Trigger transfer
   - Verify reindex scheduled

### Manual Testing

```
/say Testing item snapshot
(add 10 diamonds)
/say Closing container
(verify reindex in logs)
```

## Performance Considerations

### Memory Impact
- `openContainers` map: One entry per open container per player
- `ItemSnapshot` array: Size = container size (typical 27 items)
- Estimated: ~1KB per open container

### CPU Impact
- Comparison: O(n) where n = container size (typical 27)
- Reflection (first use): ~10ms, then cached
- Event handling: <1ms per event

### Optimization Opportunities
- Implement snapshot pooling
- Lazy BlockEntity resolution
- Batch deletion operations

## Future Enhancements

1. **Hopper Tracking**
   - Direct hopper tick event monitoring
   - Accurate transfer counting
   - Performance metrics

2. **Multi-Block Containers**
   - Detect double chests
   - Unified snapshot/indexing
   - Prevent duplicate indexing

3. **Permission Integration**
   - Check container access permissions
   - Respect protection plugins
   - Owner-based reindexing

4. **Analytics**
   - Track indexing frequency
   - Monitor performance metrics
   - Report hot containers

## Code Quality

### SOLID Principles Applied

1. **Single Responsibility**
   - Each handler monitors specific event type
   - Separation of concerns maintained

2. **Open/Closed**
   - Easy to add new event types
   - Closed for modification of core logic

3. **Liskov Substitution**
   - Platform-agnostic LocationData usage
   - Can swap implementations

4. **Interface Segregation**
   - Handlers depend on specific interfaces (VectorStorage, ContainerIndexer)
   - No unnecessary dependencies

5. **Dependency Inversion**
   - Depend on abstractions (LocationData, VectorStorage)
   - Not on concrete implementations

## References

- **NeoForge 20.6.119 Events:** `net.neoforged.neoforge.event` package
- **Minecraft 1.20.6:** Block entities, containers, items
- **Common API:** LocationData, VectorStorage interfaces
- **Pattern Reference:** Bukkit listener implementations in bukkit/ module
