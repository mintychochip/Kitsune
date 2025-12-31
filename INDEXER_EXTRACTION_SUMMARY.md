# ContainerIndexer Extraction to Common Module

## Overview
Successfully extracted the ContainerIndexer class from the bukkit module to the common module, achieving clean separation of platform-agnostic core logic from Bukkit-specific code.

## Changes Made

### New Files Created

#### Common Module
1. **`common/src/main/java/org/aincraft/kitsune/indexing/ContainerIndexer.java`**
   - Core platform-agnostic container indexing service
   - Handles scheduling, embedding generation, and vector storage
   - Uses LocationData for all location tracking (no Bukkit dependencies)
   - Key methods:
     - `scheduleIndex(ContainerLocations, List<SerializedItem>)` - schedules indexing with debouncing
     - `performIndex(LocationData, List<SerializedItem>)` - performs embedding and storage (protected)
     - `shutdown()` - graceful shutdown with executor termination

2. **`common/src/main/java/org/aincraft/kitsune/indexing/ContainerScanner.java`**
   - Platform-specific interface for scanning containers in a radius
   - Abstraction for platform-specific block/container lookups
   - Enables future implementations for other platforms (Fabric, NeoForge, etc.)

#### Bukkit Module
1. **`bukkit/src/main/java/org/aincraft/kitsune/indexing/BukkitContainerIndexer.java`**
   - Bukkit-specific wrapper extending core ContainerIndexer
   - Provides platform-specific methods for Bukkit Location handling:
     - `scheduleIndex(Location, ItemStack[])` - Bukkit location variant
     - `reindexRadius(Location, int)` - radius scanning with block traversal
   - Handles ItemStack serialization and Location conversion

### Files Deleted
- `bukkit/src/main/java/org/aincraft/kitsune/indexing/ContainerIndexer.java` (old version with Bukkit dependencies)

### Files Modified

1. **`bukkit/src/main/java/org/aincraft/kitsune/BukkitKitsuneMain.java`**
   - Updated import to use `BukkitContainerIndexer` instead of old `ContainerIndexer`
   - Changed field type from `ContainerIndexer` to `BukkitContainerIndexer`
   - Updated instantiation to create `BukkitContainerIndexer` instance
   - Updated `getContainerIndexer()` return type to `BukkitContainerIndexer`

2. **`bukkit/src/main/java/org/aincraft/kitsune/listener/ContainerCloseListener.java`**
   - Updated import and field type to `BukkitContainerIndexer`
   - Updated constructor parameter type to `BukkitContainerIndexer`

3. **`bukkit/src/main/java/org/aincraft/kitsune/listener/HopperTransferListener.java`**
   - Updated import and field type to `BukkitContainerIndexer`
   - Updated constructor parameter type to `BukkitContainerIndexer`

## Architecture Overview

```
Common Module (Platform-Agnostic)
├── ContainerIndexer (core logic)
│   ├── scheduleIndex(ContainerLocations, List<SerializedItem>)
│   ├── performIndex(LocationData, List<SerializedItem>)
│   └── shutdown()
├── ContainerScanner (interface for radius scanning)
└── SerializedItem (existing data class)

Bukkit Module (Platform-Specific)
├── BukkitContainerIndexer extends ContainerIndexer
│   ├── scheduleIndex(Location, ItemStack[]) - Bukkit wrapper
│   ├── reindexRadius(Location, int) - block scanning
│   └── ItemSerializer.serializeItemsToChunks() integration
├── ItemSerializer (Bukkit-specific serialization)
└── BukkitKitsuneMain (instantiates BukkitContainerIndexer)
```

## Separation of Concerns

### Core Logic (Common Module)
- Location-independent indexing using LocationData
- Embedding generation and scheduling
- Vector storage interaction
- Debounce logic
- Thread pool management

### Platform-Specific Code (Bukkit Module)
- Bukkit Location to LocationData conversion
- ItemStack serialization
- Block scanning/traversal for radius reindexing
- Bukkit event listeners (ContainerCloseListener, HopperTransferListener)

## Benefits

1. **Reusability** - Core indexing logic can be reused by other platforms (Fabric, NeoForge, etc.)
2. **Maintainability** - Clear separation between platform-agnostic and platform-specific code
3. **Testability** - Core logic can be tested without Bukkit dependencies
4. **Extensibility** - Easy to add new platform implementations by extending ContainerIndexer
5. **SOLID Principles** - Follows Single Responsibility Principle and Dependency Inversion

## Dependency Resolution

- Bukkit module imports `ContainerIndexer` from `org.aincraft.kitsune.indexing`
- Due to module dependencies (bukkit depends on common), it resolves to the common version
- Bukkit-specific code uses `BukkitContainerIndexer` for Location-based methods
- Listeners receive `BukkitContainerIndexer` instances to access Bukkit-specific methods

## Next Steps

1. Compile and verify no errors
2. Run integration tests
3. Merge feature branch to master
4. Consider implementing ContainerScanner for other platforms if needed
