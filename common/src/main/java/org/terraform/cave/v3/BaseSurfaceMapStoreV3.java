package org.terraform.cave.v3;

import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.terraform.biome.BiomeBank;
import org.terraform.biome.BiomeHandler;
import org.terraform.coregen.ChunkCache;
import org.terraform.coregen.HeightMap;
import org.terraform.coregen.bukkit.TerraformGenerator;
import org.terraform.data.DudChunkData;
import org.terraform.data.TerraformWorld;

import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public final class BaseSurfaceMapStoreV3 {
    private static final ChunkGenerator.ChunkData DUD = new DudChunkData();
    private static final ConcurrentHashMap<BaseSurfaceMapKey, BaseSurfaceMap> MAPS = new ConcurrentHashMap<>();

    private BaseSurfaceMapStoreV3() {
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
        int minChunkX = Math.floorDiv(minRawX, 16);
        int maxChunkX = Math.floorDiv(maxRawX, 16);
        int minChunkZ = Math.floorDiv(minRawZ, 16);
        int maxChunkZ = Math.floorDiv(maxRawZ, 16);
        HashMap<Long, ChunkCache> prepassCaches = new HashMap<>();

        for (int sourceChunkX = minChunkX; sourceChunkX <= maxChunkX; sourceChunkX++) {
            for (int sourceChunkZ = minChunkZ; sourceChunkZ <= maxChunkZ; sourceChunkZ++) {
                ChunkCache localCache = new ChunkCache(tw, sourceChunkX, sourceChunkZ);
                seedChunkSurfaceHeights(tw, sourceChunkX, sourceChunkZ, localCache);
                applyChunkTerrainTransforms(tw, sourceChunkX, sourceChunkZ, localCache);
                prepassCaches.put(packChunkCoords(sourceChunkX, sourceChunkZ), localCache);
            }
        }

        BaseSurfaceMapBuilder builder = new BaseSurfaceMapBuilder(minRawX, minRawZ, (maxRawX - minRawX) + 1, (maxRawZ - minRawZ) + 1);
        for (int rawX = minRawX; rawX <= maxRawX; rawX++) {
            for (int rawZ = minRawZ; rawZ <= maxRawZ; rawZ++) {
                ChunkCache localCache = prepassCaches.get(packChunkCoords(rawX >> 4, rawZ >> 4));
                int baseSurfaceY = localCache.getTransformedHeight(rawX & 0xF, rawZ & 0xF);
                SurfaceTopState topState = baseSurfaceY < TerraformGenerator.seaLevel
                                           ? SurfaceTopState.WATER_EXPOSED
                                           : SurfaceTopState.AIR_EXPOSED;
                SurfaceSafety safety = topState == SurfaceTopState.WATER_EXPOSED ? SurfaceSafety.WET : SurfaceSafety.DRY;
                builder.recordColumn(rawX, rawZ, baseSurfaceY, safety, topState);
            }
        }
        return builder.build();
    }

    private static void seedChunkSurfaceHeights(@NotNull TerraformWorld tw, int chunkX, int chunkZ, @NotNull ChunkCache cache) {
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
                                                    @NotNull ChunkCache cache)
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
                    transformHandler.transformTerrain(cache, tw, random, DUD, x, z, chunkX, chunkZ);
                }
            }
        }
    }

    private static long packChunkCoords(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }

    private record BaseSurfaceMapKey(@NotNull TerraformWorld tw, int chunkX, int chunkZ, int padding) {
    }
}
