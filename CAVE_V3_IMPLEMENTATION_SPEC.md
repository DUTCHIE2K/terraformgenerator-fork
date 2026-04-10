# COMPOSITE_V3 Cave System Implementation Spec

Status: Draft for implementation

This document replaces the prior "contract-complete" claim with a concrete implementation contract tied to the current codebase.

Primary integration points in the current tree:

- `org.terraform.coregen.bukkit.TerraformGenerator`
- `org.terraform.coregen.ChunkCache`
- `org.terraform.cave.v2.*`
- `org.terraform.biome.cavepopulators.MasterCavePopulatorDistributor`
- `org.terraform.coregen.TerraformPopulator`

## 1. Scope

Included in V3:

- multi-field cave sampling
- deterministic entrance approval and shaping
- immutable base surface data plus mutable post-carve surface state
- snapshot metadata for decorators and tooling
- deterministic cross-chunk behavior under parallel generation

Explicitly excluded from V3:

- aquifers
- fluid simulation
- non-deterministic runtime probes

## 2. Current-to-Target Mapping

| Current code | V3 role | Notes |
| --- | --- | --- |
| `HeightMap.getPreciseHeight()` | base surface seed height | Keep as terrain input, not as post-carve truth |
| `ChunkCache.heightMapCache` | base surface cache | Immutable after prepass |
| `ChunkCache.transformedGroundCache` | mutable `currentTopSolidY` | Only mutable surface state |
| `TerraformGenerator.generateNoise()` | phase orchestrator | Owns prepass, approval, carve, cleanup, snapshot publish |
| `CaveSnapshotBuilder`, `CaveSnapshot`, `CaveColumn`, `CaveInterval` | replace with V3 snapshot types or extend compatibly | Current V2 objects are too thin |
| `CaveSnapshotStore.take()` | gameplay snapshot consume | Keep destructive semantics |
| `MasterCavePopulatorDistributor` | snapshot consumer | Must migrate to metadata-aware queries |

Important constraint:

- `TerraformGenerator.buildFilledCache()` must not be reused as the V3 base-surface prepass without refactor. It currently mixes terrain transform with cave carving, which violates the V3 immutable base surface contract.

## 3. Core Contracts

### 3.1 Base Surface Map

`BaseSurfaceMap` is an immutable padded window built before cave approval and before cave carving.

Window contract:

- Scope is a chunk plus `MAX_PADDING` on all sides.
- The same world seed and same chunk coordinates must produce the same `BaseSurfaceMap`.
- It must be safe to share across threads after construction.

Per-column contract:

```java
record BaseSurfaceColumn(
    short baseSurfaceY,
    SurfaceSafety safety,
    SurfaceTopState topState
) {}
```

Enums:

```java
enum SurfaceSafety { DRY, WET }
enum SurfaceTopState { AIR_EXPOSED, WATER_EXPOSED, SOLID_COVERED }
```

Derivation rules:

- `baseSurfaceY` is the post-transform terrain top before any cave carve or entrance shaping.
- `topState` is derived from the block directly above `baseSurfaceY` in the prepass result.
- `safety` is `WET` if `topState == WATER_EXPOSED`; otherwise `DRY`.

Implementation note:

- Extract this from the terrain fill plus biome transform portion of `TerraformGenerator.generateNoise()`.
- Do not read from mutable carve state while building it.

### 3.2 Mutable Surface State

`currentTopSolidY` is the only mutable surface-height state in V3.

Storage:

- Backed by `ChunkCache.transformedGroundCache`.

Rules:

- Initialized from `BaseSurfaceMap.baseSurfaceY`.
- May be updated during carve and cleanup.
- May be read by cleanup, interval rebuild, and final snapshot build.
- Must not be read by field samplers, entrance approval, or decorator placement heuristics.

### 3.3 Entrance Approval

Entrances are deterministic approvals, not opportunistic local decisions.

Identity:

```java
record EntranceAnchor(int cellX, int cellZ) {}
```

- Identity is derived from the deterministic entrance seed/search grid.
- Every chunk must derive the same anchor set for the same world.

Canonical ownership:

```java
static int ownerChunkX(EntranceAnchor anchor) { ... }
static int ownerChunkZ(EntranceAnchor anchor) { ... }
```

- Owner chunk is derived only from the anchor.
- Ownership must not depend on generation order.

Approval object:

```java
record EntranceApproval(
    EntranceAnchor anchor,
    int ownerChunkX,
    int ownerChunkZ,
    int mouthRawX,
    int mouthY,
    int mouthRawZ,
    int targetRawX,
    int targetY,
    int targetRawZ,
    float mouthRadius,
    float targetRadius,
    int effectPadding
) {}
```

Approval protocol:

- Approval is a pure function of world seed plus immutable padded `BaseSurfaceMap`.
- Any thread may request approval for an anchor.
- The approval cache may memoize results, but the function must be valid without prior generation of the owner chunk.
- Neighbor chunks may shape using an approved entrance but may not re-run validation with local mutable state.

