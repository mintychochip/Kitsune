# ContainerLocationResolver Interface Migration Analysis

## Task Summary
Evaluate feasibility of moving `ContainerLocationResolver` interface from the Bukkit module to the common module.

- **Current Location:** `bukkit/src/main/java/org/aincraft/kitsune/util/ContainerLocationResolver.java`
- **Proposed Location:** `common/src/main/java/org/aincraft/kitsune/util/ContainerLocationResolver.java`

## Finding: NOT FEASIBLE - Interface is Bukkit-Specific

### Reason
The `ContainerLocationResolver` interface directly uses Bukkit-specific types in its method signatures, making it inherently platform-dependent. Moving it to the common module would violate the principle of platform separation and create unnecessary Bukkit dependencies in the common module.

---

## Detailed Analysis

### 1. Interface Definition
**File:** `bukkit/src/main/java/org/aincraft/kitsune/util/ContainerLocationResolver.java`

```java
public interface ContainerLocationResolver {

    /**
     * Resolve locations for a container from its inventory holder.
     * @param holder the inventory holder
     * @return container locations, or null if not a valid container
     */
    @Nullable
    ContainerLocations resolveLocations(InventoryHolder holder);

    /**
     * Resolve locations for a container from a block.
     * @param block the block that may be part of a container
     * @return container locations, or null if not a container block
     */
    @Nullable
    ContainerLocations resolveLocations(Block block);
}
```

### 2. Bukkit-Specific Dependencies

The interface depends on the following Bukkit types:

| Type | Import | Classification |
|------|--------|-----------------|
| `InventoryHolder` | `org.bukkit.inventory.InventoryHolder` | Bukkit API |
| `Block` | `org.bukkit.block.Block` | Bukkit API |
| `ContainerLocations` | `org.aincraft.kitsune.api.ContainerLocations` | Platform-Agnostic |

**Critical Dependencies:** Both method parameters are Bukkit-specific types.

### 3. Implementation Analysis
**File:** `bukkit/src/main/java/org/aincraft/kitsune/util/BukkitContainerLocationResolver.java`

The implementation confirms the interface is Bukkit-specific:
- Uses `DoubleChest` (Bukkit type)
- Uses `Chest` (Bukkit type)
- Uses `Container` (Bukkit type)
- Uses `LocationConverter.toLocationData()` (Bukkit-specific conversion)

Example code snippet showing Bukkit-specific handling:
```java
if (holder instanceof DoubleChest doubleChest) {
    return resolveDoubleChestLocations(doubleChest);
}

if (holder instanceof Container container) {
    LocationData location = LocationConverter.toLocationData(container.getLocation());
    return ContainerLocations.single(location);
}
```

### 4. Usage in the Codebase

The interface is actively used in the Bukkit module's listener classes:

**ContainerCloseListener.java**
```java
public ContainerCloseListener(ContainerIndexer containerIndexer,
                             ContainerLocationResolver locationResolver) {
    this.containerIndexer = containerIndexer;
    this.locationResolver = locationResolver;
}
```

**HopperTransferListener.java**
```java
public class HopperTransferListener implements Listener {
    private final ContainerLocationResolver locationResolver;

    // Uses both methods of the interface
    ContainerLocations sourceLocations = locationResolver.resolveLocations(source.getHolder());
    // ...
}
```

Both usages are strictly within the Bukkit module context, dealing with Bukkit-specific inventory and event handling.

### 5. Return Type Analysis

**Return Type:** `ContainerLocations`

The return type is platform-agnostic:
- Located in: `common/src/main/java/org/aincraft/kitsune/api/ContainerLocations.java`
- Uses only platform-agnostic `LocationData` (world name + coordinates)
- No Bukkit dependencies

**This is the ONLY portable aspect of the interface.**

---

## Architectural Implications

### Current Architecture (Correct)
```
┌─────────────────────────┐
│   common module         │
│  (Platform-agnostic)    │
│                         │
│  - ContainerLocations   │
│  - LocationData         │
│  - APIs & Interfaces    │
└─────────────────────────┘
         ↑
         │ (depends on)
┌─────────────────────────┐
│   bukkit module         │
│  (Bukkit-specific)      │
│                         │
│  - ContainerLocationResolver ← interface
│  - BukkitContainerLocationResolver ← impl
│  - Listeners (using interface)
└─────────────────────────┘
```

