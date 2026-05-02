package org.terraform.biome.flat;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.terraform.biome.BiomeBank;
import org.terraform.biome.BiomeBlender;
import org.terraform.biome.BiomeHandler;
import org.terraform.coregen.ChunkCache;
import org.terraform.coregen.HeightMap;
import org.terraform.coregen.populatordata.PopulatorDataAbstract;
import org.terraform.data.TerraformWorld;
import org.terraform.main.config.TConfig;
import org.terraform.utils.GenUtils;
import org.terraform.utils.noise.FastNoise;
import org.terraform.utils.noise.NoiseCacheHandler;
import org.terraform.utils.noise.NoiseCacheHandler.NoiseCacheEntry;

import java.util.Random;

public class ErodedPlainsHandler extends BiomeHandler {
    static final BiomeHandler plainsHandler = BiomeBank.PLAINS.getHandler();
    static final boolean slabs = TConfig.c.MISC_USE_SLABS_TO_SMOOTH;
    static BiomeBlender biomeBlender;

    private static @NotNull BiomeBlender getBiomeBlender(TerraformWorld tw) {
        if (biomeBlender == null) {
            biomeBlender = new BiomeBlender(tw, true, true).setRiverThreshold(4).setBlendBeaches(false);
        }
        return biomeBlender;
    }

    public static ErodedPlainsColumnTransform sampleTransform(@NotNull TerraformWorld tw,
                                                              @NotNull Random random,
                                                              int rawTerrainY,
                                                              int rawX,
                                                              int rawZ)
    {
        FastNoise noise = NoiseCacheHandler.getNoise(tw, NoiseCacheEntry.BIOME_ERODEDPLAINS_CLIFFNOISE, world -> {
            FastNoise n = new FastNoise();
            n.SetNoiseType(FastNoise.NoiseType.CubicFractal);
            n.SetFractalOctaves(3);
            n.SetFrequency(0.02f);
            return n;
        });

        FastNoise details = NoiseCacheHandler.getNoise(tw, NoiseCacheEntry.BIOME_ERODEDPLAINS_DETAILS, world -> {
            FastNoise n = new FastNoise();
            n.SetNoiseType(FastNoise.NoiseType.SimplexFractal);
            n.SetFrequency(0.03f);
            return n;
        });

        double threshold = 0.1;
        int heightFactor = 10;
        double noiseValue = Math.max(0, noise.GetNoise(rawX, rawZ))
                            * getBiomeBlender(tw).getEdgeFactor(BiomeBank.ERODED_PLAINS, rawX, rawZ);
        double detailsValue = details.GetNoise(rawX, rawZ);

        double d = (noiseValue / threshold) - (int) (noiseValue / threshold) - 0.5;
        double platformHeight = (int) (noiseValue / threshold) * heightFactor
                                + (64 * Math.pow(d, 7) * heightFactor)
                                + detailsValue * heightFactor * 0.5;

        int roundedPlatformHeight = (int) Math.round(platformHeight);
        int newHeight = rawTerrainY + roundedPlatformHeight;
        if (newHeight < rawTerrainY) {
            return null;
        }

        int raise = newHeight - rawTerrainY;
        Material[] addedTopShellMaterials = new Material[raise];
        for (int y = rawTerrainY + 1; y <= newHeight; y++) {
            Material material = GenUtils.randChoice(random,
                    Material.STONE,
                    Material.STONE,
                    Material.STONE,
                    Material.STONE,
                    Material.COBBLESTONE,
                    Material.COBBLESTONE,
                    Material.MOSSY_COBBLESTONE,
                    Material.ANDESITE
            );
            if (slabs
                && y == newHeight
                && platformHeight - (int) platformHeight >= 0.5)
            {
                material = Material.getMaterial(material.name() + "_SLAB");
            }
            if (material == null) {
                throw new IllegalStateException("Missing slab material for eroded plains top shell");
            }
            addedTopShellMaterials[y - rawTerrainY - 1] = material;
        }

        if (detailsValue < 0.2 && GenUtils.chance(random, 3, 4) && raise > 0) {
            addedTopShellMaterials[raise - 1] = Material.GRASS_BLOCK;
        }

        return new ErodedPlainsColumnTransform(addedTopShellMaterials);
    }

    @Override
    public boolean isOcean() {
        return plainsHandler.isOcean();
    }

    @Override
    public Biome getBiome() {
        return plainsHandler.getBiome();
    }

    @Override
    public Material[] getSurfaceCrust(Random rand) {
        return plainsHandler.getSurfaceCrust(rand);
    }

    @Override
    public void populateSmallItems(TerraformWorld world,
                                   Random random,
                                   int rawX,
                                   int surfaceY,
                                   int rawZ,
                                   PopulatorDataAbstract data)
    {
        plainsHandler.populateSmallItems(world, random, rawX, surfaceY, rawZ, data);
    }

    @Override
    public BiomeHandler getTransformHandler() {
        return this;
    }

    @Override
    public void transformTerrain(@NotNull ChunkCache cache,
                                 TerraformWorld tw,
                                 Random random,
                                 ChunkGenerator.@NotNull ChunkData chunk,
                                 int x,
                                 int z,
                                 int chunkX,
                                 int chunkZ)
    {
        int rawX = chunkX * 16 + x;
        int rawZ = chunkZ * 16 + z;
        int rawTerrainY = (int) HeightMap.getPreciseHeight(tw, rawX, rawZ);
        ErodedPlainsColumnTransform transform = sampleTransform(tw, random, rawTerrainY, rawX, rawZ);
        if (transform == null) {
            return;
        }

        Material[] addedTopShellMaterials = transform.addedTopShellMaterials();
        cache.writeTransformedHeight(x, z, (short) (rawTerrainY + addedTopShellMaterials.length));
        for (int shellIndex = 0; shellIndex < addedTopShellMaterials.length; shellIndex++) {
            chunk.setBlock(x, rawTerrainY + shellIndex + 1, z, addedTopShellMaterials[shellIndex]);
        }
    }

    @Override
    public void populateLargeItems(TerraformWorld tw, Random random, PopulatorDataAbstract data) {
        plainsHandler.populateLargeItems(tw, random, data);
    }

    public record ErodedPlainsColumnTransform(Material @NotNull [] addedTopShellMaterials) {
    }
}
