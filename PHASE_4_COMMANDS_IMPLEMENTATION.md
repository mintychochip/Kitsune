# Phase 4: Commands Implementation for NeoForge Module

## Overview
Implemented complete command handling system for the NeoForge ChestFind module using Brigadier command system and NeoForge event system.

## Files Created

### 1. ChestFindCommands.java
**Location:** `neoforge/src/main/java/org/aincraft/chestfind/command/ChestFindCommands.java`

Complete command implementation with the following features:

#### Commands Implemented
- `/find <query>` - Search containers using semantic search
- `/chestfind reload` - Reload configuration
- `/chestfind stats` - Display storage statistics
- `/chestfind reindex <radius>` - Reindex containers in specified radius
- `/chestfind purge` - Clear all vectors and cache
- `/chestfind` - Display help message

#### Key Features
- Uses Brigadier for command parsing and argument handling
- Async command execution via CompletableFuture
- Results synced back to main server thread using `source.getServer().execute()`
- Permission checking via `hasPermission(4)` for ops
- Provider mismatch detection and handling
- Search filtering by similarity threshold (78%)
- Distance calculation for search results

#### Method Structure
- `initialize()` - Sets up command dependencies (called from ChestFindMod)
- `setProviderMismatch()` - Updates provider status
- `onRegisterCommands()` - Public static event handler for command registration
- `executeFind()` - Search execution with async embedding/search
- `executeStats()` - Display indexing statistics
- `executeReindex()` - Schedule radius-based reindexing
- `executeReload()` - Configuration reload
- `executePurge()` - Clear vectors and cache
- `executeHelp()` - Display command help
- `hasPermission()` - Permission checking (NeoForge doesn't have built-in perms)
- `spawnHighlight()` - Placeholder for particle highlighting

## Files Modified

### 1. ChestFindMod.java
**Location:** `neoforge/src/main/java/org/aincraft/chestfind/ChestFindMod.java`

#### Changes Made

**Added Imports:**
- ChestFindCommands and related command dependencies
- ContainerIndexer, event handlers
- ProtectionProvider infrastructure
- ProviderMetadata for tracking embedding providers

**Added Fields:**
- `containerIndexer` - Manages debounced container indexing
- `protectionProvider` - Handles permission checks (if needed)
- `providerMetadata` - Tracks embedding provider changes
- `chestFindConfig` - Main configuration object
- `initialized` - Initialization state flag
- `providerMismatch` - Provider mismatch detection

**Updated onServerStarting():**
- Initialize ContainerIndexer
- Create ProviderMetadata tracker
- Call `checkProviderMismatch()` after service initialization
- Initialize event handlers with dependencies:
  - BlockBreakHandler.setVectorStorage()
  - ContainerCloseHandler.setDependencies()
  - ItemTransferHandler.setDependencies()
  - ChestFindCommands.initialize()
- Set initialized flag to true after completion

**Added checkProviderMismatch() Method:**
- Detects if embedding provider/model changed
- Logs warning if mismatch detected
- Disables indexing/search until purge is run

**Updated onServerStopping():**
- Added containerIndexer.shutdown() call

**Added CommandEvents Inner Class:**
- Handles RegisterCommandsEvent
- Routes to ChestFindCommands.onRegisterCommands()
- Uses @Mod.EventBusSubscriber with FORGE bus and DEDICATED_SERVER dist

## Files Updated

### 1. ContainerIndexer.java
**Location:** `neoforge/src/main/java/org/aincraft/chestfind/indexing/ContainerIndexer.java`

**Added Method:**
```java
public CompletableFuture<Integer> reindexRadius(LocationData centerLocation, int radius)
```

- Placeholder implementation returning 0
- TODO: Implement full radius scan for container discovery
- Requires chunk loading and block entity iteration

## Architecture Decisions

### Command Registration Pattern
- Uses NeoForge's `@Mod.EventBusSubscriber` for declarative event listening
- Static method registration avoids instantiation issues
- Separation of command logic from event handling

### Async Execution Model
- Commands execute async to avoid server thread blocking
- Results synced back via `source.getServer().execute()`
- CompletableFuture chaining for sequential operations

### Service Initialization
- Delayed command handler initialization until all services ready
- Initialize state check in each command
- Provider mismatch check prevents operations on mismatched providers

### Permission System
- NeoForge has no built-in permission API
- Uses command level checking (level 4 = ops)
- Can be extended to integrate with mods like LuckPerms

## Async Flow for Search Command

```
User /find <query>
  ↓
executeFind() → Check permissions, state, provider
  ↓
embeddingService.embed(query) [async]
  ↓
vectorStorage.search(embedding) [async]
  ↓
Results filtering (78% threshold)
  ↓
source.getServer().execute() → Sync back to main thread
  ↓
Send chat messages + spawn particles
  ↓
exceptionally() → Handle errors and send to player
```

## Testing Recommendations

1. **Command Registration**
   - Verify `/find` and `/chestfind` commands appear in command suggestions
   - Check permission requirement for admin commands

2. **Search Functionality**
   - Test basic search with various queries
   - Verify similarity filtering (should reject <78%)
   - Test async execution doesn't block server

3. **Admin Commands**
   - Test `/chestfind reload`
   - Test `/chestfind stats` output
   - Test `/chestfind purge` clears vectors

4. **Error Handling**
   - Test with uninitialized services
   - Test with provider mismatch
   - Test with invalid queries

5. **Reindex Command**
   - Currently returns 0 containers (placeholder)
   - Implement full radius scan in Phase 5

## Dependencies
- `embeddingService` - Query embedding and document embedding
- `vectorStorage` - Semantic search and indexing
- `containerIndexer` - Container discovery and indexing
- `config` - Configuration management
- `logger` - Logging

## Future Improvements

1. **Particle Effects**
   - Implement `spawnHighlight()` for visual feedback
   - Consider periodic glow/outline effects

2. **Radius Reindexing**
   - Implement full chunk scanning
   - Block entity discovery
   - Batch indexing

3. **Permission System**
   - Integrate LuckPerms support
   - Custom permission levels

4. **Query Enhancement**
   - Query expansion for synonyms
   - Pluralization handling
   - Search history

## Summary

Phase 4 successfully implements the command system for NeoForge, providing:
- Complete Brigadier command definitions
- Async execution with proper thread synchronization
- Service state management and error handling
- Integration with existing event handlers
- Foundation for future enhancements

All code follows SOLID principles with clear separation of concerns and async-first design.
