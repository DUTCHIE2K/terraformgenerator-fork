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
import org.terraform.biome.cavepopulators.MasterCavePopulatorDistributor;
import org.terraform.cave.v3.BaseSurfaceMap;
import org.terraform.cave.v3.BaseSurfaceMapStoreV3;
import org.terraform.cave.v3.CaveIntervalMetadata;
import org.terraform.cave.v3.CaveIntervalV3;
import org.terraform.cave.v3.CaveSnapshotStoreV3;
import org.terraform.cave.v3.CaveSnapshotV3;
import org.terraform.cave.v3.CaveSnapshotV3Builder;
import org.terraform.cave.v3.SurfaceConnectivity;
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

public class TerraformGenerator extends ChunkGenerator {
    public static final List<SimpleChunkLocation> preWorldInitGen = new ArrayList<>();
    // Explode if a read is attempted. Transform Handlers are not supposed to read.
    private static final DudChunkData DUD = new DudChunkData();
    //This cache is NOT fucking used correctly.
    // By right, nobody's supposed to be writing to it at the same time, but in
    // practice, that doesn't matter
    public static ConcurrentLRUCache<TWCoordPair, ChunkCache> CHUNK_CACHE;
    public static int seaLevel = 62;
    private record CarvedInterval(short ceilingAirY, short floorSolidY) {}

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
        if (cache.areTransformedHeightsFilled()) {
            return;
        }

        // TerraformGeneratorPlugin.watchdogSuppressant.tickWatchdog(); don't unnecessarily tick this shit

