package org.terraform.biome.flat;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.terraform.biome.BiomeBank;
import org.terraform.biome.BiomeHandler;
import org.terraform.biome.custombiomes.CustomBiomeType;
import org.terraform.coregen.ChunkCache;
import org.terraform.coregen.HeightMap;
import org.terraform.coregen.bukkit.TerraformGenerator;
import org.terraform.coregen.populatordata.PopulatorDataAbstract;
import org.terraform.data.SimpleBlock;
import org.terraform.data.SimpleLocation;
import org.terraform.data.TerraformWorld;
import org.terraform.main.config.TConfig;
import org.terraform.small_items.PlantBuilder;
import org.terraform.tree.FractalTreeBuilder;
import org.terraform.tree.FractalTypes;
import org.terraform.tree.TreeDB;
import org.terraform.utils.BlockUtils;
import org.terraform.utils.CoralGenerator;
import org.terraform.utils.GenUtils;
import org.terraform.utils.blockdata.BisectedBuilder;
import org.terraform.utils.blockdata.DirectionalBuilder;
import org.terraform.utils.noise.FastNoise;
import org.terraform.utils.noise.FastNoise.NoiseType;
import org.terraform.utils.noise.NoiseCacheHandler;
import org.terraform.utils.noise.NoiseCacheHandler.NoiseCacheEntry;
import org.terraform.utils.version.V_1_19;

import java.util.Random;

public class TropicSwampHandler extends BiomeHandler {

    @Override
    public @NotNull BiomeBank getRiverType() {
        return BiomeBank.TROPIC_SWAMP;
    }

    @Override
    public @NotNull BiomeBank getBeachType() {
        return BiomeBank.TROPIC_SWAMP;
    }

    @Override
    public boolean isOcean() {
        return true;
    }

    @Override
    public @NotNull Biome getBiome() {
        return V_1_19.MANGROVE_SWAMP;
    }

    @Override
    public @NotNull CustomBiomeType getCustomBiome() {
        return CustomBiomeType.TROPIC_SWAMP;
    }

    @Override
    public Material @NotNull [] getSurfaceCrust(@NotNull Random rand) {
        return new Material[] {
                GenUtils.weightedRandomMaterial(rand, Material.GRASS_BLOCK, 144, V_1_19.MUD, 48, Material.MUDDY_MANGROVE_ROOTS, 36, Material.MOSS_BLOCK, 48, Material.PODZOL, 48, Material.VERDANT_FROGLIGHT, 1),
                GenUtils.randChoice(rand, Material.DIRT, Material.DIRT, V_1_19.MUD),
                GenUtils.randChoice(rand, Material.DIRT, Material.DIRT, Material.STONE),
                GenUtils.randChoice(rand, Material.DIRT, Material.STONE),
                GenUtils.randChoice(rand, Material.DIRT, Material.STONE)
        };
    }

    @Override
    public @NotNull BiomeHandler getTransformHandler() {
        return this;
    }

    @Override
    public void transformTerrain(@NotNull ChunkCache cache,
                                 TerraformWorld tw,
                                 @NotNull Random random,
                                 ChunkGenerator.@NotNull ChunkData chunk,
                                 int x,
                                 int z,
                                 int chunkX,
                                 int chunkZ)
    {
        int surfaceY = cache.getTransformedHeight(x, z);
        if (surfaceY < TerraformGenerator.seaLevel) {
            int rawX = chunkX * 16 + x;
            int rawZ = chunkZ * 16 + z;
            FastNoise mudNoise = NoiseCacheHandler.getNoise(tw, NoiseCacheEntry.BIOME_SWAMP_MUDNOISE, world -> {
                FastNoise n = new FastNoise((int) (world.getSeed() * 4));
                n.SetNoiseType(NoiseType.SimplexFractal);
                n.SetFrequency(0.05f);
                n.SetFractalOctaves(4);

                return n;
            });

            double noise = mudNoise.GetNoise(rawX, rawZ);
            if (noise < 0) {
                noise = 0;
            }

            int att = (int) Math.round(noise * 10);
            if (att + surfaceY > TerraformGenerator.seaLevel) {
                att = TerraformGenerator.seaLevel - surfaceY;
            }

            if (att > 0) {
                Material[] crust = getSurfaceCrust(random);
                Material surface = crust[0];
                Material subsurface = crust[1];
                for (int i = 1; i <= att; i++) {
                    if (i < att) {
                        chunk.setBlock(x, surfaceY + i, z, subsurface);
                    }
                    else {
                        chunk.setBlock(x, surfaceY + i, z, surface);
                    }
                }
            }

            cache.writeTransformedHeight(x, z, (short) (surfaceY + att));
        }
    }

