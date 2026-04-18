package org.terraform.coregen;

import org.jetbrains.annotations.NotNull;
import org.terraform.cave.v3.CaveColumnV3;
import org.terraform.cave.v3.CaveIntervalMetadata;
import org.terraform.cave.v3.CaveIntervalV3;
import org.terraform.cave.v3.CaveSnapshotV3;
import org.terraform.cave.v3.SurfaceConnectivity;
import org.terraform.biome.BiomeBank;
import org.terraform.biome.cavepopulators.MasterCavePopulatorDistributor;
import org.terraform.data.TerraformWorld;
import org.terraform.main.TerraformGeneratorPlugin;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/**
 * I don't know why Z and X indices are swapped consistently here.
 * I guess it doesn't affect anything but it sure is a fucking
 * war crime
 */
public class ChunkCache {
    public enum PrefillType {
        NONE,
        SURFACE,
        FULL_COLUMN
    }

    public enum PrefillScheduleResult {
        FILLED,
        SCHEDULED,
        UPGRADED,
        ALREADY_SCHEDULED
    }

    public final TerraformWorld tw;
    public final int chunkX, chunkZ;
    public static final float CHUNKCACHE_INVAL = TerraformGeneratorPlugin.injector.getMinY() - 1;

    /**
     * heightCache caches the FINAL height of the terrain (the one applied to the
     * world).
     * <br>
     * dominantBiomeHeightCache holds the non-final height calculation of
     * the most dominant biome at those coordinates.
     * <br>
     * blurredHeightCache will hold intermediate height blurring values
     * (calculated after dominantBiomeHeightCache)
     */
    float[] heightMapCache;
    short[] highestGroundCache;
    short[] transformedGroundCache;
    float[] yBarrierNoiseCache;
    float[] bottomSealYCache;
    volatile boolean transformedHeightsFilled;
    volatile PrefillType prefillType;
    CompositeV3ChunkPrefill compositeV3ChunkPrefill;
    CaveSnapshotV3 gameplaySnapshotV3;

    BiomeBank[] biomeCache;

    public ChunkCache(TerraformWorld tw, int chunkX, int chunkZ) {
        this.tw = tw;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        initInternalCache();
    }

    private void initInternalCache() {
        heightMapCache = new float[256];
        Arrays.fill(heightMapCache, CHUNKCACHE_INVAL);
        transformedGroundCache = new short[256];
        Arrays.fill(transformedGroundCache, (short) CHUNKCACHE_INVAL);
        yBarrierNoiseCache = new float[256];
        Arrays.fill(yBarrierNoiseCache, CHUNKCACHE_INVAL);
        bottomSealYCache = new float[256];
        Arrays.fill(bottomSealYCache, CHUNKCACHE_INVAL);
        highestGroundCache = new short[256];
        Arrays.fill(highestGroundCache, (short) CHUNKCACHE_INVAL);
        transformedHeightsFilled = false;
        prefillType = PrefillType.NONE;
        compositeV3ChunkPrefill = null;
        gameplaySnapshotV3 = null;

        /*
        If arrays.fill gives further speed problems, just use
        System.arraycopy next time with a static final MIN_VALUE array
        Problems might occur because the lib itself uses a for loop.
        Optimization lies entirely at the mercy of the running JVM.
        */

        //11/4/2025 not fucking adding more things to the sacred array are ya???
        biomeCache = new BiomeBank[256];
    }

    public void cacheSolid(int interChunkX, int interChunkY, int interChunkZ)
    {
    }
    public void cacheNonSolid(int interChunkX, int interChunkY, int interChunkZ)
    {
    }
    public boolean isSolid(int interChunkX, int interChunkY, int interChunkZ)
    {
        return false;
    }

    public double getHeightMapHeight(int rawX, int rawZ) {
        return heightMapCache[(rawX & 0xF) + 16 * (rawZ & 0xF)];
    }

    public short getHighestGround(int rawX, int rawZ) {
        return highestGroundCache[(rawX & 0xF) + 16 * (rawZ & 0xF)];
    }

    /**
     * This is solely for surface cave carving use as surface
     * caves may modify heights.
     *
     * @return the ACTUAL mutable copy from the cache.
     */
    public short getTransformedHeight(int chunkSubX, int chunkSubZ) {
        return transformedGroundCache[chunkSubX + 16 * chunkSubZ];
    }