        CompositeCaveGeneratorMode caveMode = CompositeCaveGeneratorMode.fromConfig(TConfig.c.CAVES_GENERATOR_MODE);
        if (caveMode == CompositeCaveGeneratorMode.COMPOSITE_V3) {
            boolean cavesEnabled = TConfig.areCavesEnabled();
            BaseSurfaceChunkV3 surfaceChunk = BaseSurfaceMapStoreV3.getBaseSurfaceChunk(tw, chunkX, chunkZ);
            CompositeCaveSampler compositeSampler = cavesEnabled ? createCompositeV3Sampler(tw) : null;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int rawX = chunkX * 16 + x;
                    int rawZ = chunkZ * 16 + z;
                    int baseSurfaceY = surfaceChunk.getBaseSurfaceY(x, z);
                    cache.writeTransformedHeight(x, z, (short) baseSurfaceY);
                    if (cavesEnabled) {
                        for (int y = baseSurfaceY; y >= TerraformGeneratorPlugin.injector.getMinY(); y--) {
                            if (compositeSampler.canCarve(rawX, y, rawZ, baseSurfaceY, cache)) {
                                cache.writeTransformedHeight(x, z, (short) (y - 1));
                            }
                            else {
                                break;
                            }
                        }
                    }
                }
            }
            cache.markTransformedHeightsFilled();
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
                for (int y = (int) preciseHeight; y >= TerraformGeneratorPlugin.injector.getMinY(); y--)
                // Set stone if a cave CANNOT be carved here
                // Check canNoiseCarve because carver caves may expose
                // noise caves below, which contribute to height changes
                {
                    if (tw.noiseCaveRegistry.canGenerateCarve(rawX, y, rawZ, preciseHeight, seaLevelFilter)
                        || tw.noiseCaveRegistry.canNoiseCarve(rawX, y, rawZ, preciseHeight, cache))
                    {
                        cache.writeTransformedHeight(x, z, (short) (y - 1));
                    }
                    else {
                        break;
                    }
                }
            }
        }
        applyChunkTerrainTransforms(tw, chunkX, chunkZ, cache, DUD);
        cache.markTransformedHeightsFilled();
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
        if (caveMode == CompositeCaveGeneratorMode.COMPOSITE_V3) {
            generateCompositeV3Noise(tw, cache, dontCareRandom, chunkX, chunkZ, chunkData);
            return;
        }

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
                        cache.cacheNonSolid(x,y,z);
                    }
                    else cache.cacheSolid(x,y,z);
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

    private void generateCompositeV3Noise(@NotNull TerraformWorld tw,
                                          @NotNull ChunkCache cache,
                                          @NotNull Random dontCareRandom,
                                          int chunkX,
                                          int chunkZ,
                                          @NotNull ChunkData chunkData)
    {
        CaveSnapshotV3Builder caveBuilderV3 = new CaveSnapshotV3Builder(chunkX, chunkZ);
        @SuppressWarnings("unchecked")
        List<CarvedInterval>[] caveIntervalsByColumn = new List[256];
        BaseSurfaceChunkV3 surfaceChunk = BaseSurfaceMapStoreV3.getBaseSurfaceChunk(tw, chunkX, chunkZ);
        boolean cavesEnabled = TConfig.areCavesEnabled();
        boolean usePrecomputedTopSolidHints = cavesEnabled && cache.areTransformedHeightsFilled();
        CompositeCaveSampler compositeSampler = cavesEnabled ? createCompositeV3Sampler(tw) : null;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int rawX = (chunkX << 4) + x;
                int rawZ = (chunkZ << 4) + z;
                int columnIndex = getColumnIndex(x, z);
                int rawTerrainHeight = surfaceChunk.getRawTerrainHeight(x, z);
                int baseSurfaceY = surfaceChunk.getBaseSurfaceY(x, z);
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

                caveIntervalsByColumn[columnIndex] = carveDensityFieldColumn(
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
                        precomputedTopSolidY
                );

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
        cache.markTransformedHeightsFilled();
        CaveSnapshotV3 snapshotV3 = caveBuilderV3.build();
        CaveSnapshotStoreV3.publishGameplay(tw, chunkX, chunkZ, snapshotV3);
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
        boolean surfaceResolved = false;
        boolean mustUpdateHeight = true;
        int minY = TerraformGeneratorPlugin.injector.getMinY();
        List<CoordPair> rawPairs = new ArrayList<>();
        float surfaceSeaLevelFilter = tw.noiseCaveRegistry.getGenerateCarveSeaFilter(rawX, rawZ, height, cache);
        for (int y = (int) height; y > minY; y--) {
            boolean isCarved = tw.noiseCaveRegistry.canGenerateCarve(rawX, y, rawZ, height, surfaceSeaLevelFilter)
                               || !chunkData.getType(localX, y, localZ).isSolid();
            if (isCarved) {
                chunkData.setBlock(localX, y, localZ, CommonMat.CAVE_AIR);
                cache.cacheNonSolid(localX, y, localZ);
                if (mustUpdateHeight) {
                    cache.writeTransformedHeight(localX, localZ, (short) (y - 1));
                }
                if (surfaceResolved && firstCaveAir == invalHeight) {
                    firstCaveAir = y;
                }
            }
            else {
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
                                                                         int precomputedTopSolidY)
    {
        final int minY = TerraformGeneratorPlugin.injector.getMinY();
        final int invalHeight = minY - 1;
        int firstCaveAir = invalHeight;
        boolean surfaceResolved = false;
        boolean mustUpdateHeight = true;
        List<CoordPair> rawPairs = new ArrayList<>();
        int y = (int) surfaceHeight;

        if (usePrecomputedTopSolidHint) {
            int hintedTopSolidY = Math.min(y, precomputedTopSolidY);
            if (hintedTopSolidY < minY) {
                for (int carveY = y; carveY >= minY; carveY--) {
                    chunkData.setBlock(localX, carveY, localZ, CommonMat.CAVE_AIR);
                    cache.cacheNonSolid(localX, carveY, localZ);
                }
                return Collections.emptyList();
            }

            for (int carveY = y; carveY > hintedTopSolidY; carveY--) {
                chunkData.setBlock(localX, carveY, localZ, CommonMat.CAVE_AIR);
                cache.cacheNonSolid(localX, carveY, localZ);
            }

            if (hintedTopSolidY >= 0 && hintedTopSolidY <= 2) {
                chunkData.setBlock(localX, hintedTopSolidY, localZ, GenUtils.randChoice(
                        dontCareRandom,
                        CommonMat.DEEPSLATE,
                        CommonMat.STONE
                ));
            }
            cache.cacheSolid(localX, hintedTopSolidY, localZ);
            surfaceResolved = true;
            mustUpdateHeight = false;
            y = hintedTopSolidY - 1;
        }

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
                isCarved = compositeSampler.canCarve(rawX, y, rawZ, surfaceHeight, cache);
            }
            if (isCarved) {
                chunkData.setBlock(localX, y, localZ, CommonMat.CAVE_AIR);
                cache.cacheNonSolid(localX, y, localZ);
                if (y > minY && mustUpdateHeight) {
                    cache.writeTransformedHeight(localX, localZ, (short) (y - 1));
                }
                if (y > minY && surfaceResolved && firstCaveAir == invalHeight) {
                    firstCaveAir = y;
                }
            }
            else {
                cache.cacheSolid(localX, y, localZ);
                if (y > minY) {
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

        List<CaveIntervalV3> result = new ArrayList<>(intervals.size());
        for (CarvedInterval interval : intervals) {
            result.add(new CaveIntervalV3(
                    interval.ceilingAirY(),
                    interval.floorSolidY(),
                    buildIntervalMetadata(
                            cache,
                            localX,
                            localZ,
                            interval
                    )
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