Required API:

```java
@Nullable EntranceApproval getApprovedEntrance(TerraformWorld tw, EntranceAnchor anchor);
Collection<EntranceApproval> getApprovedEntrancesTouchingChunk(TerraformWorld tw, int chunkX, int chunkZ);
```

### 3.4 Field Sampling

Each field family returns a signed carve score in a shared domain where `>= 0` means carve-favorable after all applicable adjustments.

Required families for V3:

- `CHEESE`
- `SPAGHETTI`
- `ENTRANCE`

Runtime sample contract:

```java
enum CaveIntentType { NONE, CHEESE, SPAGHETTI, ENTRANCE }
enum CaveResolvedType { CHEESE, SPAGHETTI, ENTRANCE }

record CompositeVoxelSample(
    CaveIntentType intentType,
    CaveResolvedType resolvedType,
    float finalScore,
    float confidence
) {}
```

Reduction order per voxel:

1. Sample each family using immutable inputs only.
2. Apply family-local shaping rules.
3. Determine `intentType` from the highest family-local score.
4. Apply global penalties and blockers.
5. Determine `resolvedType` from the highest post-penalty score among carve-positive families.
6. Carve iff the highest post-penalty score is `>= 0`.

Family-local rules:

- Cheese is the primary volume field.
- Spaghetti must remain thin or gated and may not directly mutate cheese scores.
- Entrance shaping may only contribute local positive influence around approved entrance geometry.

Global penalties:

- surface clearance penalty
- sea-level penalty
- blocked entrance or connectivity penalty

Confidence:

- `confidence = clamp(abs(finalScore) / CONFIDENCE_SCALE, 0f, 1f)`
- `CONFIDENCE_SCALE` is a config-backed constant and must be the same for snapshot and debug paths.

Hot-path rules:

- no per-voxel object allocation
- no reads from mutable surface state
- no reads from partially rebuilt columns

### 3.5 Interval Metadata

V3 intervals must carry metadata needed by decorators and tooling.

```java
enum SurfaceConnectivity { YES, NO, UNKNOWN }

record CaveIntervalMetadata(
    CaveResolvedType resolvedType,
    float confidence,
    SurfaceConnectivity openToSurface
) {}

record CaveIntervalV3(
    short ceilingAirY,
    short floorSolidY,
    CaveIntervalMetadata metadata
) {}
```

Interval reduction rules:

- Intervals are produced by top-down scan over final carved columns after cleanup.
- For each interval, aggregate carved voxel classifications inside the interval.
- `resolvedType` is chosen by highest carved-voxel count.
- If counts tie, choose highest cumulative `finalScore`.
- If still tied, use the deterministic signature `(worldX, worldZ, intervalMinY, intervalMaxY)`.
- `confidence` is the arithmetic mean of voxel confidence values inside the interval.

`openToSurface` rules:

- `YES` if a conservative connectivity test finds a path from the interval to the top of the padded final carve window.
- `NO` if the conservative test fails.
- `UNKNOWN` is allowed only during intermediate rebuild work and must be materialized to `NO` before gameplay snapshot publish.

Connectivity implementation rule:

- Use a padded flood-fill or equivalent conservative graph walk over final non-solid cells.
- Read only final carve state plus approved entrance geometry.
- Do not use mutable in-progress state from another chunk.

### 3.6 Snapshot System

Gameplay and tooling snapshots have different retention semantics.

Gameplay snapshot:

```java
record CaveColumnV3(
    short baseSurfaceY,
    short topSolidY,
    List<CaveIntervalV3> intervals
) {}
```

```java
record CaveSnapshotV3(
    int chunkX,
    int chunkZ,
    CaveColumnV3[] columns
) {}
```

Store contract:

```java
void publishGameplay(TerraformWorld tw, int chunkX, int chunkZ, CaveSnapshotV3 snapshot);
@Nullable CaveSnapshotV3 takeGameplay(TerraformWorld tw, int chunkX, int chunkZ);
@Nullable CaveSnapshotV3 peekTooling(TerraformWorld tw, int chunkX, int chunkZ);
```

Rules:

- Gameplay snapshots must never be evicted before `takeGameplay()`.
- `takeGameplay()` is destructive.
- Tooling snapshots may live in a bounded LRU or TTL cache.
- Snapshot objects must be deeply immutable.

Compatibility path:

- Either extend `CaveInterval` and friends in place, or add V3 types plus an adapter for current consumers.
- Do not force `MasterCavePopulatorDistributor` to infer metadata from bare ceil/floor pairs once V3 is live.

## 4. Phase Contract

`TerraformGenerator.generateNoise()` remains the top-level orchestrator.

### Phase 0: Base Surface Prepass

Inputs:

- terrain height functions
- biome transform handlers
- no cave fields

Writes:

- immutable `BaseSurfaceMap`
- initial `currentTopSolidY`

Reads forbidden:

- entrance approvals
- mutable carve state

Exit criteria:

