package org.terraform.coregen.bukkit;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.terraform.cave.v3.CaveSnapshotStoreV3;
import org.terraform.cave.v3.CaveSnapshotV3;
import org.terraform.coregen.ChunkCache;
import org.terraform.coregen.HeightMap;
import org.terraform.coregen.TerraformPopulator;
import org.terraform.coregen.density.TerrainCaveChunkGenerator;
import org.terraform.coregen.density.TerrainColumnPreparer;
import org.terraform.data.SimpleChunkLocation;
import org.terraform.data.TWCoordPair;
import org.terraform.data.TerraformWorld;
import org.terraform.main.TerraformGeneratorPlugin;
import org.terraform.main.config.TConfig;
import org.terraform.utils.datastructs.ConcurrentLRUCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TerraformGenerator extends ChunkGenerator {
    private static final TerrainCaveChunkGenerator TERRAIN_CAVE_CHUNK_GENERATOR = new TerrainCaveChunkGenerator();
    public static final List<SimpleChunkLocation> preWorldInitGen = new ArrayList<>();
    public static ConcurrentLRUCache<TWCoordPair, ChunkCache> CHUNK_CACHE;
    public static int seaLevel = 62;

    public static void updateSeaLevelFromConfig() {
        seaLevel = TConfig.c.HEIGHT_MAP_SEA_LEVEL;
    }

    /**
     * @param x chunk X
     * @param z chunk Z
     */
    public static @NotNull ChunkCache getCache(TerraformWorld tw, int x, int z) {
        return CHUNK_CACHE.get(new TWCoordPair(tw, x, z));
    }

    public static void buildFilledCache(@NotNull TerraformWorld tw, int chunkX, int chunkZ, @NotNull ChunkCache cache) {
        synchronized (cache) {
            if (cache.areTransformedHeightsFilled()) {
                return;
            }

            TerrainColumnPreparer.PreparedChunk preparedChunk = TERRAIN_CAVE_CHUNK_GENERATOR.prepareChunk(
                    tw,
                    chunkX,
                    chunkZ
            );
            TERRAIN_CAVE_CHUNK_GENERATOR.fillTransformedHeightCache(tw, cache, chunkX, chunkZ, preparedChunk);
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
     */
    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo,
                              @NotNull Random dontCareRandom,
                              int chunkX,
                              int chunkZ,
                              @NotNull ChunkData chunkData)
    {
        TerraformGeneratorPlugin.watchdogSuppressant.tickWatchdog();

        TerraformWorld tw = TerraformWorld.get(worldInfo.getName(), worldInfo.getSeed());
        ChunkCache cache = getCache(tw, chunkX, chunkZ);
        TerrainColumnPreparer.PreparedChunk preparedChunk = TERRAIN_CAVE_CHUNK_GENERATOR.prepareChunk(
                tw,
                chunkX,
                chunkZ
        );

        synchronized (cache) {
            CaveSnapshotV3 snapshot = TERRAIN_CAVE_CHUNK_GENERATOR.generateChunkRuntime(
                    tw,
                    cache,
                    dontCareRandom,
                    chunkX,
                    chunkZ,
                    chunkData,
                    preparedChunk
            );
            CaveSnapshotStoreV3.publishGameplay(tw, chunkX, chunkZ, snapshot);
        }
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
        return List.of(new TerraformPopulator(), new TerraformBukkitBlockPopulator(tw));
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