### If Moved to Common (Problematic)
```
┌─────────────────────────┐
│   common module         │
│  (Now with Bukkit deps) │
│                         │
│  - ContainerLocations   │
│  - ContainerLocationResolver ← PROBLEM
│  - (Bukkit import here) │
└─────────────────────────┘
         ↑
         │ (introduces circular/unwanted dependency)
┌─────────────────────────┐
│   fabric module         │
│  (Would need Bukkit)    │
│                         │
│  - Dependency conflict  │
└─────────────────────────┘

┌─────────────────────────┐
│   neoforge module       │
│  (Would need Bukkit)    │
│                         │
│  - Dependency conflict  │
└─────────────────────────┘
```

---

## Alternative Approaches Considered

### Option 1: Create Platform-Agnostic Variant (Not Recommended)
Create a generic version without Bukkit types:
```java
// In common module
public interface ContainerLocationResolver<T> {
    @Nullable
    ContainerLocations resolveLocations(T holder);
}
```

**Verdict:** Adds unnecessary complexity and generics overhead. The current Bukkit-specific implementation is cleaner and more maintainable.

### Option 2: Extract Common Logic to Common Module
Move the location resolution logic and primary selection algorithm to a shared utility:

```java
// In common module
public class ContainerLocationSelector {
    public static LocationData selectPrimary(LocationData loc1, LocationData loc2) {
        if (loc1.blockX() != loc2.blockX()) {
            return loc1.blockX() < loc2.blockX() ? loc1 : loc2;
        }
        if (loc1.blockZ() != loc2.blockZ()) {
            return loc1.blockZ() < loc2.blockZ() ? loc1 : loc2;
        }
        return loc1.blockY() < loc2.blockY() ? loc1 : loc2;
    }
}
```

**Verdict:** Reasonable if code duplication is observed across modules. Currently not needed.

### Option 3: Keep as Platform-Specific (RECOMMENDED)
Keep the interface in the Bukkit module where it belongs.

**Verdict:** Best practice. Interface directly models Bukkit concepts (InventoryHolder, Block).

---

## Dependencies & Impact Assessment

### Direct Dependencies on ContainerLocationResolver
1. `BukkitContainerLocationResolver` (implementation)
2. `ContainerCloseListener` (usage)
3. `HopperTransferListener` (usage)

**All in bukkit module.** No cross-module dependencies exist.

### If Moved - What Breaks
1. **Common module gains Bukkit dependency** - violates separation of concerns
2. **Fabric/NeoForge modules would need to exclude or handle Bukkit classes**
3. **Build complexity increases** - potential classpath conflicts
4. **Future platform support becomes harder** - interface suggests "common" platform abstraction

---

## Recommendation

### DECISION: Keep ContainerLocationResolver in Bukkit Module

**Rationale:**
1. Interface is fundamentally Bukkit-specific (InventoryHolder, Block parameters)
2. No platform-agnostic version makes sense without gratuitous generics
3. All usages are within the Bukkit module
4. Moving violates the principle of platform separation
5. Common module should contain only platform-agnostic code
6. Current structure is clean and maintainable

**Action Required:**
- Do not move this interface
- This is a correct architectural decision as-is
- If code sharing is needed in future, extract utilities to common (not the interface)

---

## Code Locations

### Interface
- **Path:** `bukkit/src/main/java/org/aincraft/kitsune/util/ContainerLocationResolver.java`
- **Status:** Correctly placed

### Implementation
- **Path:** `bukkit/src/main/java/org/aincraft/kitsune/util/BukkitContainerLocationResolver.java`
- **Status:** Correctly placed

### Usages
- **ContainerCloseListener:** `bukkit/src/main/java/org/aincraft/kitsune/listener/ContainerCloseListener.java`
- **HopperTransferListener:** `bukkit/src/main/java/org/aincraft/kitsune/listener/HopperTransferListener.java`

---

## Summary Table

| Aspect | Finding |
|--------|---------|
| **Bukkit Dependencies** | Yes - InventoryHolder, Block in signatures |
| **Platform-Specific** | Yes - Models Bukkit inventory concepts |
| **Currently Used in Common** | No - only Bukkit module |
| **Cross-Module Usage** | No - all within Bukkit |
| **Feasible to Move** | No - violates architectural principles |
| **Recommended Action** | Keep in Bukkit module |
| **Quality of Current Location** | Good - correct placement |

---

**Analysis Date:** 2025-12-31
**Status:** COMPLETED
**Finding:** Interface is Bukkit-specific and should NOT be moved