    @Override
    public void populateSmallItems(TerraformWorld world,
                                   @NotNull Random random,
                                   int rawX,
                                   int surfaceY,
                                   int rawZ,
                                   @NotNull PopulatorDataAbstract data)
    {
        SimpleBlock ground = new SimpleBlock(data, rawX, surfaceY, rawZ);
        Material groundType = ground.getType();
        if (!BlockUtils.isStoneLike(groundType) && groundType != V_1_19.MUD && groundType != Material.MOSS_BLOCK) {
            return;
        }

        int seaLevel = TerraformGenerator.seaLevel;
        if (surfaceY < seaLevel) {
            if (data.getType(rawX, seaLevel, rawZ) == Material.WATER && GenUtils.chance(random, 1, 24)) {
                PlantBuilder.LILY_PAD.build(data, rawX, seaLevel + 1, rawZ);
            }

            if (BlockUtils.isWet(ground.getUp())
                && surfaceY < seaLevel - 2
                && GenUtils.chance(random, 25, 100))
            {
                CoralGenerator.generateKelpGrowth(data, rawX, surfaceY + 1, rawZ);
            }

            if (GenUtils.chance(random, TConfig.c.BIOME_CLAY_DEPOSIT_CHANCE_OUT_OF_THOUSAND, 1000)) {
                BlockUtils.generateClayDeposit(rawX, surfaceY, rawZ, data, random);
            }
            if (GenUtils.chance(random, 6, 1000)) {
                BlockUtils.replaceCircularPatch(
                        random.nextInt(9999),
                        3.5f,
                        new SimpleBlock(data, rawX, surfaceY, rawZ),
                        V_1_19.MUD
                );
            }
            return;
        }

        SimpleBlock aboveGround = ground.getUp();
        if (aboveGround.getType() != Material.AIR || BlockUtils.isWet(aboveGround)) {
            return;
        }

        if (groundType == Material.MOSS_BLOCK) {
            if (TConfig.arePlantsEnabled() && GenUtils.chance(random, 2, 7)) {
                if (random.nextBoolean()) {
                    new DirectionalBuilder(Material.BIG_DRIPLEAF).setFacing(BlockUtils.getDirectBlockFace(random))
                                                                 .apply(aboveGround);
                }
                else if (BlockUtils.isAir(ground.getUp(2).getType())) {
                    new BisectedBuilder(Material.SMALL_DRIPLEAF).placeBoth(aboveGround);
                }
            }
            return;
        }

        if (GenUtils.chance(random, 2, 9)) {
            switch (GenUtils.randInt(random, 0, 26)) {
                case 0, 1, 2, 3, 4, 5, 6 -> PlantBuilder.GRASS.build(data, rawX, surfaceY + 1, rawZ);
                case 9 ,10, 11, 12, 13, 14 -> PlantBuilder.TALL_GRASS.build(data, rawX, surfaceY + 1, rawZ);
                case 16, 17, 18, 19 -> PlantBuilder.FERN.build(data, rawX, surfaceY + 1, rawZ);
                case 22, 23 -> PlantBuilder.AZALEA.build(data, rawX, surfaceY + 1, rawZ);
                case 24 -> PlantBuilder.ORANGE_TULIP.build(data, rawX, surfaceY + 1, rawZ);

                default -> {
                    if (GenUtils.chance(random, 1, 6)) {
                        PlantBuilder.FIREFLY_BUSH.build(data, rawX, surfaceY + 1, rawZ);
                    }
                    else {
                        PlantBuilder.TALL_GRASS.build(data, rawX, surfaceY + 1, rawZ);
                    }
                }
            }
        }

        if (GenUtils.chance(random, 8, 70) && hasAdjacentWater(data, rawX, surfaceY, rawZ)) {
            PlantBuilder.SUGAR_CANE.build(random, data, rawX, surfaceY + 1, rawZ, 2, 4);
        }
    }

