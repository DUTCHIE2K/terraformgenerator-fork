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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public final class BaseSurfaceMapStoreV3 {
    private static final ConcurrentHashMap<BaseSurfaceChunkKey, BaseSurfaceChunkV3> CHUNKS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BaseSurfaceMapKey, BaseSurfaceMap> MAPS = new ConcurrentHashMap<>();

    private BaseSurfaceMapStoreV3() {
    }

    public static @NotNull BaseSurfaceChunkV3 getBaseSurfaceChunk(@NotNull TerraformWorld tw, int chunkX, int chunkZ) {
        BaseSurfaceChunkKey key = new BaseSurfaceChunkKey(tw, chunkX, chunkZ);
        return CHUNKS.computeIfAbsent(key, ignored -> buildBaseSurfaceChunk(tw, chunkX, chunkZ));
    }

    public static @NotNull BaseSurfaceMap getBaseSurfaceMap(@NotNull TerraformWorld tw,
                                                            int chunkX,
                                                            int chunkZ,
                                                            int padding)
    {
        BaseSurfaceMapKey key = new BaseSurfaceMapKey(tw, chunkX, chunkZ, padding);
        return MAPS.computeIfAbsent(key, ignored -> buildBaseSurfaceMap(tw, chunkX, chunkZ, padding));
    }

    public static void clearWorld(@NotNull TerraformWorld tw) {
        CHUNKS.keySet().removeIf(key -> key.tw().getName().equals(tw.getName()));
        MAPS.keySet().removeIf(key -> key.tw().getName().equals(tw.getName()));
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
        ChunkCache cache = new ChunkCache(tw, chunkX, chunkZ);
        short[] rawTerrainHeights = new short[256];
        seedRawTerrainHeights(tw, chunkX, chunkZ, cache, rawTerrainHeights);
        SurfaceCaptureChunkData capture = new SurfaceCaptureChunkData(tw, chunkX, chunkZ, rawTerrainHeights);
        applyChunkTerrainTransforms(tw, chunkX, chunkZ, rawTerrainHeights, cache, capture);

        BaseSurfaceColumn[] columns = new BaseSurfaceColumn[256];
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
        return new BaseSurfaceChunkV3(rawTerrainHeights, columns, capture.toSurfaceWritesByColumn());
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

    private record BaseSurfaceChunkKey(@NotNull TerraformWorld tw, int chunkX, int chunkZ) {
    }

    private record BaseSurfaceMapKey(@NotNull TerraformWorld tw, int chunkX, int chunkZ, int padding) {
    }

    private static final class SurfaceCaptureChunkData implements ChunkGenerator.ChunkData {
        private static final int Y_OFFSET = 2048;

        private final @NotNull TerraformWorld tw;
        private final int chunkX;
        private final int chunkZ;
        private final short[] rawTerrainHeights;
        private final LinkedHashMap<Integer, Material> blockOverrides = new LinkedHashMap<>();

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
            int key = packBlock(x, y, z);
            Material overridden = blockOverrides.get(key);
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
            int key = packBlock(x, y, z);
            Material fallback = fallbackMaterial(x, y, z);
            if (material == fallback) {
                blockOverrides.remove(key);
            }
            else {
                blockOverrides.put(key, material);
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
            @SuppressWarnings("unchecked")
            ArrayList<BaseSurfaceChunkV3.SurfaceBlockWrite>[] writesByColumn = new ArrayList[256];
            for (Map.Entry<Integer, Material> entry : blockOverrides.entrySet()) {
                int packed = entry.getKey();
                int localX = packed & 0xF;
                int localZ = (packed >> 4) & 0xF;
                int columnIndex = index(localX, localZ);
                ArrayList<BaseSurfaceChunkV3.SurfaceBlockWrite> columnWrites = writesByColumn[columnIndex];
                if (columnWrites == null) {
                    columnWrites = new ArrayList<>();
                    writesByColumn[columnIndex] = columnWrites;
                }
                columnWrites.add(new BaseSurfaceChunkV3.SurfaceBlockWrite(
                        packed & 0xF,
                        decodeY(packed),
                        (packed >> 4) & 0xF,
                        entry.getValue()
                ));
            }
            BaseSurfaceChunkV3.SurfaceBlockWrite[][] result = new BaseSurfaceChunkV3.SurfaceBlockWrite[256][];
            for (int columnIndex = 0; columnIndex < result.length; columnIndex++) {
                ArrayList<BaseSurfaceChunkV3.SurfaceBlockWrite> columnWrites = writesByColumn[columnIndex];
                result[columnIndex] = columnWrites == null
                                      ? new BaseSurfaceChunkV3.SurfaceBlockWrite[0]
                                      : columnWrites.toArray(new BaseSurfaceChunkV3.SurfaceBlockWrite[0]);
            }
            return result;
        }

        private boolean isInsideChunk(int x, int z) {
            return x >= 0 && x < 16 && z >= 0 && z < 16;
        }

        private int packBlock(int x, int y, int z) {
            return ((y + Y_OFFSET) << 8) | (z << 4) | x;
        }

        private int decodeY(int packed) {
            return (packed >> 8) - Y_OFFSET;
        }
    }
}
