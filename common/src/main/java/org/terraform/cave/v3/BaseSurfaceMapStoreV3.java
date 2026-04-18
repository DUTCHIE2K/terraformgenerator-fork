package org.terraform.cave.v3;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.material.MaterialData;
import org.jetbrains.annotations.NotNull;
import org.terraform.biome.BiomeBank;
import org.terraform.biome.BiomeHandler;
import org.terraform.coregen.ChunkCache;
import org.terraform.coregen.HeightMap;
import org.terraform.coregen.bukkit.TerraformGenerator;
import org.terraform.data.TerraformWorld;
import org.terraform.main.config.TConfig;
import org.terraform.utils.datastructs.ConcurrentLRUCache;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public final class BaseSurfaceMapStoreV3 {
    private static final ConcurrentHashMap<TerraformWorld, SurfaceCaches> WORLD_CACHES = new ConcurrentHashMap<>();

    private BaseSurfaceMapStoreV3() {
    }

    public static @NotNull BaseSurfaceChunkV3 getBaseSurfaceChunk(@NotNull TerraformWorld tw, int chunkX, int chunkZ) {
        SurfaceCaches caches = getWorldCaches(tw);
        LocalChunkKey key = new LocalChunkKey(chunkX, chunkZ);
        try (CaveV3Profiler.Scope ignored = CaveV3Profiler.start("cave-v3.surface-chunk.get")) {
            BaseSurfaceChunkV3 existing = caches.chunks().getIfPresent(key);
            if (existing != null) {
                CaveV3Profiler.recordEvent("cave-v3.surface-chunk.cache-hit");
                return existing;
            }

            CaveV3Profiler.recordEvent("cave-v3.surface-chunk.cache-miss");
            return caches.chunks().get(key);
        }
    }

    public static boolean hasBaseSurfaceChunk(@NotNull TerraformWorld tw, int chunkX, int chunkZ) {
        SurfaceCaches caches = getWorldCaches(tw);
        return caches.chunks().getIfPresent(new LocalChunkKey(chunkX, chunkZ)) != null;
    }

    public static @NotNull BaseSurfaceMap getBaseSurfaceMap(@NotNull TerraformWorld tw,
                                                            int chunkX,
                                                            int chunkZ,
                                                            int padding)
    {
        return getWorldCaches(tw).maps().get(new LocalMapKey(chunkX, chunkZ, padding));
    }

    public static void clearWorld(@NotNull TerraformWorld tw) {
        WORLD_CACHES.remove(tw);
    }

    private static @NotNull SurfaceCaches getWorldCaches(@NotNull TerraformWorld tw) {
        return WORLD_CACHES.computeIfAbsent(tw, ignored -> createWorldCaches(tw));
    }

    private static @NotNull SurfaceCaches createWorldCaches(@NotNull TerraformWorld tw) {
        int chunkCacheSize = Math.max(256, TConfig.c.DEVSTUFF_CHUNKCACHE_SIZE);
        int mapCacheSize = Math.max(64, chunkCacheSize / 8);
        return new SurfaceCaches(
                new ConcurrentLRUCache<>(
                        "baseSurfaceChunkCache[" + tw.getName() + "]",
                        chunkCacheSize,
                        key -> buildBaseSurfaceChunk(tw, key.chunkX(), key.chunkZ())
                ),
                new ConcurrentLRUCache<>(
                        "baseSurfaceMapCache[" + tw.getName() + "]",
                        mapCacheSize,
                        key -> buildBaseSurfaceMap(tw, key.chunkX(), key.chunkZ(), key.padding())
                )
        );
    }

    private static @NotNull BaseSurfaceMap buildBaseSurfaceMap(@NotNull TerraformWorld tw,
                                                               int chunkX,
                                                               int chunkZ,
                                                               int padding)
    {
        int minRawX = (chunkX << 4) - padding;
        int maxRawX = (chunkX << 4) + 15 + padding;
        int minRawZ = (chunkZ << 4) - padding;
        int maxRawZ = (chunkZ << 4) + 15 + padding;
        BaseSurfaceMapBuilder builder = new BaseSurfaceMapBuilder(
                minRawX,
                minRawZ,
                (maxRawX - minRawX) + 1,
                (maxRawZ - minRawZ) + 1
        );
        for (int rawX = minRawX; rawX <= maxRawX; rawX++) {
            for (int rawZ = minRawZ; rawZ <= maxRawZ; rawZ++) {
                BaseSurfaceChunkV3 sourceChunk = getBaseSurfaceChunk(tw, rawX >> 4, rawZ >> 4);
                BaseSurfaceColumn column = sourceChunk.getColumn(rawX & 0xF, rawZ & 0xF);
                builder.recordColumn(rawX, rawZ, column.baseSurfaceY(), column.safety(), column.topState());
            }
        }
        return builder.build();
    }

    private static @NotNull BaseSurfaceChunkV3 buildBaseSurfaceChunk(@NotNull TerraformWorld tw, int chunkX, int chunkZ) {
        try (CaveV3Profiler.Scope ignored = CaveV3Profiler.start("cave-v3.surface-chunk.build")) {
            ChunkCache cache = new ChunkCache(tw, chunkX, chunkZ);
            short[] rawTerrainHeights = new short[256];
            try (CaveV3Profiler.Scope inner = CaveV3Profiler.start("cave-v3.surface-chunk.seed-heights")) {
                seedRawTerrainHeights(tw, chunkX, chunkZ, cache, rawTerrainHeights);
            }
            SurfaceCaptureChunkData capture = new SurfaceCaptureChunkData(tw, chunkX, chunkZ, rawTerrainHeights);
            try (CaveV3Profiler.Scope inner = CaveV3Profiler.start("cave-v3.surface-chunk.apply-transforms")) {
                applyChunkTerrainTransforms(tw, chunkX, chunkZ, rawTerrainHeights, cache, capture);
            }

            BaseSurfaceColumn[] columns = new BaseSurfaceColumn[256];
            try (CaveV3Profiler.Scope inner = CaveV3Profiler.start("cave-v3.surface-chunk.classify-columns")) {
                for (int localX = 0; localX < 16; localX++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        int index = index(localX, localZ);
                        int baseSurfaceY = cache.getTransformedHeight(localX, localZ);
                        Material aboveSurface = capture.getType(localX, baseSurfaceY + 1, localZ);
                        SurfaceTopState topState = classifyTopState(aboveSurface);
                        SurfaceSafety safety = topState == SurfaceTopState.WATER_EXPOSED ? SurfaceSafety.WET : SurfaceSafety.DRY;
                        columns[index] = new BaseSurfaceColumn((short) baseSurfaceY, safety, topState);
                    }
                }
            }

            BaseSurfaceChunkV3.SurfaceBlockWrite[][] surfaceWritesByColumn;
            try (CaveV3Profiler.Scope inner = CaveV3Profiler.start("cave-v3.surface-chunk.pack-writes")) {
                surfaceWritesByColumn = capture.toSurfaceWritesByColumn();
            }
            return new BaseSurfaceChunkV3(rawTerrainHeights, columns, surfaceWritesByColumn);
        }
    }

    private static void seedRawTerrainHeights(@NotNull TerraformWorld tw,
                                              int chunkX,
                                              int chunkZ,
                                              @NotNull ChunkCache cache,
                                              short @NotNull [] rawTerrainHeights)
    {
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int rawX = chunkX * 16 + localX;
                int rawZ = chunkZ * 16 + localZ;
                short rawTerrainY = (short) HeightMap.getPreciseHeight(tw, rawX, rawZ);
                rawTerrainHeights[index(localX, localZ)] = rawTerrainY;
                cache.writeTransformedHeight(localX, localZ, rawTerrainY);
            }
        }
    }

    private static void applyChunkTerrainTransforms(@NotNull TerraformWorld tw,
                                                    int chunkX,
                                                    int chunkZ,
                                                    short @NotNull [] rawTerrainHeights,
                                                    @NotNull ChunkCache cache,
                                                    ChunkGenerator.@NotNull ChunkData chunkData)
    {
        Random random = tw.getHashedRand(chunkX, chunkZ, 31278);
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int rawX = chunkX * 16 + localX;
                int rawZ = chunkZ * 16 + localZ;
                int rawTerrainY = rawTerrainHeights[index(localX, localZ)];
                BiomeBank bank = tw.getBiomeBank(rawX, rawTerrainY, rawZ);
                BiomeHandler transformHandler = bank.getHandler().getTransformHandler();
                if (transformHandler != null) {
                    transformHandler.transformTerrain(cache, tw, random, chunkData, localX, localZ, chunkX, chunkZ);
                }
            }
        }
    }

    private static @NotNull SurfaceTopState classifyTopState(@NotNull Material material) {
        if (material == Material.WATER) {
            return SurfaceTopState.WATER_EXPOSED;
        }
        if (material.isAir()) {
            return SurfaceTopState.AIR_EXPOSED;
        }
        return SurfaceTopState.SOLID_COVERED;
    }

    private static int index(int localX, int localZ) {
        return localX + (localZ << 4);
    }

    private record SurfaceCaches(@NotNull ConcurrentLRUCache<LocalChunkKey, BaseSurfaceChunkV3> chunks,
                                 @NotNull ConcurrentLRUCache<LocalMapKey, BaseSurfaceMap> maps) {
    }

    private record LocalChunkKey(int chunkX, int chunkZ) {
    }

    private record LocalMapKey(int chunkX, int chunkZ, int padding) {
    }

    private static final class SurfaceCaptureChunkData implements ChunkGenerator.ChunkData {
        private final @NotNull TerraformWorld tw;
        private final int chunkX;
        private final int chunkZ;
        private final short[] rawTerrainHeights;
        private final ColumnOverrideBuffer[] blockOverridesByColumn = new ColumnOverrideBuffer[256];

        private SurfaceCaptureChunkData(@NotNull TerraformWorld tw, int chunkX, int chunkZ, short[] rawTerrainHeights) {
            this.tw = tw;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.rawTerrainHeights = rawTerrainHeights;
        }

        @Override
        public int getMinHeight() {
            return tw.minY;
        }

        @Override
        public int getMaxHeight() {
            return tw.maxY;
        }

        @NotNull
        @Override
        public Biome getBiome(int x, int y, int z) {
            int rawX = (chunkX << 4) + x;
            int rawZ = (chunkZ << 4) + z;
            return tw.getBiomeBank(rawX, y, rawZ).getHandler().getBiome();
        }

        @Override
        public void setBlock(int x, int y, int z, @NotNull Material material) {
            setMaterial(x, y, z, material);
        }

        @Override
        public void setBlock(int x, int y, int z, @NotNull MaterialData materialData) {
            setMaterial(x, y, z, materialData.getItemType());
        }

        @Override
        public void setBlock(int x, int y, int z, @NotNull BlockData blockData) {
            setMaterial(x, y, z, blockData.getMaterial());
        }

        @Override
        public void setRegion(int xMin,
                              int yMin,
                              int zMin,
                              int xMax,
                              int yMax,
                              int zMax,
                              @NotNull Material material)
        {
            setRegionMaterial(xMin, yMin, zMin, xMax, yMax, zMax, material);
        }

        @Override
        public void setRegion(int xMin,
                              int yMin,
                              int zMin,
                              int xMax,
                              int yMax,
                              int zMax,
                              @NotNull MaterialData materialData)
        {
            setRegionMaterial(xMin, yMin, zMin, xMax, yMax, zMax, materialData.getItemType());
        }

        @Override
        public void setRegion(int xMin,
                              int yMin,
                              int zMin,
                              int xMax,
                              int yMax,
                              int zMax,
                              @NotNull BlockData blockData)
        {
            setRegionMaterial(xMin, yMin, zMin, xMax, yMax, zMax, blockData.getMaterial());
        }

        @NotNull
        @Override
        public Material getType(int x, int y, int z) {
            if (!isInsideChunk(x, z)) {
                return Material.AIR;
            }
            ColumnOverrideBuffer columnOverrides = blockOverridesByColumn[index(x, z)];
            Material overridden = columnOverrides == null ? null : columnOverrides.get(y);
            return overridden != null ? overridden : fallbackMaterial(x, y, z);
        }

        @NotNull
        @Override
        public MaterialData getTypeAndData(int x, int y, int z) {
            return new MaterialData(getType(x, y, z));
        }

        @NotNull
        @Override
        public BlockData getBlockData(int x, int y, int z) {
            return getType(x, y, z).createBlockData();
        }

        @Override
        public byte getData(int x, int y, int z) {
            return getTypeAndData(x, y, z).getData();
        }

        private void setRegionMaterial(int xMin,
                                       int yMin,
                                       int zMin,
                                       int xMax,
                                       int yMax,
                                       int zMax,
                                       @NotNull Material material)
        {
            for (int x = xMin; x < xMax; x++) {
                for (int y = yMin; y < yMax; y++) {
                    for (int z = zMin; z < zMax; z++) {
                        setMaterial(x, y, z, material);
                    }
                }
            }
        }

        private void setMaterial(int x, int y, int z, @NotNull Material material) {
            if (!isInsideChunk(x, z)) {
                return;
            }
            int columnIndex = index(x, z);
            ColumnOverrideBuffer columnOverrides = blockOverridesByColumn[columnIndex];
            Material fallback = fallbackMaterial(x, y, z);
            if (material == fallback) {
                if (columnOverrides != null && columnOverrides.remove(y) && columnOverrides.isEmpty()) {
                    blockOverridesByColumn[columnIndex] = null;
                }
            }
            else {
                if (columnOverrides == null) {
                    columnOverrides = new ColumnOverrideBuffer();
                    blockOverridesByColumn[columnIndex] = columnOverrides;
                }
                columnOverrides.put(y, material);
            }
        }

        private @NotNull Material fallbackMaterial(int x, int y, int z) {
            int rawTerrainY = rawTerrainHeights[index(x, z)];
            if (y <= rawTerrainY) {
                return y < 0 ? Material.DEEPSLATE : Material.STONE;
            }
            if (y <= TerraformGenerator.seaLevel) {
                return Material.WATER;
            }
            return Material.AIR;
        }

        private BaseSurfaceChunkV3.SurfaceBlockWrite[] @NotNull [] toSurfaceWritesByColumn() {
            try (CaveV3Profiler.Scope ignored = CaveV3Profiler.start("cave-v3.surface-chunk.collect-writes")) {
                BaseSurfaceChunkV3.SurfaceBlockWrite[][] result = new BaseSurfaceChunkV3.SurfaceBlockWrite[256][];
                for (int columnIndex = 0; columnIndex < result.length; columnIndex++) {
                    ColumnOverrideBuffer columnWrites = blockOverridesByColumn[columnIndex];
                    result[columnIndex] = columnWrites == null
                                          ? BaseSurfaceChunkV3.NO_WRITES
                                          : columnWrites.toSurfaceWrites();
                }
                return result;
            }
        }

        private boolean isInsideChunk(int x, int z) {
            return x >= 0 && x < 16 && z >= 0 && z < 16;
        }

        private static final class ColumnOverrideBuffer {
            private short[] ys = new short[4];
            private Material[] materials = new Material[4];
            private int size;

            private @NotNull BaseSurfaceChunkV3.SurfaceBlockWrite[] toSurfaceWrites() {
                if (size == 0) {
                    return BaseSurfaceChunkV3.NO_WRITES;
                }

                BaseSurfaceChunkV3.SurfaceBlockWrite[] writes = new BaseSurfaceChunkV3.SurfaceBlockWrite[size];
                for (int i = 0; i < size; i++) {
                    writes[i] = new BaseSurfaceChunkV3.SurfaceBlockWrite(ys[i], materials[i]);
                }
                return writes;
            }

            private Material get(int y) {
                int index = indexOf(y);
                return index < 0 ? null : materials[index];
            }

            private void put(int y, @NotNull Material material) {
                int index = indexOf(y);
                if (index >= 0) {
                    materials[index] = material;
                    return;
                }

                ensureCapacity(size + 1);
                ys[size] = (short) y;
                materials[size] = material;
                size++;
            }

            private boolean remove(int y) {
                int index = indexOf(y);
                if (index < 0) {
                    return false;
                }

                int lastIndex = size - 1;
                if (index != lastIndex) {
                    ys[index] = ys[lastIndex];
                    materials[index] = materials[lastIndex];
                }
                materials[lastIndex] = null;
                size = lastIndex;
                return true;
            }

            private boolean isEmpty() {
                return size == 0;
            }

            private int indexOf(int y) {
                short targetY = (short) y;
                for (int i = size - 1; i >= 0; i--) {
                    if (ys[i] == targetY) {
                        return i;
                    }
                }
                return -1;
            }

            private void ensureCapacity(int requiredSize) {
                if (requiredSize <= ys.length) {
                    return;
                }

                int newLength = Math.max(requiredSize, ys.length << 1);
                ys = Arrays.copyOf(ys, newLength);
                materials = Arrays.copyOf(materials, newLength);
            }
        }
    }
}