    public void writeTransformedHeight(int chunkSubX, int chunkSubZ, short val) {
        transformedGroundCache[chunkSubX + 16 * chunkSubZ] = val;
    }

    public boolean areTransformedHeightsFilled() {
        return transformedHeightsFilled;
    }

    public void markTransformedHeightsFilled() {
        transformedHeightsFilled = true;
        prefillType = PrefillType.NONE;
    }

    public synchronized PrefillScheduleResult tryScheduleSurfacePrefill() {
        if (transformedHeightsFilled) {
            return PrefillScheduleResult.FILLED;
        }
        if (prefillType != PrefillType.NONE) {
            return PrefillScheduleResult.ALREADY_SCHEDULED;
        }
        prefillType = PrefillType.SURFACE;
        return PrefillScheduleResult.SCHEDULED;
    }

    public synchronized PrefillScheduleResult tryScheduleFullColumnPrefill() {
        if (transformedHeightsFilled) {
            return PrefillScheduleResult.FILLED;
        }
        if (prefillType == PrefillType.NONE) {
            prefillType = PrefillType.FULL_COLUMN;
            return PrefillScheduleResult.SCHEDULED;
        }
        if (prefillType == PrefillType.SURFACE) {
            prefillType = PrefillType.FULL_COLUMN;
            return PrefillScheduleResult.UPGRADED;
        }
        return PrefillScheduleResult.ALREADY_SCHEDULED;
    }

    public synchronized boolean shouldPromoteSurfacePrefillToFullColumn() {
        return !transformedHeightsFilled && prefillType == PrefillType.FULL_COLUMN;
    }

    public synchronized PrefillType getPrefillType() {
        return prefillType;
    }

    public void clearPrefillScheduled() {
        prefillType = PrefillType.NONE;
    }

    public boolean hasCompositeV3ChunkPrefill() {
        return compositeV3ChunkPrefill != null;
    }

    public CompositeV3ChunkPrefill getCompositeV3ChunkPrefill() {
        return compositeV3ChunkPrefill;
    }

    public synchronized void cacheCompositeV3ChunkPrefill(CompositeV3ChunkPrefill prefill) {
        compositeV3ChunkPrefill = prefill;
    }

    public boolean hasGameplaySnapshotV3() {
        return gameplaySnapshotV3 != null;
    }

    public CaveSnapshotV3 getGameplaySnapshotV3() {
        return gameplaySnapshotV3;
    }

    public void cacheGameplaySnapshotV3(CaveSnapshotV3 snapshotV3) {
        gameplaySnapshotV3 = snapshotV3;
    }

    /**
     * As usual, the solution to any pain point is caching it like an idiot
     * @return the noise calculated in NoiseCaveRegistry.YBarrier. Only the noise.
     */
    public float getYBarrierNoise(int chunkSubX, int chunkSubZ) {
        return yBarrierNoiseCache[chunkSubX + 16 * chunkSubZ];
    }

    public void cacheYBarrierNoise(int chunkSubX, int chunkSubZ, float val) {
        yBarrierNoiseCache[chunkSubX + 16 * chunkSubZ] = val;
    }

    public float getBottomSealY(int chunkSubX, int chunkSubZ) {
        return bottomSealYCache[chunkSubX + 16 * chunkSubZ];
    }

    public void cacheBottomSealY(int chunkSubX, int chunkSubZ, float val) {
        bottomSealYCache[chunkSubX + 16 * chunkSubZ] = val;
    }

    /**
     * @param rawX  BLOCK COORD x
     * @param rawZ  BLOCK COORD z
     * @param value height to cache
     */
    public void cacheHeightMap(int rawX, int rawZ, double value) {
        heightMapCache[(rawX & 0xF) + 16 * (rawZ & 0xF)] = (float) value;
    }

    /**
     * @param rawX  BLOCK COORD x
     * @param rawZ  BLOCK COORD z
     * @param value height to cache
     */
    public void cacheHighestGround(int rawX, int rawZ, short value) {
        highestGroundCache[(rawX & 0xF) + 16 * (rawZ & 0xF)] = value;
    }

    public BiomeBank getBiome(int rawX, int rawZ) {
        return biomeCache[(rawX & 0xF) + 16 * (rawZ & 0xF)];
    }

    /**
     * @param rawX  BLOCK COORD x
     * @param rawZ  BLOCK COORD z
     * @param value biome to cache
     */
    public BiomeBank cacheBiome(int rawX, int rawZ, BiomeBank value) {
        biomeCache[(rawX & 0xF) + 16 * (rawZ & 0xF)] = value;
        return value;
    }

