package org.terraform.coregen.bukkit;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.terraform.biome.BiomeBank;
import org.terraform.biome.BiomeHandler;
import org.terraform.biome.cavepopulators.MasterCavePopulatorDistributor;
import org.terraform.cave.v2.CaveInterval;
import org.terraform.cave.v2.CaveSnapshotBuilder;
import org.terraform.cave.v2.CaveSnapshotStore;
import org.terraform.cave.v2.generation.AmbientCaveGeneratorMode;
import org.terraform.cave.v2.generation.CaveDensitySampler;
import org.terraform.cave.v2.generation.CaveFieldProvider;
import org.terraform.cave.v2.generation.Phase3ADensityFieldProvider;
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
import org.terraform.utils.BlockUtils;
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
    private static final long DENSITY_V1_ENTRANCE_SEED_SALT = 0x51A1E5EEL;
    private static final float DENSITY_V1_ENTRANCE_PERTURB_MULTIPLIER = 0.35f;
    private static final int DENSITY_V1_ENTRANCE_HALF_WIDTH = 1;
    private static final int DENSITY_V1_ENTRANCE_DEPTH = 2;
    private static final int DENSITY_V1_ENTRANCE_ROOF_OFFSET = 2;
    private static final int DENSITY_V1_ENTRANCE_DOMINANT_MARGIN = 1;
    private static final int DENSITY_V1_ENTRANCE_FACE_DROP = 1;
    private static final int DENSITY_V1_ENTRANCE_SEA_LEVEL_CLEARANCE = 2;
    private static final int DENSITY_V1_ENTRANCE_INTERVAL_TOLERANCE = 4;
    private static final int DENSITY_V1_ENTRANCE_MAX_FLOOR_EXTRA_DEPTH = 6;
    private static final int DENSITY_V1_ENTRANCE_NEIGHBORHOOD_MIN_MATCHES = 4;
    private static final float DENSITY_V1_ENTRANCE_MIN_VALID_FOOTPRINT_RATIO = 0.67f;
    private static final int DENSITY_V1_ENTRANCE_DEBUG_SAMPLE_LIMIT = 3;

    private enum DensityEntranceRejectionReason {
        WATER_DRY_CONSTRAINT("water/dry constraint failure"),
        SLOPE_DIRECTION("slope/direction failure"),
        CAVE_VALIDATION("cave validation failure"),
        FOOTPRINT_VALIDATION("footprint validation failure");

        private final String label;

        DensityEntranceRejectionReason(String label) {
            this.label = label;
        }
    }

    private record DensityEntranceCandidate(int localX,
                                            int localZ,
                                            @NotNull BlockFace direction,
                                            @NotNull CaveInterval targetInterval) {}

    private record EntranceFootprintColumn(int localX, int localZ, int depthStep, int widthOffset) {}

    private static final class DensityEntranceDebugStats {
        private final TerraformWorld tw;
        private final int chunkX;
        private final int chunkZ;
        private int candidates;
        private int slopePasses;
        private int cavePasses;
        private int footprintPasses;
        private int accepted;
        private int sampledRejections;

        private DensityEntranceDebugStats(@NotNull TerraformWorld tw, int chunkX, int chunkZ) {
            this.tw = tw;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        private void sampleRejection(int rawX, int rawZ, @NotNull DensityEntranceRejectionReason reason) {
            if (sampledRejections >= DENSITY_V1_ENTRANCE_DEBUG_SAMPLE_LIMIT) {
                return;
            }
            sampledRejections++;
            TerraformGeneratorPlugin.logger.info(
                    "[Entrances] reject " + reason.label + " at (" + rawX + "," + rawZ + ") in "
                    + tw.getName() + " chunk " + chunkX + "," + chunkZ
            );
        }

        private void logApplied(int rawX, int rawZ) {
            TerraformGeneratorPlugin.logger.info("[Entrances] applied at (" + rawX + "," + rawZ + ")");
        }

        private void logSummary() {
            TerraformGeneratorPlugin.logger.info(
                    "[Entrances] " + tw.getName() + " chunk " + chunkX + "," + chunkZ
                    + " candidates=" + candidates
                    + " slope=" + slopePasses
                    + " cave=" + cavePasses
                    + " footprint=" + footprintPasses
                    + " accepted=" + accepted
            );
        }
    }

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

        // Ensure that this shit is the same as the one in generateSurface
        Random random = tw.getHashedRand(chunkX, chunkZ, 31278);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int rawX = chunkX * 16 + x;
                int rawZ = chunkZ * 16 + z;

                double preciseHeight = HeightMap.getPreciseHeight(
                        tw,
                        rawX,
                        rawZ
                ); // bank.getHandler().calculateHeight(tw, rawX, rawZ);
                cache.writeTransformedHeight(x, z, (short) preciseHeight);

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

                // Apply biome transforms to get real height
                BiomeBank bank = tw.getBiomeBank(rawX, (int) preciseHeight, rawZ);
                BiomeHandler transformHandler = bank.getHandler().getTransformHandler();

                if (transformHandler != null) {
                    transformHandler.transformTerrain(cache, tw, random, DUD, x, z, chunkX, chunkZ);
                }
            }
        }
        cache.markTransformedHeightsFilled();
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
        CaveSnapshotBuilder caveBuilder = new CaveSnapshotBuilder(chunkX, chunkZ);
        @SuppressWarnings("unchecked")
        List<CaveInterval>[] caveIntervalsByColumn = new List[256];

        // For transformation ONLY
        Random transformRandom = tw.getHashedRand(chunkX, chunkZ, 31278);
        AmbientCaveGeneratorMode caveMode = AmbientCaveGeneratorMode.fromConfig(TConfig.c.CAVES_GENERATOR_MODE);
        CaveFieldProvider fieldProvider = caveMode == AmbientCaveGeneratorMode.DENSITY_V1
                                          ? new Phase3ADensityFieldProvider()
                                          : null;
        CaveDensitySampler densitySampler = fieldProvider == null ? null : fieldProvider.createSampler(tw);

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

                if (caveMode == AmbientCaveGeneratorMode.LEGACY) {
                    //Iterate the remaining area to carve out caves
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

                List<CaveInterval> caveIntervals = caveMode == AmbientCaveGeneratorMode.DENSITY_V1
                                                   ? carveDensityFieldColumn(
                                                           densitySampler,
                                                           cache,
                                                           chunkData,
                                                           dontCareRandom,
                                                           x,
                                                           z,
                                                           rawX,
                                                           rawZ,
                                                           height
                                                   )
                                                   : carveLegacyAmbientCaves(
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
                BiomeHandler transformHandler = bank.getHandler().getTransformHandler();
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
        if (caveMode == AmbientCaveGeneratorMode.DENSITY_V1) {
            applyDensitySurfaceEntrances(tw, cache, chunkData, chunkX, chunkZ, caveIntervalsByColumn);
        }
        // After this whole song and dance, place bedrock in one operation
        chunkData.setRegion(0,TerraformGeneratorPlugin.injector.getMinY(), 0,
                16,TerraformGeneratorPlugin.injector.getMinY()+1, 16, CommonMat.BEDROCK);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                caveBuilder.recordColumn(
                        x,
                        z,
                        cache.getTransformedHeight(x, z),
                        caveIntervalsByColumn[getColumnIndex(x, z)]
                );
            }
        }
        cache.markTransformedHeightsFilled();
        CaveSnapshotStore.publish(tw, chunkX, chunkZ, caveBuilder.build());
    }

    private static void applyDensitySurfaceEntrances(@NotNull TerraformWorld tw,
                                                     @NotNull ChunkCache cache,
                                                     @NotNull ChunkData chunkData,
                                                     int chunkX,
                                                     int chunkZ,
                                                     @NotNull List<CaveInterval>[] intervalsByColumn)
    {
        if (!TConfig.c.CAVES_DENSITY_V1_ENTRANCES_ENABLED) {
            return;
        }

        int spacing = Math.max(32, TConfig.c.CAVES_DENSITY_V1_ENTRANCES_SPACING);
        int validationRadius = Math.max(1, TConfig.c.CAVES_DENSITY_V1_ENTRANCES_VALIDATION_RADIUS);
        int chunkMargin = validationRadius + DENSITY_V1_ENTRANCE_DEPTH + DENSITY_V1_ENTRANCE_HALF_WIDTH;
        DensityEntranceDebugStats debugStats = TConfig.c.CAVES_DENSITY_V1_ENTRANCES_DEBUG
                                               ? new DensityEntranceDebugStats(tw, chunkX, chunkZ)
                                               : null;
        CoordPair[] entranceCandidates = GenUtils.vectorRandomObjectPositions(
                Long.hashCode(tw.getSeed() ^ DENSITY_V1_ENTRANCE_SEED_SALT),
                chunkX,
                chunkZ,
                spacing,
                DENSITY_V1_ENTRANCE_PERTURB_MULTIPLIER * spacing
        );
        boolean[] touched = new boolean[256];
        ArrayList<Integer> touchedOrder = new ArrayList<>();

        for (CoordPair entranceCandidate : entranceCandidates) {
            int localX = (int) entranceCandidate.x() - (chunkX << 4);
            int localZ = (int) entranceCandidate.z() - (chunkZ << 4);
            if (localX < chunkMargin || localX > 15 - chunkMargin || localZ < chunkMargin || localZ > 15 - chunkMargin) {
                continue;
            }
            if (debugStats != null) {
                debugStats.candidates++;
            }

            DensityEntranceCandidate candidate = evaluateDensityEntranceCandidate(
                    cache,
                    chunkData,
                    intervalsByColumn,
                    touched,
                    localX,
                    localZ,
                    validationRadius,
                    debugStats,
                    chunkX,
                    chunkZ
            );
            if (candidate != null) {
                applyDensityEntranceBreach(cache, chunkData, touched, touchedOrder, candidate);
                if (debugStats != null) {
                    debugStats.accepted++;
                    debugStats.logApplied((chunkX << 4) + candidate.localX(), (chunkZ << 4) + candidate.localZ());
                }
            }
        }

        for (int index : touchedOrder) {
            intervalsByColumn[index] = rebuildTouchedDensityColumn(cache, chunkData, index & 0xF, index >> 4);
        }
        if (debugStats != null) {
            debugStats.logSummary();
        }
    }

    private static DensityEntranceCandidate evaluateDensityEntranceCandidate(@NotNull ChunkCache cache,
                                                                            @NotNull ChunkData chunkData,
                                                                            @NotNull List<CaveInterval>[] intervalsByColumn,
                                                                            boolean @NotNull [] touched,
                                                                            int localX,
                                                                            int localZ,
                                                                            int validationRadius,
                                                                            DensityEntranceDebugStats debugStats,
                                                                            int chunkX,
                                                                            int chunkZ)
    {
        int rawX = (chunkX << 4) + localX;
        int rawZ = (chunkZ << 4) + localZ;
        if (!isSafeEntranceColumn(cache, chunkData, localX, localZ)) {
            if (debugStats != null) {
                debugStats.sampleRejection(rawX, rawZ, DensityEntranceRejectionReason.WATER_DRY_CONSTRAINT);
            }
            return null;
        }

        BlockFace direction = getDominantEntranceDirection(cache, localX, localZ);
        if (direction == null) {
            if (debugStats != null) {
                debugStats.sampleRejection(rawX, rawZ, DensityEntranceRejectionReason.SLOPE_DIRECTION);
            }
            return null;
        }
        if (debugStats != null) {
            debugStats.slopePasses++;
        }

        List<EntranceFootprintColumn> footprint = getEntranceFootprint(localX, localZ, direction);
        if (!isUntouchedEntranceFootprint(footprint, touched)) {
            if (debugStats != null) {
                debugStats.sampleRejection(rawX, rawZ, DensityEntranceRejectionReason.FOOTPRINT_VALIDATION);
            }
            return null;
        }

        CaveInterval targetInterval = findDensityEntranceTarget(cache, intervalsByColumn, footprint);
        if (targetInterval == null) {
            if (debugStats != null) {
                debugStats.sampleRejection(rawX, rawZ, DensityEntranceRejectionReason.CAVE_VALIDATION);
            }
            return null;
        }

        if (!hasEntranceNeighborhoodMatch(intervalsByColumn, localX, localZ, validationRadius, targetInterval)) {
            if (debugStats != null) {
                debugStats.sampleRejection(rawX, rawZ, DensityEntranceRejectionReason.CAVE_VALIDATION);
            }
            return null;
        }
        if (debugStats != null) {
            debugStats.cavePasses++;
        }

        if (!isDensityEntranceFootprintValid(cache, chunkData, intervalsByColumn, footprint, direction, targetInterval)) {
            if (debugStats != null) {
                debugStats.sampleRejection(rawX, rawZ, DensityEntranceRejectionReason.FOOTPRINT_VALIDATION);
            }
            return null;
        }
        if (debugStats != null) {
            debugStats.footprintPasses++;
        }

        return new DensityEntranceCandidate(localX, localZ, direction, targetInterval);
    }

    private static void applyDensityEntranceBreach(@NotNull ChunkCache cache,
                                                   @NotNull ChunkData chunkData,
                                                   boolean @NotNull [] touched,
                                                   @NotNull ArrayList<Integer> touchedOrder,
                                                   @NotNull DensityEntranceCandidate candidate)
    {
        int centerSurfaceY = cache.getTransformedHeight(candidate.localX(), candidate.localZ());
        int minY = TerraformGeneratorPlugin.injector.getMinY();
        for (EntranceFootprintColumn footprintColumn : getEntranceFootprint(
                candidate.localX(),
                candidate.localZ(),
                candidate.direction()
        ))
        {
            int localX = footprintColumn.localX();
            int localZ = footprintColumn.localZ();
            int columnSurfaceY = cache.getTransformedHeight(localX, localZ);
            int roofY = Math.min(
                    centerSurfaceY - 1,
                    columnSurfaceY + DENSITY_V1_ENTRANCE_ROOF_OFFSET - footprintColumn.depthStep()
            );
            int floorY = candidate.targetInterval().ceilingAirY()
                         + (footprintColumn.depthStep() * 2)
                         + Math.abs(footprintColumn.widthOffset());
            if (roofY <= minY || floorY > roofY) {
                continue;
            }

            markTouchedColumn(localX, localZ, touched, touchedOrder);
            for (int y = roofY; y >= floorY && y > minY; y--) {
                chunkData.setBlock(localX, y, localZ, CommonMat.CAVE_AIR);
                if (y <= columnSurfaceY) {
                    cache.cacheNonSolid(localX, y, localZ);
                }
            }
        }
    }

    private static @NotNull List<CaveInterval> rebuildTouchedDensityColumn(@NotNull ChunkCache cache,
                                                                           @NotNull ChunkData chunkData,
                                                                           int localX,
                                                                           int localZ)
    {
        int minY = TerraformGeneratorPlugin.injector.getMinY();
        int invalidHeight = minY - 1;
        int topSolidY = cache.getTransformedHeight(localX, localZ);
        while (topSolidY > minY && !chunkData.getType(localX, topSolidY, localZ).isSolid()) {
            cache.cacheNonSolid(localX, topSolidY, localZ);
            topSolidY--;
        }

        if (chunkData.getType(localX, topSolidY, localZ).isSolid()) {
            cache.cacheSolid(localX, topSolidY, localZ);
        }
        else {
            cache.cacheNonSolid(localX, topSolidY, localZ);
        }
        cache.writeTransformedHeight(localX, localZ, (short) topSolidY);

        List<CoordPair> rawPairs = new ArrayList<>();
        int firstCaveAir = invalidHeight;
        for (int y = topSolidY - 1; y > minY; y--) {
            if (chunkData.getType(localX, y, localZ).isSolid()) {
                cache.cacheSolid(localX, y, localZ);
                if (firstCaveAir != invalidHeight) {
                    rawPairs.add(new CoordPair(firstCaveAir, y));
                    firstCaveAir = invalidHeight;
                }
            }
            else {
                cache.cacheNonSolid(localX, y, localZ);
                if (firstCaveAir == invalidHeight) {
                    firstCaveAir = y;
                }
            }
        }

        if (chunkData.getType(localX, minY, localZ).isSolid()) {
            cache.cacheSolid(localX, minY, localZ);
        }
        else {
            cache.cacheNonSolid(localX, minY, localZ);
        }
        return toCaveIntervals(rawPairs);
    }

    private static @NotNull List<EntranceFootprintColumn> getEntranceFootprint(int localX,
                                                                               int localZ,
                                                                               @NotNull BlockFace direction)
    {
        ArrayList<EntranceFootprintColumn> footprint = new ArrayList<>(DENSITY_V1_ENTRANCE_DEPTH * 3);
        BlockFace side = BlockUtils.getRight(direction);
        for (int depthStep = 0; depthStep < DENSITY_V1_ENTRANCE_DEPTH; depthStep++) {
            int baseX = localX + direction.getModX() * depthStep;
            int baseZ = localZ + direction.getModZ() * depthStep;
            for (int widthOffset = -DENSITY_V1_ENTRANCE_HALF_WIDTH;
                 widthOffset <= DENSITY_V1_ENTRANCE_HALF_WIDTH;
                 widthOffset++)
            {
                footprint.add(new EntranceFootprintColumn(
                        baseX + side.getModX() * widthOffset,
                        baseZ + side.getModZ() * widthOffset,
                        depthStep,
                        widthOffset
                ));
            }
        }
        return footprint;
    }

    private static CaveInterval findDensityEntranceTarget(@NotNull ChunkCache cache,
                                                          @NotNull List<CaveInterval>[] intervalsByColumn,
                                                          @NotNull List<EntranceFootprintColumn> footprint)
    {
        CaveInterval best = null;
        int bestScore = Integer.MAX_VALUE;
        int minDepth = Math.max(1, TConfig.c.CAVES_DENSITY_V1_ENTRANCES_MINIMUM_CAVE_DEPTH);
        int maxDepth = Math.max(minDepth, TConfig.c.CAVES_DENSITY_V1_ENTRANCES_MAXIMUM_CAVE_DEPTH);
        int maxFloorDepth = maxDepth + DENSITY_V1_ENTRANCE_MAX_FLOOR_EXTRA_DEPTH;

        for (EntranceFootprintColumn footprintColumn : footprint) {
            int surfaceY = cache.getTransformedHeight(footprintColumn.localX(), footprintColumn.localZ());
            List<CaveInterval> intervals = intervalsByColumn[getColumnIndex(footprintColumn.localX(), footprintColumn.localZ())];
            if (intervals == null || intervals.isEmpty()) {
                continue;
            }

            for (CaveInterval interval : intervals) {
                int ceilingDepth = surfaceY - interval.ceilingAirY();
                int floorDepth = surfaceY - interval.floorSolidY();
                if (ceilingDepth < minDepth || ceilingDepth > maxDepth || floorDepth > maxFloorDepth) {
                    continue;
                }

                int score = ceilingDepth + (footprintColumn.depthStep() * 2) + Math.abs(footprintColumn.widthOffset());
                if (score < bestScore) {
                    best = interval;
                    bestScore = score;
                }
            }
        }
        return best;
    }

    private static boolean hasEntranceNeighborhoodMatch(@NotNull List<CaveInterval>[] intervalsByColumn,
                                                        int localX,
                                                        int localZ,
                                                        int validationRadius,
                                                        @NotNull CaveInterval targetInterval)
    {
        int matches = 0;
        for (int nx = localX - validationRadius; nx <= localX + validationRadius; nx++) {
            for (int nz = localZ - validationRadius; nz <= localZ + validationRadius; nz++) {
                if (!isInsideChunk(nx, nz)) {
                    return false;
                }
                if (columnMatchesEntranceTarget(intervalsByColumn[getColumnIndex(nx, nz)], targetInterval)) {
                    matches++;
                }
            }
        }
        return matches >= DENSITY_V1_ENTRANCE_NEIGHBORHOOD_MIN_MATCHES;
    }

    private static boolean isDensityEntranceFootprintValid(@NotNull ChunkCache cache,
                                                           @NotNull ChunkData chunkData,
                                                           @NotNull List<CaveInterval>[] intervalsByColumn,
                                                           @NotNull List<EntranceFootprintColumn> footprint,
                                                           @NotNull BlockFace direction,
                                                           @NotNull CaveInterval targetInterval)
    {
        int validColumns = 0;
        for (EntranceFootprintColumn footprintColumn : footprint) {
            int localX = footprintColumn.localX();
            int localZ = footprintColumn.localZ();
            if (!isSafeEntranceColumn(cache, chunkData, localX, localZ)) {
                continue;
            }
            if (!hasEntranceFaceExposure(cache, localX, localZ, direction)) {
                continue;
            }
            if (!columnOrNeighborMatchesTarget(intervalsByColumn, localX, localZ, targetInterval, 1)) {
                continue;
            }
            validColumns++;
        }

        int requiredValid = Math.max(
                1,
                (int) Math.ceil(footprint.size() * DENSITY_V1_ENTRANCE_MIN_VALID_FOOTPRINT_RATIO)
        );
        return validColumns >= requiredValid;
    }

    private static boolean columnOrNeighborMatchesTarget(@NotNull List<CaveInterval>[] intervalsByColumn,
                                                         int localX,
                                                         int localZ,
                                                         @NotNull CaveInterval targetInterval,
                                                         int radius)
    {
        for (int nx = localX - radius; nx <= localX + radius; nx++) {
            for (int nz = localZ - radius; nz <= localZ + radius; nz++) {
                if (!isInsideChunk(nx, nz)) {
                    continue;
                }
                if (columnMatchesEntranceTarget(intervalsByColumn[getColumnIndex(nx, nz)], targetInterval)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean columnMatchesEntranceTarget(List<CaveInterval> intervals, @NotNull CaveInterval targetInterval) {
        if (intervals == null || intervals.isEmpty()) {
            return false;
        }

        for (CaveInterval interval : intervals) {
            int overlap = Math.min(interval.ceilingAirY(), targetInterval.ceilingAirY())
                          - Math.max(interval.floorSolidY(), targetInterval.floorSolidY());
            if (overlap >= 3
                && Math.abs(interval.ceilingAirY() - targetInterval.ceilingAirY()) <= DENSITY_V1_ENTRANCE_INTERVAL_TOLERANCE)
            {
                return true;
            }
        }
        return false;
    }

    private static boolean isSafeEntranceColumn(@NotNull ChunkCache cache,
                                                @NotNull ChunkData chunkData,
                                                int localX,
                                                int localZ)
    {
        if (!isInsideChunk(localX, localZ)) {
            return false;
        }

        int surfaceY = cache.getTransformedHeight(localX, localZ);
        if (surfaceY <= seaLevel + DENSITY_V1_ENTRANCE_SEA_LEVEL_CLEARANCE) {
            return false;
        }
        if (!chunkData.getType(localX, surfaceY, localZ).isSolid()) {
            return false;
        }

        Material aboveSurface = chunkData.getType(
                localX,
                Math.min(surfaceY + 1, TerraformGeneratorPlugin.injector.getMaxY() - 1),
                localZ
        );
        return !BlockUtils.wetMaterials.contains(aboveSurface);
    }

    private static boolean hasEntranceFaceExposure(@NotNull ChunkCache cache, int localX, int localZ, @NotNull BlockFace direction) {
        int forwardX = localX + direction.getModX();
        int forwardZ = localZ + direction.getModZ();
        if (!isInsideChunk(forwardX, forwardZ)) {
            return false;
        }

        int surfaceY = cache.getTransformedHeight(localX, localZ);
        int forwardSurfaceY = cache.getTransformedHeight(forwardX, forwardZ);
        return surfaceY - forwardSurfaceY >= DENSITY_V1_ENTRANCE_FACE_DROP;
    }

    private static BlockFace getDominantEntranceDirection(@NotNull ChunkCache cache, int localX, int localZ) {
        int centerSurfaceY = cache.getTransformedHeight(localX, localZ);
        BlockFace bestDirection = null;
        int bestDrop = Integer.MIN_VALUE;
        int secondBestDrop = Integer.MIN_VALUE;

        for (BlockFace face : BlockUtils.directBlockFaces) {
            int neighborSurfaceY = cache.getTransformedHeight(localX + face.getModX(), localZ + face.getModZ());
            int drop = centerSurfaceY - neighborSurfaceY;
            if (drop > bestDrop) {
                secondBestDrop = bestDrop;
                bestDrop = drop;
                bestDirection = face;
            }
            else if (drop > secondBestDrop) {
                secondBestDrop = drop;
            }
        }

        if (bestDirection == null || bestDrop < TConfig.c.CAVES_DENSITY_V1_ENTRANCES_MINIMUM_SLOPE_DROP) {
            return null;
        }
        if (secondBestDrop != Integer.MIN_VALUE && bestDrop < secondBestDrop + DENSITY_V1_ENTRANCE_DOMINANT_MARGIN) {
            return null;
        }
        return bestDirection;
    }

    private static boolean isUntouchedEntranceFootprint(@NotNull List<EntranceFootprintColumn> footprint,
                                                        boolean @NotNull [] touched)
    {
        for (EntranceFootprintColumn footprintColumn : footprint) {
            if (!isInsideChunk(footprintColumn.localX(), footprintColumn.localZ())) {
                return false;
            }
            if (touched[getColumnIndex(footprintColumn.localX(), footprintColumn.localZ())]) {
                return false;
            }
        }
        return true;
    }

    private static void markTouchedColumn(int localX,
                                          int localZ,
                                          boolean @NotNull [] touched,
                                          @NotNull ArrayList<Integer> touchedOrder)
    {
        int index = getColumnIndex(localX, localZ);
        if (!touched[index]) {
            touched[index] = true;
            touchedOrder.add(index);
        }
    }

    private static boolean isInsideChunk(int localX, int localZ) {
        return localX >= 0 && localX < 16 && localZ >= 0 && localZ < 16;
    }

    private static int getColumnIndex(int localX, int localZ) {
        return localX + (localZ << 4);
    }

    private static @NotNull List<CaveInterval> carveLegacyAmbientCaves(@NotNull TerraformWorld tw,
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

    private static @NotNull List<CaveInterval> carveDensityFieldColumn(@NotNull CaveDensitySampler densitySampler,
                                                                       @NotNull ChunkCache cache,
                                                                       @NotNull ChunkData chunkData,
                                                                       @NotNull Random dontCareRandom,
                                                                       int localX,
                                                                       int localZ,
                                                                       int rawX,
                                                                       int rawZ,
                                                                       double surfaceHeight)
    {
        final int minY = TerraformGeneratorPlugin.injector.getMinY();
        final int invalHeight = minY - 1;
        int firstCaveAir = invalHeight;
        boolean surfaceResolved = false;
        boolean mustUpdateHeight = true;
        List<CoordPair> rawPairs = new ArrayList<>();

        for (int y = (int) surfaceHeight; y >= minY; y--) {
            if (y >= 0 && y <= 2) {
                chunkData.setBlock(localX, y, localZ, GenUtils.randChoice(
                        dontCareRandom,
                        CommonMat.DEEPSLATE,
                        CommonMat.STONE
                ));
            }

            boolean isCarved = false;
            if (TConfig.areCavesEnabled() && canDensityCarveAtY(y, surfaceHeight)) {
                float threshold = TConfig.c.CAVES_DENSITY_V1_THRESHOLD
                                  + getDensitySurfacePenalty(y, surfaceHeight)
                                  + getDensitySeaLevelPenalty(y, surfaceHeight);
                isCarved = densitySampler.sampleDensity(rawX, y, rawZ, surfaceHeight) >= threshold;
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

    private static boolean canDensityCarveAtY(int y, double surfaceHeight) {
        return y <= surfaceHeight - TConfig.c.CAVES_DENSITY_V1_SURFACE_NO_CARVE_CLEARANCE;
    }

    private static float getDensitySurfacePenalty(int y, double surfaceHeight) {
        int hardClearance = TConfig.c.CAVES_DENSITY_V1_SURFACE_NO_CARVE_CLEARANCE;
        int fullClearance = Math.max(hardClearance, TConfig.c.CAVES_DENSITY_V1_SURFACE_FULL_CARVE_CLEARANCE);
        if (fullClearance <= hardClearance) {
            return 0f;
        }

        double hardCeiling = surfaceHeight - hardClearance;
        double fullCarveY = surfaceHeight - fullClearance;
        if (y <= fullCarveY) {
            return 0f;
        }

        float ratio = (float) ((y - fullCarveY) / (hardCeiling - fullCarveY));
        return TConfig.c.CAVES_DENSITY_V1_SURFACE_MAX_THRESHOLD_PENALTY * clamp01(ratio);
    }

    private static float getDensitySeaLevelPenalty(int y, double surfaceHeight) {
        if (surfaceHeight > seaLevel + TConfig.c.CAVES_DENSITY_V1_SEA_LEVEL_COLUMN_BUFFER) {
            return 0f;
        }

        int fadeDepth = TConfig.c.CAVES_DENSITY_V1_SEA_LEVEL_FADE_DEPTH;
        if (fadeDepth <= 0) {
            return 0f;
        }

        int lowerBound = seaLevel - fadeDepth;
        if (y <= lowerBound) {
            return 0f;
        }

        float ratio = (float) (y - lowerBound) / fadeDepth;
        return TConfig.c.CAVES_DENSITY_V1_SEA_LEVEL_MAX_THRESHOLD_PENALTY * clamp01(ratio);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static @NotNull List<CaveInterval> toCaveIntervals(@NotNull Collection<CoordPair> rawPairs) {
        Collection<CoordPair> filteredCaveCeilFloors = MasterCavePopulatorDistributor.getFilteredPairs(
                rawPairs,
                MasterCavePopulatorDistributor.AMBIENT_MINIMUM_CAVE_HEIGHT
        );
        if (filteredCaveCeilFloors.isEmpty()) {
            return Collections.emptyList();
        }

        List<CaveInterval> caveIntervals = new ArrayList<>(filteredCaveCeilFloors.size());
        for (CoordPair pair : filteredCaveCeilFloors) {
            caveIntervals.add(new CaveInterval((short) pair.x(), (short) pair.z()));
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