    @Override
    public void populateLargeItems(@NotNull TerraformWorld tw,
                                   @NotNull Random random,
                                   @NotNull PopulatorDataAbstract data)
    {
        SimpleLocation[] swampTrees = GenUtils.randomObjectPositions(tw, data.getChunkX(), data.getChunkZ(), 20);
        for (SimpleLocation sLoc : swampTrees) {
            int treeY = GenUtils.getHighestGround(data, sLoc.getX(), sLoc.getZ());
            sLoc = sLoc.getAtY(treeY);
            if (!isTropicSwampZone(tw.getBiomeBank(sLoc.getX(), sLoc.getZ()))) {
                continue;
            }

            Material groundType = data.getType(sLoc.getX(), sLoc.getY(), sLoc.getZ());
            if (!BlockUtils.isDirtLike(groundType) && groundType != V_1_19.MUD) {
                continue;
            }
            if (treeY <= TerraformGenerator.seaLevel - 5 || treeY > TerraformGenerator.seaLevel + 1) {
                continue;
            }

            TreeDB.spawnBreathingRoots(tw, new SimpleBlock(data, sLoc), V_1_19.MANGROVE_ROOTS);
            FractalTypes.Tree.TROPIC_TOP.build(tw, new SimpleBlock(data, sLoc));
        }

        SimpleLocation[] tropicalTrees = GenUtils.randomObjectPositions(tw, data.getChunkX(), data.getChunkZ(), 12);
        for (SimpleLocation sLoc : tropicalTrees) {
            int treeY = GenUtils.getHighestGround(data, sLoc.getX(), sLoc.getZ());
            sLoc = sLoc.getAtY(treeY);
            if (!isTropicSwampZone(tw.getBiomeBank(sLoc.getX(), sLoc.getZ()))) {
                continue;
            }
            if (treeY < TerraformGenerator.seaLevel + 2) {
                continue;
            }

            Material groundType = data.getType(sLoc.getX(), sLoc.getY(), sLoc.getZ());
            if ((!BlockUtils.isDirtLike(groundType) && groundType != V_1_19.MUD)
                || BlockUtils.isWet(new SimpleBlock(data, sLoc.getX(), sLoc.getY() + 1, sLoc.getZ())))
            {
                continue;
            }

            if (TConfig.c.TREES_JUNGLE_BIG_ENABLED && GenUtils.chance(random, 3, 8)) {
                new FractalTreeBuilder(FractalTypes.Tree.TROPIC_BIG).skipGradientCheck().build(
                        tw,
                        data,
                        sLoc.getX(),
                        sLoc.getY(),
                        sLoc.getZ()
                );
            }
            else {
                TreeDB.spawnSmallTropicTree(true, tw, data, sLoc.getX(), sLoc.getY(), sLoc.getZ());
            }
        }
    }

    @Override
    public double calculateHeight(TerraformWorld tw, int x, int z) {
        double height = HeightMap.CORE.getHeight(tw, x, z) - 8;
        if (height <= 0) {
            height = 3;
        }

        return height;
    }

    private boolean hasAdjacentWater(@NotNull PopulatorDataAbstract data, int rawX, int surfaceY, int rawZ) {
        return data.getType(rawX + 1, surfaceY, rawZ) == Material.WATER
               || data.getType(rawX - 1, surfaceY, rawZ) == Material.WATER
               || data.getType(rawX, surfaceY, rawZ + 1) == Material.WATER
               || data.getType(rawX, surfaceY, rawZ - 1) == Material.WATER;
    }

    private boolean isTropicSwampZone(@NotNull BiomeBank bank) {
        return bank == BiomeBank.TROPIC_SWAMP || bank == BiomeBank.MUDFLATS;
    }
}