    /**
     * Nobody benchmarked the performance of this hashcode.
     * oh well.
     */
    @Override
    public int hashCode() {
        return tw.hashCode() ^ (chunkX + chunkZ * 31);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ChunkCache chunk)) {
            return false;
        }
        return this.tw == chunk.tw && this.chunkX == chunk.chunkX && this.chunkZ == chunk.chunkZ;
    }

    @Override
    public String toString(){
        return tw.getName() + ":" + chunkX + "," + chunkZ;
    }

    public static final class CompositeV3ChunkPrefill {
        private final CompositeV3ColumnPrefill[] columns;
        private volatile CaveSnapshotV3 snapshot;

        public CompositeV3ChunkPrefill(CompositeV3ColumnPrefill[] columns) {
            if (columns.length != 256) {
                throw new IllegalArgumentException("CompositeV3ChunkPrefill requires exactly 256 columns");
            }
            this.columns = columns;
        }

        public CompositeV3ColumnPrefill getColumn(int chunkSubX, int chunkSubZ) {
            return columns[chunkSubX + 16 * chunkSubZ];
        }

        public CaveSnapshotV3 toSnapshot(int chunkX, int chunkZ) {
            CaveSnapshotV3 cachedSnapshot = snapshot;
            if (cachedSnapshot != null) {
                return cachedSnapshot;
            }

            CaveColumnV3[] snapshotColumns = new CaveColumnV3[256];
            for (int i = 0; i < columns.length; i++) {
                CompositeV3ColumnPrefill column = columns[i];
                snapshotColumns[i] = new CaveColumnV3(
                        column.getBaseSurfaceY(),
                        column.getTransformedHeight(),
                        column.getCaveIntervals()
                );
            }

            CaveSnapshotV3 builtSnapshot = new CaveSnapshotV3(chunkX, chunkZ, snapshotColumns);
            snapshot = builtSnapshot;
            return builtSnapshot;
        }
    }

    public static final class CompositeV3ColumnPrefill {
        private final short baseSurfaceY;
        private final short transformedHeight;
        private final short[] carvedAirRuns;
        private volatile List<CaveIntervalV3> caveIntervals;

        public CompositeV3ColumnPrefill(short baseSurfaceY,
                                        short transformedHeight,
                                        short[] carvedAirRuns)
        {
            this.baseSurfaceY = baseSurfaceY;
            this.transformedHeight = transformedHeight;
            this.carvedAirRuns = carvedAirRuns;
        }

        public short getBaseSurfaceY() {
            return baseSurfaceY;
        }

        public short getTransformedHeight() {
            return transformedHeight;
        }

        public short[] getCarvedAirRuns() {
            return carvedAirRuns;
        }

        public List<CaveIntervalV3> getCaveIntervals() {
            List<CaveIntervalV3> cachedIntervals = caveIntervals;
            if (cachedIntervals != null) {
                return cachedIntervals;
            }

            List<CaveIntervalV3> builtIntervals = buildCaveIntervals(transformedHeight, carvedAirRuns);
            caveIntervals = builtIntervals;
            return builtIntervals;
        }

        private static @NotNull List<CaveIntervalV3> buildCaveIntervals(int topSolidY,
                                                                         short @NotNull [] carvedAirRuns)
        {
            if (carvedAirRuns.length == 0) {
                return List.of();
            }

            int minY = TerraformGeneratorPlugin.injector.getMinY();
            ArrayList<CaveIntervalV3> intervals = new ArrayList<>(carvedAirRuns.length / 2);
            for (int i = 0; i < carvedAirRuns.length; i += 2) {
                int bottomY = carvedAirRuns[i];
                int topY = carvedAirRuns[i + 1];
                if (bottomY <= minY || bottomY > topSolidY) {
                    continue;
                }

                int floorSolidY = bottomY - 1;
                if ((topY - floorSolidY) < MasterCavePopulatorDistributor.AMBIENT_MINIMUM_CAVE_HEIGHT) {
                    continue;
                }

                intervals.add(new CaveIntervalV3(
                        (short) topY,
                        (short) floorSolidY,
                        new CaveIntervalMetadata(topY > topSolidY ? SurfaceConnectivity.YES : SurfaceConnectivity.NO)
                ));
            }

            return intervals.isEmpty() ? List.of() : List.copyOf(intervals);
        }
    }
}
