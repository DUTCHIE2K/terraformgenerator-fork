package org.terraform.biome.flat;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockFace;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.terraform.biome.BiomeBank;
import org.terraform.biome.BiomeBlender;
import org.terraform.biome.BiomeHandler;
import org.terraform.coregen.ChunkCache;
import org.terraform.coregen.HeightMap;
import org.terraform.coregen.bukkit.TerraformGenerator;
import org.terraform.coregen.populatordata.PopulatorDataAbstract;
import org.terraform.data.SimpleBlock;
import org.terraform.data.SimpleLocation;
import org.terraform.data.TerraformWorld;
import org.terraform.main.config.TConfig;
import org.terraform.utils.BlockUtils;
import org.terraform.utils.GenUtils;
import org.terraform.utils.noise.FastNoise;
import org.terraform.utils.noise.NoiseCacheHandler;
import org.terraform.utils.noise.NoiseCacheHandler.NoiseCacheEntry;

import java.util.Random;

public class GorgeHandler extends BiomeHandler {
    private static final Material[] GORGE_RAISE_MATERIALS = new Material[] {
            Material.STONE,
            Material.STONE,
            Material.STONE,
            Material.STONE,
            Material.COBBLESTONE,
            Material.COBBLESTONE,
            Material.ANDESITE,
            Material.ANDESITE
    };
    static final BiomeHandler plainsHandler = BiomeBank.PLAINS.getHandler();
    static final boolean slabs = TConfig.c.MISC_USE_SLABS_TO_SMOOTH;
    static BiomeBlender biomeBlender;

    private static @NotNull BiomeBlender getBiomeBlender(TerraformWorld tw) {
        if (biomeBlender == null) {
            biomeBlender = new BiomeBlender(tw, true, true).setGridBlendingFactor(2).setSmoothBlendTowardsRivers(4);
        }
        return biomeBlender;
    }

    @Override
    public boolean isOcean() {
        return plainsHandler.isOcean();
    }

    @Override
    public Biome getBiome() {
        return plainsHandler.getBiome();
    }

    // Remove rivers from gorges.
    @Override
    public double calculateHeight(TerraformWorld tw, int x, int z) {
        double height = super.calculateHeight(tw, x, z);
        double riverDepth = HeightMap.getRawRiverDepth(tw, x, z);
        if (riverDepth > 0) {
            height += riverDepth;
        }
        return height;
    }

    @Override
    public Material[] getSurfaceCrust(Random rand) {
        return plainsHandler.getSurfaceCrust(rand);
    }

    @Override
    public void populateSmallItems(TerraformWorld world,
                                   @NotNull Random random,
                                   int rawX,
                                   int surfaceY,
                                   int rawZ,
                                   @NotNull PopulatorDataAbstract data)
    {

        SimpleBlock target = new SimpleBlock(data, rawX, surfaceY + 1, rawZ);
        boolean wasBelowSea = false;
        // the repair work is here because it needs the 3x3 boundary
        // for cave air that is BESIDE the water
        // DOES NOT change height truth because another block MUST be above the
        // one being changed due to the way this works
        while (target.getY() <= TerraformGenerator.seaLevel - 20) {
            wasBelowSea = true;
            if (target.getType() == Material.WATER) {
                for (BlockFace face : BlockUtils.directBlockFaces) {
                    if (BlockUtils.isAir(target.getRelative(face).getType())) {
                        target.getRelative(face).setType(Material.STONE);
                    }
                }
            }
            target = target.getUp();
        }

        // Do not do dry decorations if this was water
        if (wasBelowSea) {
            return;
        }

        target = target.getGround();

        if (!BlockUtils.isWet(target.getUp()) && target.getType() == Material.STONE) {
            // Make the ground more dynamic
            target.setType(Material.GRASS_BLOCK);
            target.getDown().setType(Material.DIRT);
            if (random.nextBoolean()) {
                target.getDown(2).setType(Material.DIRT);
                if (random.nextBoolean()) {
                    target.getDown(3).setType(Material.DIRT);
                }
            }
        }

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

        double preciseHeight = HeightMap.getPreciseHeight(tw, rawX, rawZ);
        GorgeColumnTransform transform = sampleTransform(tw, random, (int) preciseHeight, rawX, rawZ);
        if (transform.raiseHeight() > 0) {
            cache.writeTransformedHeight(x, z, (short) (transform.raiseHeight() + (int) preciseHeight));
            for (int y = 1; y <= transform.raiseHeight(); y++) {
                chunk.setBlock(x, (int) preciseHeight + y, z, transform.addedTopShellMaterials()[y - 1]);
            }
        }
        else if (transform.trimDepth() > 0) {
            int height = (int) preciseHeight;
            cache.writeTransformedHeight(x, z, (short) (height - transform.trimDepth()));
            for (int y = 0; y < transform.trimDepth(); y++) {
                int targetY = height - y;
                chunk.setBlock(
                        x,
                        targetY,
                        z,
                        targetY <= transform.removedTopWaterlineY() ? Material.WATER : Material.AIR
                );
            }

            if (height - transform.trimDepth() <= transform.removedTopWaterlineY()) {
                chunk.setBlock(x, height - transform.trimDepth(), z, Material.STONE);
            }
        }
    }