- every padded column has `baseSurfaceY`, `safety`, and `topState`

### Phase 1: Entrance Approval

Inputs:

- `BaseSurfaceMap`
- deterministic anchor grid

Writes:

- immutable `EntranceApproval` objects in approval cache

Reads forbidden:

- mutable carve state
- partially rebuilt chunk state

Exit criteria:

- every anchor touching the chunk window resolves to approved or rejected deterministically

### Phase 2: Composite Carve

Inputs:

- `BaseSurfaceMap`
- `EntranceApproval`s touching the chunk
- field samplers

Writes:

- block carve result
- per-voxel or per-column classification accumulators
- mutable `currentTopSolidY`

Reads forbidden:

- partial cleanup output from neighboring chunks

Exit criteria:

- chunk-local carve state is complete
- touched columns are tracked

### Phase 3: Cleanup and Interval Rebuild

Inputs:

- final carve state for the chunk
- touched-column set
- immutable approvals if needed

Writes:

- rebuilt intervals
- finalized `currentTopSolidY`
- finalized `openToSurface`

Exit criteria:

- all final columns are scan-consistent
- no gameplay interval carries `UNKNOWN`

### Phase 4: Snapshot Publish

Inputs:

- finalized columns and metadata

Writes:

- gameplay snapshot store
- optional tooling snapshot store

Exit criteria:

- one gameplay snapshot exists per generated chunk

### Phase 5: Decoration Consume

Owner:

- `TerraformPopulator.populate()`

Contract:

- consume gameplay snapshot destructively
- decorators read `resolvedType`, `confidence`, and `openToSurface`
- decoration must not mutate snapshot objects

## 5. Padding Contract

There is one global `MAX_PADDING` constant for V3.

Rules:

- Every subsystem declares `requiredPadding()`.
- `requiredPadding() <= MAX_PADDING` must be asserted in development builds.
- All padded immutable windows use the same `MAX_PADDING`.

Initial contributors to `MAX_PADDING`:

- entrance approval search radius
- entrance shaping radius
- connectivity flood-fill margin
- debug slice window

## 6. Acceptance Criteria

The implementation is not complete until all of the following pass.

Determinism:

- Same seed, same config, same chunk order yields byte-equivalent snapshot data.
- Generating chunk A then B must match generating B then A for all overlapping effects.

Seam safety:

- Entrance approvals and shaping must be identical on chunk borders regardless of which chunk requests them first.
- No border-only caves or missing border openings may appear.

Snapshot lifecycle:

- Every generated chunk publishes exactly one gameplay snapshot.
- `TerraformPopulator` consumes it exactly once.
- Post-gen repair paths may skip gameplay snapshots, but must not log runtime errors for expected repair flows.

Decorator compatibility:

- Existing cave decorators continue to work through an adapter until they are migrated to metadata-aware APIs.
- No decorator may need to rescan raw blocks to recover `resolvedType`.

Performance:

- Debug disabled path does not allocate per voxel.
- Field sampling remains primitive-only.
- Added entrance approval lookups are amortized or cached by anchor.

## 7. Implementation Trajectory

### Step 1: Introduce V3 data types

Add a new `org.terraform.cave.v3` package with:

- `BaseSurfaceMap`
- `EntranceApproval`
- `CaveIntervalMetadata`
- `CaveIntervalV3`
- `CaveColumnV3`
- `CaveSnapshotV3`
- `CaveSnapshotStoreV3`

Exit:

- types compile without changing generation behavior

### Step 2: Extract base surface prepass

Refactor terrain fill plus transform logic out of `TerraformGenerator.generateNoise()` into a cave-free prepass builder.

Exit:

- `BaseSurfaceMap` is produced without cave carving
- `ChunkCache.transformedGroundCache` is initialized from it

### Step 3: Implement entrance approval cache

Move entrance validation to a pure approval function over padded immutable surface data.

Exit:

- any chunk can query approved entrances deterministically
- neighbor chunks never perform local reinterpretation

### Step 4: Replace single-field carve with composite reducer

Promote current density sampling into family-aware composite sampling.

Exit:

- per-voxel classification yields `intentType`, `resolvedType`, `finalScore`, `confidence`

### Step 5: Rebuild intervals with metadata

Upgrade snapshot build and storage to publish metadata-rich intervals.

Exit:

- `TerraformPopulator` consumes V3 gameplay snapshots
- tooling can `peek`

### Step 6: Migrate decorators

Update `MasterCavePopulatorDistributor` and downstream cave populators to use metadata-aware cave queries.

Exit:

- no production cave decorator depends on bare ceil/floor pairs only

## 8. Non-Negotiable Rules

- Sampling uses immutable data only.
- Approval uses immutable data only.
- Cleanup may read mutable chunk-local carve state, but only after carve is finished for that chunk.
- Gameplay snapshots are deeply immutable.
- `UNKNOWN` connectivity never leaves the generation pipeline.
- Cross-chunk effects are derived from deterministic anchors plus immutable padded windows, not generation order.
