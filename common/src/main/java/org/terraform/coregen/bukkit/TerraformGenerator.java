package org.terraform.coregen.bukkit;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.terraform.biome.BiomeBank;
import org.terraform.biome.BiomeHandler;
import org.terraform.cave.v3.BaseSurfaceChunkV3;
import org.terraform.cave.v3.CaveV3Profiler;
import org.terraform.biome.cavepopulators.MasterCavePopulatorDistributor;
import org.terraform.cave.v3.BaseSurfaceMap;
import org.terraform.cave.v3.BaseSurfaceMapStoreV3;
import org.terraform.cave.v3.CaveIntervalMetadata;
import org.terraform.cave.v3.CaveIntervalV3;
import org.terraform.cave.v3.CaveSnapshotStoreV3;
import org.terraform.cave.v3.CaveSnapshotV3;
import org.terraform.cave.v3.CaveSnapshotV3Builder;
import org.terraform.cave.v3.SurfaceConnectivity;
import org.terraform.cave.v3.generation.CompositeCaveColumnSampler;
import org.terraform.cave.v3.generation.CompositeCaveSampler;
import org.terraform.cave.v3.generation.CompositeCaveGeneratorMode;
import org.terraform.coregen.ChunkCache;
import org.terraform.coregen.HeightMap;
import org.terraform.coregen.TerraformPopulator;
import org.terraform.data.CoordPair;
import org.terraform.data.DudChunkData;
import org.terraform.data.SimpleChunkLocation;
import org.terraform.data.TWCoordPair;
import org.terraform.data.TerraformWorld;
import org.terraform.main.TerraformGeneratorPlugin;
import org.terraform.main.config.TConfig;
import org.terraform.utils.GenUtils;
import org.terraform.utils.blockdata.CommonMat;
import org.terraform.utils.datastructs.ConcurrentLRUCache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TerraformGenerator extends ChunkGenerator {
    public static final List<SimpleChunkLocation> preWorldInitGen = new ArrayList<>();
    private static final int MAX_CONCURRENT_FULL_COLUMN_PREFILLS = 2;
    private static final int[] SURFACE_PREFILL_NEIGHBOR_X = {1, -1, 0, 0};
    private static final int[] SURFACE_PREFILL_NEIGHBOR_Z = {0, 0, 1, -1};
    private static final ConcurrentHashMap<TerraformWorld, ChunkCoord> LAST_COMPLETED_GENERATION = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<TerraformWorld, ChunkCoord> PREFERRED_FULL_COLUMN_PREFILL = new ConcurrentHashMap<>();
    private static final AtomicInteger FULL_COLUMN_PREFILLS_IN_FLIGHT = new AtomicInteger();
    // Explode if a read is attempted. Transform Handlers are not supposed to read.
    private static final DudChunkData DUD = new DudChunkData();
    //This cache is NOT fucking used correctly.
    // By right, nobody's supposed to be writing to it at the same time, but in
    // practice, that doesn't matter
    public static ConcurrentLRUCache<TWCoordPair, ChunkCache> CHUNK_CACHE;
    public static int seaLevel = 62;
    private record CarvedInterval(short ceilingAirY, short floorSolidY) {}
    private record ChunkCoord(int x, int z) {}
    private record PrefilledDensityColumn(short transformedHeight,
                                         short[] carvedAirRuns,
                                         @NotNull List<CaveIntervalV3> snapshotIntervals) {}

    public static void updateSeaLevelFromConfig() {
        seaLevel = TConfig.c.HEIGHT_MAP_SEA_LEVEL;
    }

    /**
     * @param x chunk X
     * @param z chunk Z
     */
    public static @NotNull ChunkCache getCache(TerraformWorld tw, int x, int z) {
        // Note how it DOES NOT initInternalCache here
        // Cos this is the damn key
        // Don't fucking run calculations here
        return CHUNK_CACHE.get(new TWCoordPair(tw, x,z));
    }

    // This method ONLY fills transformedHeight with meaningful values,
    // and writes nothing.
    public static void buildFilledCache(@NotNull TerraformWorld tw, int chunkX, int chunkZ, @NotNull ChunkCache cache) {
        synchronized (cache) {
            CompositeCaveGeneratorMode caveMode = CompositeCaveGeneratorMode.fromConfig(TConfig.c.CAVES_GENERATOR_MODE);
            if (cache.areTransformedHeightsFilled()) {
                if (caveMode == CompositeCaveGeneratorMode.COMPOSITE_V3) {
                    CaveV3Profiler.recordEvent("cave-v3.build-filled-cache.skip-already-filled");
                }
                return;
            }

            // TerraformGeneratorPlugin.watchdogSuppressant.tickWatchdog(); don't unnecessarily tick this shit

            if (caveMode == CompositeCaveGeneratorMode.COMPOSITE_V3) {
                try (CaveV3Profiler.Scope ignored = CaveV3Profiler.start("cave-v3.build-filled-cache")) {
                    boolean cavesEnabled = TConfig.areCavesEnabled();
                    BaseSurfaceChunkV3 surfaceChunk = BaseSurfaceMapStoreV3.getBaseSurfaceChunk(tw, chunkX, chunkZ);
                    CompositeCaveSampler compositeSampler = cavesEnabled ? createCompositeV3Sampler(tw) : null;
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            int rawX = chunkX * 16 + x;
                            int rawZ = chunkZ * 16 + z;
                            int baseSurfaceY = surfaceChunk.getBaseSurfaceY(x, z);
                            CompositeCaveColumnSampler columnSampler = cavesEnabled
                                                                      ? compositeSampler.createColumnSampler(
                                                                              rawX,
                                                                              rawZ,
                                                                              baseSurfaceY,
                                                                              cache
                                                                      )
                                                                      : null;
                            short transformedHeight = (short) baseSurfaceY;
                            if (cavesEnabled) {
                                for (int y = baseSurfaceY; y >= TerraformGeneratorPlugin.injector.getMinY(); y--) {
                                    if (columnSampler.canCarve(y)) {
                                        transformedHeight = (short) (y - 1);
                                    }
                                    else {
                                        CaveV3Profiler.recordEvents(
                                                "cave-v3.build-filled-cache.voxels-skipped-after-cutoff",
                                                y - TerraformGeneratorPlugin.injector.getMinY() + 1L
                                        );
                                        break;
                                    }
                                }
                            }
                            cache.writeTransformedHeight(x, z, transformedHeight);
                        }
                    }
                    cache.markTransformedHeightsFilled();
                }
                return;
            }

            seedChunkSurfaceHeights(tw, chunkX, chunkZ, cache);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int rawX = chunkX * 16 + x;
                    int rawZ = chunkZ * 16 + z;
                    double preciseHeight = HeightMap.getPreciseHeight(tw, rawX, rawZ);
                    // Carve caves
                    float seaLevelFilter = tw.noiseCaveRegistry.getGenerateCarveSeaFilter(rawX, rawZ, preciseHeight, cache);
                    short transformedHeight = cache.getTransformedHeight(x, z);
                    for (int y = (int) preciseHeight; y >= TerraformGeneratorPlugin.injector.getMinY(); y--)
                    // Set stone if a cave CANNOT be carved here
                    // Check canNoiseCarve because carver caves may expose
                    // noise caves below, which contribute to height changes
                    {
                        if (tw.noiseCaveRegistry.canGenerateCarve(rawX, y, rawZ, preciseHeight, seaLevelFilter)
                            || tw.noiseCaveRegistry.canNoiseCarve(rawX, y, rawZ, preciseHeight, cache))
                        {
                            transformedHeight = (short) (y - 1);
                        }
                        else {
                            break;
                        }
                    }
                    cache.writeTransformedHeight(x, z, transformedHeight);
                }
            }
            applyChunkTerrainTransforms(tw, chunkX, chunkZ, cache, DUD);
            cache.markTransformedHeightsFilled();
        }
    }

    private static void scheduleCompositeV3NeighborPrefill(@NotNull TerraformWorld tw,
                                                           int chunkX,
                                                           int chunkZ,
                                                           ChunkCoord previousChunk)
    {
        if (!TConfig.areCavesEnabled()) {
            return;
        }
        if (CompositeCaveGeneratorMode.fromConfig(TConfig.c.CAVES_GENERATOR_MODE) != CompositeCaveGeneratorMode.COMPOSITE_V3) {
            return;
        }

        // The current best-performing exact strategy is mixed prewarm:
        // one predicted next chunk gets the expensive full-column cave prefill,
        // while the cardinal ring only gets cheaper surface data warming.
        ChunkCoord fullColumnTarget = getFullColumnPrefillTarget(chunkX, chunkZ, previousChunk);
        if (fullColumnTarget != null) {
            PREFERRED_FULL_COLUMN_PREFILL.put(tw, fullColumnTarget);
            scheduleNeighborPrefill(
                    tw,
                    fullColumnTarget.x(),
                    fullColumnTarget.z(),
                    true
            );
        }
        else {
            PREFERRED_FULL_COLUMN_PREFILL.remove(tw);
        }

        for (int i = 0; i < SURFACE_PREFILL_NEIGHBOR_X.length; i++) {
            int neighborChunkX = chunkX + SURFACE_PREFILL_NEIGHBOR_X[i];
            int neighborChunkZ = chunkZ + SURFACE_PREFILL_NEIGHBOR_Z[i];
            if (fullColumnTarget != null
                && fullColumnTarget.x() == neighborChunkX
                && fullColumnTarget.z() == neighborChunkZ)
            {
                continue;
            }
            scheduleNeighborPrefill(
                    tw,
                    neighborChunkX,
                    neighborChunkZ,
                    false
            );
        }
    }

    private static void scheduleNeighborPrefill(@NotNull TerraformWorld tw,
                                                int neighborChunkX,
                                                int neighborChunkZ,
                                                boolean shouldPrefillFullColumn)
    {
        ChunkCache neighborCache = getCache(tw, neighborChunkX, neighborChunkZ);
        if (neighborCache.areTransformedHeightsFilled()) {
            CaveV3Profiler.recordEvent("cave-v3.prefill-neighbor.skip-filled");
            return;
        }
        if (!neighborCache.tryMarkPrefillScheduled()) {
            CaveV3Profiler.recordEvent("cave-v3.prefill-neighbor.skip-scheduled");
            return;
        }

        if (shouldPrefillFullColumn) {
            scheduleNeighborFullColumnPrefill(tw, neighborChunkX, neighborChunkZ, neighborCache);
        }
        else {
            scheduleNeighborSurfacePrefill(tw, neighborChunkX, neighborChunkZ, neighborCache);
        }
    }

    private static ChunkCoord getFullColumnPrefillTarget(int chunkX, int chunkZ, ChunkCoord previousChunk) {
        if (previousChunk == null) {
            return null;
        }

        int directionX = Integer.compare(chunkX, previousChunk.x());
        int directionZ = Integer.compare(chunkZ, previousChunk.z());
        if (directionX == 0 && directionZ == 0) {
            return null;
        }

        return new ChunkCoord(chunkX + directionX, chunkZ + directionZ);
    }

    private static void scheduleNeighborFullColumnPrefill(@NotNull TerraformWorld tw,
                                                          int neighborChunkX,
                                                          int neighborChunkZ,
                                                          @NotNull ChunkCache neighborCache)
    {
        // Do not let heavy exact prefills fan out without bound. When the heavy lane is saturated,
        // degrade gracefully to the lighter surface prewarm instead of starting more full cave jobs.
        if (!tryAcquireFullColumnPrefillSlot()) {
            CaveV3Profiler.recordEvent("cave-v3.prefill-neighbor.full-column-downgraded-cap");
            scheduleNeighborSurfacePrefill(tw, neighborChunkX, neighborChunkZ, neighborCache);
            return;
        }

        CaveV3Profiler.recordEvent("cave-v3.prefill-neighbor.full-column-scheduled");
        TerraformGeneratorPlugin.taskScheduler.execAsync(() -> {
            try {
                if (neighborCache.areTransformedHeightsFilled()) {
                    CaveV3Profiler.recordEvent("cave-v3.prefill-neighbor.skip-filled-late");
                    return;
                }
                if (!isPreferredFullColumnTarget(tw, neighborChunkX, neighborChunkZ)) {
                    CaveV3Profiler.recordEvent("cave-v3.prefill-neighbor.full-column-downgraded-stale");
                    completeSurfacePrefill(tw, neighborChunkX, neighborChunkZ, neighborCache);
                    return;
                }
                buildCompositeV3ChunkPrefill(tw, neighborChunkX, neighborChunkZ, neighborCache);
                CaveV3Profiler.recordEvent("cave-v3.prefill-neighbor.full-column-completed");
            }
            finally {
                releaseFullColumnPrefillSlot();
                neighborCache.clearPrefillScheduled();
            }
        });
    }

    private static void scheduleNeighborSurfacePrefill(@NotNull TerraformWorld tw,
                                                       int neighborChunkX,
                                                       int neighborChunkZ,
                                                       @NotNull ChunkCache neighborCache)
    {
        if (BaseSurfaceMapStoreV3.hasBaseSurfaceChunk(tw, neighborChunkX, neighborChunkZ)) {
            CaveV3Profiler.recordEvent("cave-v3.prefill-neighbor.skip-surface-cached");
            neighborCache.clearPrefillScheduled();
            return;
        }

        CaveV3Profiler.recordEvent("cave-v3.prefill-neighbor.surface-scheduled");
        TerraformGeneratorPlugin.taskScheduler.execAsync(() -> {
            try {
                completeSurfacePrefill(tw, neighborChunkX, neighborChunkZ, neighborCache);
            }
            finally {
                neighborCache.clearPrefillScheduled();
            }
        });
    }

    private static void completeSurfacePrefill(@NotNull TerraformWorld tw,
                                               int neighborChunkX,
                                               int neighborChunkZ,
                                               @NotNull ChunkCache neighborCache)
    {
        if (neighborCache.areTransformedHeightsFilled()) {
            CaveV3Profiler.recordEvent("cave-v3.prefill-neighbor.skip-filled-late");
            return;
        }
        if (BaseSurfaceMapStoreV3.hasBaseSurfaceChunk(tw, neighborChunkX, neighborChunkZ)) {
            CaveV3Profiler.recordEvent("cave-v3.prefill-neighbor.skip-surface-cached");
            return;
        }
        BaseSurfaceMapStoreV3.getBaseSurfaceChunk(tw, neighborChunkX, neighborChunkZ);
        CaveV3Profiler.recordEvent("cave-v3.prefill-neighbor.surface-completed");
    }

    private static boolean isPreferredFullColumnTarget(@NotNull TerraformWorld tw, int chunkX, int chunkZ) {
        ChunkCoord preferredTarget = PREFERRED_FULL_COLUMN_PREFILL.get(tw);
        return preferredTarget != null && preferredTarget.x() == chunkX && preferredTarget.z() == chunkZ;
    }

    private static boolean tryAcquireFullColumnPrefillSlot() {
        while (true) {
            int inFlight = FULL_COLUMN_PREFILLS_IN_FLIGHT.get();
            if (inFlight >= MAX_CONCURRENT_FULL_COLUMN_PREFILLS) {
                return false;
            }
            if (FULL_COLUMN_PREFILLS_IN_FLIGHT.compareAndSet(inFlight, inFlight + 1)) {
                return true;
            }
        }
    }

    private static void releaseFullColumnPrefillSlot() {
        FULL_COLUMN_PREFILLS_IN_FLIGHT.decrementAndGet();
    }

    private static void buildCompositeV3ChunkPrefill(@NotNull TerraformWorld tw,
                                                     int chunkX,
                                                     int chunkZ,
                                                     @NotNull ChunkCache cache)
    {
        synchronized (cache) {
            if (cache.hasCompositeV3ChunkPrefill()) {
                CaveV3Profiler.recordEvent("cave-v3.build-full-prefill.skip-already-filled");
                return;
            }

            try (CaveV3Profiler.Scope ignored = CaveV3Profiler.start("cave-v3.build-full-prefill")) {
                BaseSurfaceChunkV3 surfaceChunk = BaseSurfaceMapStoreV3.getBaseSurfaceChunk(tw, chunkX, chunkZ);
                CompositeCaveSampler compositeSampler = createCompositeV3Sampler(tw);
                ChunkCache.CompositeV3ColumnPrefill[] columns = new ChunkCache.CompositeV3ColumnPrefill[256];

                // This is an exact accelerator, not an approximation. We run the same column sampler that
                // live generation would use, cache the transformed top solid plus carved air runs, and later
                // replay those runs directly on the generation-critical path.
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int rawX = chunkX * 16 + x;
                        int rawZ = chunkZ * 16 + z;
                        int baseSurfaceY = surfaceChunk.getBaseSurfaceY(x, z);
                        CompositeCaveColumnSampler columnSampler = compositeSampler.createColumnSampler(
                                rawX,
                                rawZ,
                                baseSurfaceY,
                                cache
                        );
                        PrefilledDensityColumn prefilledColumn = scanDensityFieldColumnForPrefill(
                                columnSampler,
                                baseSurfaceY
                        );
                        columns[getColumnIndex(x, z)] = new ChunkCache.CompositeV3ColumnPrefill(
                                (short) baseSurfaceY,
                                prefilledColumn.transformedHeight(),
                                prefilledColumn.carvedAirRuns(),
                                prefilledColumn.snapshotIntervals()
                        );
                        cache.writeTransformedHeight(x, z, prefilledColumn.transformedHeight());
                    }
                }

                cache.cacheCompositeV3ChunkPrefill(new ChunkCache.CompositeV3ChunkPrefill(columns));
                cache.markTransformedHeightsFilled();
            }
        }
    }

    private static @NotNull PrefilledDensityColumn scanDensityFieldColumnForPrefill(@NotNull CompositeCaveColumnSampler columnSampler,
                                                                                     int surfaceY)
    {
        int minY = TerraformGeneratorPlugin.injector.getMinY();
        int invalHeight = minY - 1;
        int activeCaveAirTop = invalHeight;
        boolean mustUpdateHeight = true;
        short transformedHeight = (short) surfaceY;
        List<Short> rawAirRuns = new ArrayList<>();

        for (int y = surfaceY; y >= minY; y--) {
            boolean isCarved = columnSampler.canCarve(y);
            if (isCarved) {
                if (activeCaveAirTop == invalHeight) {
                    activeCaveAirTop = y;
                }
                if (y > minY && mustUpdateHeight) {
                    transformedHeight = (short) (y - 1);
                }
            }
            else {
                if (activeCaveAirTop != invalHeight) {
                    rawAirRuns.add((short) (y + 1));
                    rawAirRuns.add((short) activeCaveAirTop);
                    activeCaveAirTop = invalHeight;
                }
                if (y > minY) {
                    mustUpdateHeight = false;
                }
            }
        }

        if (activeCaveAirTop != invalHeight) {
            rawAirRuns.add((short) minY);
            rawAirRuns.add((short) activeCaveAirTop);
        }

        short[] carvedAirRuns = toShortArray(rawAirRuns);
        List<CarvedInterval> carvedIntervals = toCaveIntervalsFromAirRuns(carvedAirRuns, transformedHeight, minY);
        return new PrefilledDensityColumn(
                transformedHeight,
                carvedAirRuns,
                toV3Intervals(transformedHeight, carvedIntervals)
        );
    }

    private static short @NotNull [] toShortArray(@NotNull List<Short> values) {
        short[] result = new short[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private static void seedChunkSurfaceHeights(@NotNull TerraformWorld tw,
                                                int chunkX,
                                                int chunkZ,
                                                @NotNull ChunkCache cache)
    {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int rawX = chunkX * 16 + x;
                int rawZ = chunkZ * 16 + z;
                cache.writeTransformedHeight(x, z, (short) HeightMap.getPreciseHeight(tw, rawX, rawZ));
            }
        }
    }

    private static void applyChunkTerrainTransforms(@NotNull TerraformWorld tw,
                                                    int chunkX,
                                                    int chunkZ,
                                                    @NotNull ChunkCache cache,
                                                    @NotNull ChunkData chunkData)
    {
        Random random = tw.getHashedRand(chunkX, chunkZ, 31278);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int rawX = chunkX * 16 + x;
                int rawZ = chunkZ * 16 + z;
                double preciseHeight = HeightMap.getPreciseHeight(tw, rawX, rawZ);
                BiomeBank bank = tw.getBiomeBank(rawX, (int) preciseHeight, rawZ);
                BiomeHandler transformHandler = bank.getHandler().getTransformHandler();
                if (transformHandler != null) {
                    transformHandler.transformTerrain(cache, tw, random, chunkData, x, z, chunkX, chunkZ);
                }
            }
        }
    }

    @Override
    public boolean isParallelCapable() {
        return true;
    }

    /**
     * EVERY STAGE IS DONE INSIDE HERE FOR A REASON.
     * The cache MAY invalidate between stages, making it infeasible to even
     * bother splitting it up.
     * <br>
     * It was originally split into 4 phases for readability's sake,
     * but there's really no point - its faster to iterate x/z ONCE here,
     * and avoid all the cache and other nonsense issues.
     * <br>
     * It's that or throw ChunkCaches into ConcurrentHashMaps and
     * then flush it into the CHUNK_CACHE after generation, which is
     * dumb. That's dumb.
     */
    public void generateNoise(@NotNull WorldInfo worldInfo,
                              @NotNull Random dontCareRandom,
                              int chunkX,
                              int chunkZ,
                              @NotNull ChunkData chunkData)
    {
        TerraformGeneratorPlugin.watchdogSuppressant.tickWatchdog();

        TerraformWorld tw = TerraformWorld.get(worldInfo.getName(), worldInfo.getSeed());
        ChunkCache cache = getCache(tw, chunkX, chunkZ);
        CompositeCaveGeneratorMode caveMode = CompositeCaveGeneratorMode.fromConfig(TConfig.c.CAVES_GENERATOR_MODE);
        synchronized (cache) {
            if (caveMode == CompositeCaveGeneratorMode.COMPOSITE_V3) {
                generateCompositeV3Noise(tw, cache, dontCareRandom, chunkX, chunkZ, chunkData);
            }
            else {

                CaveSnapshotV3Builder caveBuilderV3 = new CaveSnapshotV3Builder(chunkX, chunkZ);
                @SuppressWarnings("unchecked")
                List<CarvedInterval>[] caveIntervalsByColumn = new List[256];
                BaseSurfaceMap baseSurfaceMap = BaseSurfaceMapStoreV3.getBaseSurfaceMap(
                        tw,
                        chunkX,
                        chunkZ,
                        0
                );
                short[] baseSurfaceYByColumn = new short[256];
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int rawX = (chunkX << 4) + x;
                        int rawZ = (chunkZ << 4) + z;
                        baseSurfaceYByColumn[getColumnIndex(x, z)] = baseSurfaceMap.getColumn(rawX, rawZ).baseSurfaceY();
                    }
                }

                // For transformation ONLY
                Random transformRandom = tw.getHashedRand(chunkX, chunkZ, 31278);

                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int rawX = (chunkX<<4) + x;
                        int rawZ = (chunkZ<<4) + z;

                        double height = HeightMap.getPreciseHeight(
                                tw,
                                rawX,
                                rawZ
                        ); // bank.getHandler().calculateHeight(tw, rawX, rawZ);
                        cache.writeTransformedHeight(x, z, (short) height);

                        // Fill stone up to the world height. Differentiate between deepslate or not.
                        chunkData.setRegion(x,3,z,x+1, (int) height+1,z+1, CommonMat.STONE);
                        chunkData.setRegion(x,TerraformGeneratorPlugin.injector.getMinY(),z,
                                x+1, 0,z+1, CommonMat.DEEPSLATE);

                        // Iterate the remaining area to carve out caves.
                        for (int y = (int) height; y >= TerraformGeneratorPlugin.injector.getMinY(); y--) {
                            if (y >= 0 && y <= 2) {
                                chunkData.setBlock(x, y, z, GenUtils.randChoice(
                                        dontCareRandom,CommonMat.DEEPSLATE, CommonMat.STONE));
                            }

                            // Set cave air if a cave CAN be carved here
                            if (tw.noiseCaveRegistry.canNoiseCarve(rawX, y, rawZ, height, cache)) {
                                chunkData.setBlock(x, y, z, CommonMat.CAVE_AIR);
                            }
                        }

                        // PERFORM SURFACE AND CAVE CARVING
                        BiomeBank bank = tw.getBiomeBank(rawX, (int) height, rawZ);
                        int index = 0;
                        Material[] crust = bank.getHandler().getSurfaceCrust(dontCareRandom);
                        while (index < crust.length) {
                            chunkData.setBlock(x, (int) (height - index), z, crust[index]);
                            index++;
                        }
                        // Water for below certain heights
                        chunkData.setRegion(x, (int) (height + 1),z,x+1,seaLevel+1,z+1, CommonMat.WATER);
                        BiomeHandler transformHandler = bank.getHandler().getTransformHandler();

                        List<CarvedInterval> caveIntervals = carveLegacyAmbientCaves(
                                tw,
                                cache,
                                chunkData,
                                x,
                                z,
                                rawX,
                                rawZ,
                                height
                        );

                        // Transform height AFTER sea level is written.
                        // Transformed below-sea areas are not supposed to be water.
                        if (transformHandler != null) {
                            transformHandler.transformTerrain(cache, tw, transformRandom, chunkData, x, z, chunkX, chunkZ);
                        }
                        caveIntervalsByColumn[getColumnIndex(x, z)] = caveIntervals;

                        // Up till y = minY+HEIGHT_MAP_BEDROCK_HEIGHT
                        for (int i = 1; i < TConfig.c.HEIGHT_MAP_BEDROCK_HEIGHT; i++) {
                            if (GenUtils.chance(dontCareRandom, TConfig.c.HEIGHT_MAP_BEDROCK_DENSITY, 100)) {
                                chunkData.setBlock(x, TerraformGeneratorPlugin.injector.getMinY() + i, z, CommonMat.BEDROCK);
                            }
                            else
                                break;
                        }
                    }
                }
                // After this whole song and dance, place bedrock in one operation
                chunkData.setRegion(0,TerraformGeneratorPlugin.injector.getMinY(), 0,
                        16,TerraformGeneratorPlugin.injector.getMinY()+1, 16, CommonMat.BEDROCK);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int columnIndex = getColumnIndex(x, z);
                        caveBuilderV3.recordColumn(
                                x,
                                z,
                                baseSurfaceYByColumn[columnIndex],
                                cache.getTransformedHeight(x, z),
                                toV3Intervals(
                                        CompositeCaveGeneratorMode.LEGACY,
                                        null,
                                        cache,
                                        x,
                                        z,
                                        (chunkX << 4) + x,
                                        (chunkZ << 4) + z,
                                        baseSurfaceYByColumn[columnIndex],
                                        caveIntervalsByColumn[columnIndex]
                                )
                        );
                    }
                }
                cache.markTransformedHeightsFilled();
                CaveSnapshotV3 snapshotV3 = caveBuilderV3.build();
                CaveSnapshotStoreV3.publishGameplay(tw, chunkX, chunkZ, snapshotV3);
            }
        }
        ChunkCoord previousChunk = LAST_COMPLETED_GENERATION.put(tw, new ChunkCoord(chunkX, chunkZ));
        scheduleCompositeV3NeighborPrefill(tw, chunkX, chunkZ, previousChunk);
    }

    private void generateCompositeV3Noise(@NotNull TerraformWorld tw,
                                          @NotNull ChunkCache cache,
                                          @NotNull Random dontCareRandom,
                                          int chunkX,
                                          int chunkZ,
                                          @NotNull ChunkData chunkData)
    {
        try (CaveV3Profiler.Scope ignored = CaveV3Profiler.start("cave-v3.generate-noise")) {
            BaseSurfaceChunkV3 surfaceChunk = BaseSurfaceMapStoreV3.getBaseSurfaceChunk(tw, chunkX, chunkZ);
            boolean cavesEnabled = TConfig.areCavesEnabled();
            ChunkCache.CompositeV3ChunkPrefill fullColumnPrefill = cavesEnabled ? cache.getCompositeV3ChunkPrefill() : null;
            boolean useFullColumnPrefill = fullColumnPrefill != null;
            CaveSnapshotV3Builder caveBuilderV3 = useFullColumnPrefill ? null : new CaveSnapshotV3Builder(chunkX, chunkZ);
            @SuppressWarnings("unchecked")
            List<CarvedInterval>[] caveIntervalsByColumn = useFullColumnPrefill ? null : new List[256];
            boolean usePrecomputedTopSolidHints = cavesEnabled && cache.areTransformedHeightsFilled();
            CaveV3Profiler.recordEvent(usePrecomputedTopSolidHints
                                       ? "cave-v3.generate-noise.prefilled-top-solid-hit"
                                       : "cave-v3.generate-noise.prefilled-top-solid-miss");
            if (useFullColumnPrefill) {
                CaveV3Profiler.recordEvent("cave-v3.generate-noise.prefilled-full-column-hit");
            }
            CompositeCaveSampler compositeSampler = cavesEnabled && !useFullColumnPrefill ? createCompositeV3Sampler(tw) : null;

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int rawX = (chunkX << 4) + x;
                    int rawZ = (chunkZ << 4) + z;
                    int columnIndex = getColumnIndex(x, z);
                    int rawTerrainHeight = surfaceChunk.getRawTerrainHeight(x, z);
                    int baseSurfaceY = surfaceChunk.getBaseSurfaceY(x, z);
                    ChunkCache.CompositeV3ColumnPrefill prefilledColumn = useFullColumnPrefill
                                                                         ? fullColumnPrefill.getColumn(x, z)
                                                                         : null;
                    int precomputedTopSolidY = usePrecomputedTopSolidHints ? cache.getTransformedHeight(x, z) : baseSurfaceY;
                    if (!usePrecomputedTopSolidHints) {
                        cache.writeTransformedHeight(x, z, (short) baseSurfaceY);
                    }

                    chunkData.setRegion(x, 3, z, x + 1, rawTerrainHeight + 1, z + 1, CommonMat.STONE);
                    chunkData.setRegion(x,
                            TerraformGeneratorPlugin.injector.getMinY(),
                            z,
                            x + 1,
                            0,
                            z + 1,
                            CommonMat.DEEPSLATE
                    );

                    BiomeBank bank = tw.getBiomeBank(rawX, rawTerrainHeight, rawZ);
                    int crustIndex = 0;
                    Material[] crust = bank.getHandler().getSurfaceCrust(dontCareRandom);
                    while (crustIndex < crust.length) {
                        chunkData.setBlock(x, rawTerrainHeight - crustIndex, z, crust[crustIndex]);
                        crustIndex++;
                    }
                    chunkData.setRegion(x, rawTerrainHeight + 1, z, x + 1, seaLevel + 1, z + 1, CommonMat.WATER);
                    surfaceChunk.replaySurfaceWrites(chunkData, x, z);

                    List<CarvedInterval> carvedIntervals = carveDensityFieldColumn(
                            compositeSampler,
                            cavesEnabled,
                            cache,
                            chunkData,
                            dontCareRandom,
                            x,
                            z,
                            rawX,
                            rawZ,
                            baseSurfaceY,
                            usePrecomputedTopSolidHints,
                            precomputedTopSolidY,
                            prefilledColumn
                    );
                    if (!useFullColumnPrefill) {
                        caveIntervalsByColumn[columnIndex] = carvedIntervals;
                    }

                    for (int i = 1; i < TConfig.c.HEIGHT_MAP_BEDROCK_HEIGHT; i++) {
                        if (GenUtils.chance(dontCareRandom, TConfig.c.HEIGHT_MAP_BEDROCK_DENSITY, 100)) {
                            chunkData.setBlock(x, TerraformGeneratorPlugin.injector.getMinY() + i, z, CommonMat.BEDROCK);
                        }
                        else {
                            break;
                        }
                    }
                }
            }

            chunkData.setRegion(0,
                    TerraformGeneratorPlugin.injector.getMinY(),
                    0,
                    16,
                    TerraformGeneratorPlugin.injector.getMinY() + 1,
                    16,
                    CommonMat.BEDROCK
            );
            cache.markTransformedHeightsFilled();
            CaveSnapshotV3 snapshotV3;
            if (useFullColumnPrefill) {
                CaveV3Profiler.recordEvent("cave-v3.generate-noise.prefilled-snapshot-hit");
                snapshotV3 = fullColumnPrefill.toSnapshot(chunkX, chunkZ);
            }
            else {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int columnIndex = getColumnIndex(x, z);
                        caveBuilderV3.recordColumn(
                                x,
                                z,
                                surfaceChunk.getBaseSurfaceY(x, z),
                                cache.getTransformedHeight(x, z),
                                toV3Intervals(
                                        CompositeCaveGeneratorMode.COMPOSITE_V3,
                                        compositeSampler,
                                        cache,
                                        x,
                                        z,
                                        (chunkX << 4) + x,
                                        (chunkZ << 4) + z,
                                        surfaceChunk.getBaseSurfaceY(x, z),
                                        caveIntervalsByColumn[columnIndex]
                                )
                        );
                    }
                }
                snapshotV3 = caveBuilderV3.build();
            }
            CaveSnapshotStoreV3.publishGameplay(tw, chunkX, chunkZ, snapshotV3);
        }
    }

    private static boolean isInsideChunk(int localX, int localZ) {
        return localX >= 0 && localX < 16 && localZ >= 0 && localZ < 16;
    }

    private static int getColumnIndex(int localX, int localZ) {
        return localX + (localZ << 4);
    }

    private static @NotNull List<CarvedInterval> carveLegacyAmbientCaves(@NotNull TerraformWorld tw,
                                                                         @NotNull ChunkCache cache,
                                                                         @NotNull ChunkData chunkData,
                                                                         int localX,
                                                                         int localZ,
                                                                         int rawX,
                                                                         int rawZ,
                                                                         double height)
    {
        final int invalHeight = TerraformGeneratorPlugin.injector.getMinY() - 1;
        int firstCaveAir = invalHeight;
        int activeCaveAirTop = invalHeight;
        boolean surfaceResolved = false;
        boolean mustUpdateHeight = true;
        short transformedHeight = cache.getTransformedHeight(localX, localZ);
        int minY = TerraformGeneratorPlugin.injector.getMinY();
        List<CoordPair> rawPairs = new ArrayList<>();
        float surfaceSeaLevelFilter = tw.noiseCaveRegistry.getGenerateCarveSeaFilter(rawX, rawZ, height, cache);
        for (int y = (int) height; y > minY; y--) {
            boolean isCarved = tw.noiseCaveRegistry.canGenerateCarve(rawX, y, rawZ, height, surfaceSeaLevelFilter)
                               || !chunkData.getType(localX, y, localZ).isSolid();
            if (isCarved) {
                if (activeCaveAirTop == invalHeight) {
                    activeCaveAirTop = y;
                }
                if (mustUpdateHeight) {
                    transformedHeight = (short) (y - 1);
                }
                if (surfaceResolved && firstCaveAir == invalHeight) {
                    firstCaveAir = y;
                }
            }
            else {
                if (activeCaveAirTop != invalHeight) {
                    flushColumnAirRun(chunkData, localX, localZ, y + 1, activeCaveAirTop);
                    activeCaveAirTop = invalHeight;
                }
                if (mustUpdateHeight) {
                    cache.writeTransformedHeight(localX, localZ, transformedHeight);
                }
                mustUpdateHeight = false;
                if (surfaceResolved) {
                    if (firstCaveAir != invalHeight) {
                        rawPairs.add(new CoordPair(firstCaveAir, y));
                        firstCaveAir = invalHeight;
                    }
                }
                else {
                    surfaceResolved = true;
                }
            }
        }
        if (activeCaveAirTop != invalHeight) {
            flushColumnAirRun(chunkData, localX, localZ, minY + 1, activeCaveAirTop);
        }
        if (mustUpdateHeight) {
            cache.writeTransformedHeight(localX, localZ, transformedHeight);
        }
        return toCaveIntervals(rawPairs);
    }

    private static @NotNull CompositeCaveSampler createCompositeV3Sampler(@NotNull TerraformWorld tw) {
        return tw.getCompositeV3CaveSampler();
    }

    private static @NotNull List<CarvedInterval> carveDensityFieldColumn(CompositeCaveSampler compositeSampler,
                                                                         boolean cavesEnabled,
                                                                         @NotNull ChunkCache cache,
                                                                         @NotNull ChunkData chunkData,
                                                                         @NotNull Random dontCareRandom,
                                                                         int localX,
                                                                         int localZ,
                                                                         int rawX,
                                                                         int rawZ,
                                                                         double surfaceHeight,
                                                                         boolean usePrecomputedTopSolidHint,
                                                                         int precomputedTopSolidY,
                                                                         ChunkCache.CompositeV3ColumnPrefill prefilledColumn)
    {
        try (CaveV3Profiler.Scope ignored = CaveV3Profiler.start("cave-v3.carve-column")) {
            final int minY = TerraformGeneratorPlugin.injector.getMinY();
            final int surfaceY = (int) surfaceHeight;
            if (prefilledColumn != null) {
                return replayPrefilledDensityColumn(
                        cache,
                        chunkData,
                        dontCareRandom,
                        localX,
                        localZ,
                        surfaceY,
                        minY,
                        prefilledColumn
                );
            }
            final int invalHeight = minY - 1;
            int firstCaveAir = invalHeight;
            int activeCaveAirTop = invalHeight;
            boolean surfaceResolved = false;
            boolean mustUpdateHeight = true;
            short transformedHeight = cache.getTransformedHeight(localX, localZ);
            List<CoordPair> rawPairs = new ArrayList<>();
            int y = surfaceY;

            if (usePrecomputedTopSolidHint) {
                int hintedTopSolidY = Math.min(y, precomputedTopSolidY);
                if (hintedTopSolidY < minY) {
                    flushColumnAirRun(chunkData, localX, localZ, minY, y);
                    CaveV3Profiler.recordEvent("cave-v3.generate-noise.prefill-empty-column");
                    CaveV3Profiler.recordEvents("cave-v3.generate-noise.prefill-air-voxels-reused", y - minY + 1L);
                    return Collections.emptyList();
                }

                if (y > hintedTopSolidY) {
                    flushColumnAirRun(chunkData, localX, localZ, hintedTopSolidY + 1, y);
                }
                if (y > hintedTopSolidY) {
                    CaveV3Profiler.recordEvent("cave-v3.generate-noise.prefill-partial-column");
                    CaveV3Profiler.recordEvents(
                            "cave-v3.generate-noise.prefill-air-voxels-reused",
                            y - hintedTopSolidY
                    );
                }

                if (hintedTopSolidY >= 0 && hintedTopSolidY <= 2) {
                    chunkData.setBlock(localX, hintedTopSolidY, localZ, GenUtils.randChoice(
                            dontCareRandom,
                            CommonMat.DEEPSLATE,
                            CommonMat.STONE
                    ));
                }
                surfaceResolved = true;
                mustUpdateHeight = false;
                y = hintedTopSolidY - 1;
            }

            CompositeCaveColumnSampler columnSampler = cavesEnabled
                                                      ? compositeSampler.createColumnSampler(
                                                              rawX,
                                                              rawZ,
                                                              surfaceHeight,
                                                              cache
                                                      )
                                                      : null;
            for (; y >= minY; y--) {
                if (y >= 0 && y <= 2) {
                    chunkData.setBlock(localX, y, localZ, GenUtils.randChoice(
                            dontCareRandom,
                            CommonMat.DEEPSLATE,
                            CommonMat.STONE
                    ));
                }

                boolean isCarved = false;
                if (cavesEnabled) {
                    isCarved = columnSampler.canCarve(y);
                }
                if (isCarved) {
                    if (activeCaveAirTop == invalHeight) {
                        activeCaveAirTop = y;
                    }
                    if (y > minY && mustUpdateHeight) {
                        transformedHeight = (short) (y - 1);
                    }
                    if (y > minY && surfaceResolved && firstCaveAir == invalHeight) {
                        firstCaveAir = y;
                    }
                }
                else {
                    if (activeCaveAirTop != invalHeight) {
                        flushColumnAirRun(chunkData, localX, localZ, y + 1, activeCaveAirTop);
                        activeCaveAirTop = invalHeight;
                    }
                    if (y > minY) {
                        if (mustUpdateHeight) {
                            cache.writeTransformedHeight(localX, localZ, transformedHeight);
                        }
                        mustUpdateHeight = false;
                        if (surfaceResolved) {
                            if (firstCaveAir != invalHeight) {
                                rawPairs.add(new CoordPair(firstCaveAir, y));
                                firstCaveAir = invalHeight;
                            }
                        }
                        else {
                            surfaceResolved = true;
                        }
                    }
                }
            }

            if (activeCaveAirTop != invalHeight) {
                flushColumnAirRun(chunkData, localX, localZ, minY, activeCaveAirTop);
            }
            if (mustUpdateHeight && transformedHeight != cache.getTransformedHeight(localX, localZ)) {
                cache.writeTransformedHeight(localX, localZ, transformedHeight);
            }
            return toCaveIntervals(rawPairs);
        }
    }

    private static void flushColumnAirRun(@NotNull ChunkData chunkData,
                                          int localX,
                                          int localZ,
                                          int bottomY,
                                          int topY)
    {
        if (bottomY > topY) {
            return;
        }
        chunkData.setRegion(localX, bottomY, localZ, localX + 1, topY + 1, localZ + 1, CommonMat.CAVE_AIR);
    }

    private static @NotNull List<CarvedInterval> replayPrefilledDensityColumn(@NotNull ChunkCache cache,
                                                                               @NotNull ChunkData chunkData,
                                                                               @NotNull Random dontCareRandom,
                                                                               int localX,
                                                                               int localZ,
                                                                               int surfaceY,
                                                                               int minY,
                                                                               @NotNull ChunkCache.CompositeV3ColumnPrefill prefilledColumn)
    {
        int bottomRandomY = Math.max(minY, 0);
        int topRandomY = Math.min(surfaceY, 2);
        for (int y = topRandomY; y >= bottomRandomY; y--) {
            chunkData.setBlock(localX, y, localZ, GenUtils.randChoice(
                    dontCareRandom,
                    CommonMat.DEEPSLATE,
                    CommonMat.STONE
            ));
        }

        short transformedHeight = prefilledColumn.getTransformedHeight();
        cache.writeTransformedHeight(localX, localZ, transformedHeight);

        short[] carvedAirRuns = prefilledColumn.getCarvedAirRuns();
        long reusedAirVoxels = 0L;
        for (int i = 0; i < carvedAirRuns.length; i += 2) {
            int bottomY = carvedAirRuns[i];
            int topY = carvedAirRuns[i + 1];
            flushColumnAirRun(chunkData, localX, localZ, bottomY, topY);
            reusedAirVoxels += (long) topY - bottomY + 1L;
        }
        if (reusedAirVoxels > 0L) {
            CaveV3Profiler.recordEvents("cave-v3.generate-noise.prefill-air-voxels-reused", reusedAirVoxels);
        }

        return toCaveIntervalsFromAirRuns(carvedAirRuns, transformedHeight, minY);
    }

    private static @NotNull List<CarvedInterval> toCaveIntervalsFromAirRuns(short @NotNull [] carvedAirRuns,
                                                                             short transformedHeight,
                                                                             int minY)
    {
        if (carvedAirRuns.length == 0) {
            return Collections.emptyList();
        }

        List<CoordPair> rawPairs = new ArrayList<>(carvedAirRuns.length / 2);
        for (int i = 0; i < carvedAirRuns.length; i += 2) {
            int bottomY = carvedAirRuns[i];
            int topY = carvedAirRuns[i + 1];
            if (bottomY <= minY || bottomY > transformedHeight) {
                continue;
            }
            rawPairs.add(new CoordPair(topY, bottomY - 1));
        }
        return toCaveIntervals(rawPairs);
    }

    private static @NotNull List<CaveIntervalV3> toV3Intervals(@NotNull CompositeCaveGeneratorMode caveMode,
                                                                CompositeCaveSampler compositeSampler,
                                                                @NotNull ChunkCache cache,
                                                                int localX,
                                                                int localZ,
                                                                int rawX,
                                                                int rawZ,
                                                                double baseSurfaceHeight,
                                                                List<CarvedInterval> intervals)
    {
        if (intervals == null || intervals.isEmpty()) {
            return Collections.emptyList();
        }

        return toV3Intervals(cache.getTransformedHeight(localX, localZ), intervals);
    }

    private static @NotNull List<CaveIntervalV3> toV3Intervals(int topSolidY,
                                                                @NotNull List<CarvedInterval> intervals)
    {
        if (intervals.isEmpty()) {
            return Collections.emptyList();
        }

        List<CaveIntervalV3> result = new ArrayList<>(intervals.size());
        for (CarvedInterval interval : intervals) {
            result.add(new CaveIntervalV3(
                    interval.ceilingAirY(),
                    interval.floorSolidY(),
                    buildIntervalMetadata(topSolidY, interval)
            ));
        }
        return result;
    }

    private static @NotNull CaveIntervalMetadata buildIntervalMetadata(@NotNull ChunkCache cache,
                                                                       int localX,
                                                                       int localZ,
                                                                       @NotNull CarvedInterval interval)
    {
        int topSolidY = cache.getTransformedHeight(localX, localZ);
        return buildIntervalMetadata(topSolidY, interval);
    }

    private static @NotNull CaveIntervalMetadata buildIntervalMetadata(int topSolidY,
                                                                       @NotNull CarvedInterval interval)
    {
        SurfaceConnectivity connectivity = interval.ceilingAirY() > topSolidY ? SurfaceConnectivity.YES : SurfaceConnectivity.NO;
        return new CaveIntervalMetadata(connectivity);
    }

    private static @NotNull List<CarvedInterval> toCaveIntervals(@NotNull Collection<CoordPair> rawPairs) {
        Collection<CoordPair> filteredCaveCeilFloors = MasterCavePopulatorDistributor.getFilteredPairs(
                rawPairs,
                MasterCavePopulatorDistributor.AMBIENT_MINIMUM_CAVE_HEIGHT
        );
        if (filteredCaveCeilFloors.isEmpty()) {
            return Collections.emptyList();
        }

        List<CarvedInterval> caveIntervals = new ArrayList<>(filteredCaveCeilFloors.size());
        for (CoordPair pair : filteredCaveCeilFloors) {
            caveIntervals.add(new CarvedInterval((short) pair.x(), (short) pair.z()));
        }
        return caveIntervals;
    }

    /**
     * Responsible for setting surface biome blocks and biomeTransforms
     */
    public void generateSurface(@NotNull WorldInfo worldInfo,
                                @NotNull Random dontCareRandom,
                                int chunkX,
                                int chunkZ,
                                @NotNull ChunkData chunkData)
    {

    }

    public void generateBedrock(@NotNull WorldInfo worldInfo,
                                @NotNull Random random,
                                int chunkX,
                                int chunkZ,
                                @NotNull ChunkData chunkData)
    {

    }

    public void generateCaves(@NotNull WorldInfo worldInfo,
                              @NotNull Random random,
                              int chunkX,
                              int chunkZ,
                              @NotNull ChunkData chunkData)
    {

    }

    @Override
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        return new Location(world, 0, HeightMap.getBlockHeight(TerraformWorld.get(world), 0, 0), 0);
    }

    @Override
    public @NotNull List<BlockPopulator> getDefaultPopulators(@NotNull World world) {
        TerraformWorld tw = TerraformWorld.get(world);
        return List.of(new TerraformPopulator(),new TerraformBukkitBlockPopulator(tw));
    }

    // Do exactly 0 of this, TFG now handles ALL of it.
    public boolean shouldGenerateNoise() {
        return false;
    }

    public boolean shouldGenerateSurface() {
        return false;
    }

    public boolean shouldGenerateBedrock() {
        return false;
    }

    public boolean shouldGenerateCaves() {
        return false;
    }

    public boolean shouldGenerateDecorations() {
        return false;
    }

    public boolean shouldGenerateMobs() {
        return false;
    }

    // This is true as StructureManager is now being overridden.
    public boolean shouldGenerateStructures() {
        return true;
    }
}