    public static @NotNull GorgeColumnTransform sampleTransform(@NotNull TerraformWorld tw,
                                                                @NotNull Random random,
                                                                int baseHeight,
                                                                int rawX,
                                                                int rawZ)
    {
        double threshold = 0.1;
        int heightFactor = 12;

        FastNoise cliffNoise = getCliffNoise(tw);
        FastNoise detailsNoise = getDetailsNoise(tw);

        double edgeFactor = getBiomeBlender(tw).getEdgeFactor(BiomeBank.GORGE, rawX, rawZ);
        double rawCliffNoiseVal = cliffNoise.GetNoise(rawX, rawZ);
        double noiseValue = rawCliffNoiseVal * edgeFactor;
        double detailsValue = detailsNoise.GetNoise(rawX, rawZ);

        if (noiseValue >= 0) {
            double d = (noiseValue / threshold) - (int) (noiseValue / threshold) - 0.5;
            double platformHeight = (int) (noiseValue / threshold) * heightFactor
                                    + (64 * Math.pow(d, 7) * heightFactor)
                                    + detailsValue * heightFactor * 0.5;
            int raiseHeight = (int) Math.round(platformHeight);
            if (raiseHeight < 1) {
                return GorgeColumnTransform.none();
            }

            Material[] addedTopShellMaterials = new Material[raiseHeight];
            for (int y = 1; y <= raiseHeight; y++) {
                Material material = GenUtils.randChoice(random, GORGE_RAISE_MATERIALS);
                if (slabs
                    && material != Material.GRASS_BLOCK
                    && y == raiseHeight
                    && platformHeight - (int) platformHeight >= 0.5)
                {
                    Material slab = Material.getMaterial(material.name() + "_SLAB");
                    if (slab != null) {
                        material = slab;
                    }
                }
                addedTopShellMaterials[y - 1] = material;
            }
            if (detailsValue < 0.2 && GenUtils.chance(random, 3, 4)) {
                addedTopShellMaterials[raiseHeight - 1] = Material.GRASS_BLOCK;
            }
            return new GorgeColumnTransform(raiseHeight, addedTopShellMaterials, 0, Integer.MIN_VALUE);
        }

        int depth = (int) Math.sqrt(Math.abs(rawCliffNoiseVal * edgeFactor) * 200 * 50);
        if (baseHeight - depth < TerraformGenerator.seaLevel - 20) {
            int depthToPreserve = baseHeight - (TerraformGenerator.seaLevel - 20);
            depth = (int) (depthToPreserve + Math.round(Math.sqrt(depth - depthToPreserve)));
        }
        if (depth > baseHeight - 10) {
            depth = baseHeight - 10;
        }
        if (depth < 1) {
            return GorgeColumnTransform.none();
        }
        return new GorgeColumnTransform(0, new Material[0], depth, TerraformGenerator.seaLevel - 20);
    }

    private static @NotNull FastNoise getCliffNoise(@NotNull TerraformWorld tw) {
        return NoiseCacheHandler.getNoise(tw, NoiseCacheEntry.BIOME_GORGE_CLIFFNOISE, world -> {
            FastNoise n = new FastNoise();
            n.SetNoiseType(FastNoise.NoiseType.CubicFractal);
            n.SetFractalOctaves(3);
            n.SetFrequency(0.04f);
            return n;
        });
    }

    private static @NotNull FastNoise getDetailsNoise(@NotNull TerraformWorld tw) {
        return NoiseCacheHandler.getNoise(tw, NoiseCacheEntry.BIOME_GORGE_DETAILS, world -> {
            FastNoise n = new FastNoise();
            n.SetNoiseType(FastNoise.NoiseType.SimplexFractal);
            n.SetFrequency(0.03f);
            return n;
        });
    }

    public record GorgeColumnTransform(int raiseHeight,
                                       Material[] addedTopShellMaterials,
                                       int trimDepth,
                                       int removedTopWaterlineY) {
        private static final GorgeColumnTransform NONE = new GorgeColumnTransform(
                0,
                new Material[0],
                0,
                Integer.MIN_VALUE
        );

        public static @NotNull GorgeColumnTransform none() {
            return NONE;
        }
    }

    @Override
    public void populateLargeItems(@NotNull TerraformWorld tw,
                                   @NotNull Random random,
                                   @NotNull PopulatorDataAbstract data)
    {
        plainsHandler.populateLargeItems(tw, random, data);

        // Spawn rocks
        SimpleLocation[] rocks = GenUtils.randomObjectPositions(tw, data.getChunkX(), data.getChunkZ(), 17, 0.4f);

        for (SimpleLocation sLoc : rocks) {
            if (data.getBiome(sLoc.getX(), sLoc.getZ()) == getBiome()) {
                int rockY = GenUtils.getHighestGround(data, sLoc.getX(), sLoc.getZ());
                sLoc = sLoc.getAtY(rockY);
                if (rockY > TerraformGenerator.seaLevel - 18) {
                    continue;
                }

                BlockUtils.replaceSphere(random.nextInt(91822),
                        (float) GenUtils.randDouble(random, 3, 6),
                        (float) GenUtils.randDouble(random, 4, 7),
                        (float) GenUtils.randDouble(random, 3, 6),
                        new SimpleBlock(data, sLoc),
                        true,
                        GenUtils.randChoice(Material.GRANITE, Material.ANDESITE, Material.DIORITE)
                );
            }
        }
    }


}
