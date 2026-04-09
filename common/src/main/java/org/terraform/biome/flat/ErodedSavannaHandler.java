package org.terraform.biome.flat;

import org.terraform.coregen.HeightMap;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;
import org.terraform.biome.BiomeHandler;
import org.terraform.biome.custombiomes.CustomBiomeType;
import org.terraform.coregen.populatordata.PopulatorDataAbstract;
import org.terraform.data.SimpleBlock;
import org.terraform.data.SimpleLocation;
import org.terraform.data.TerraformWorld;
import org.terraform.data.Wall;
import org.terraform.small_items.PlantBuilder;
import org.terraform.tree.FractalTreeBuilder;
import org.terraform.tree.FractalTypes;
import org.terraform.utils.BlockUtils;
import org.terraform.utils.GenUtils;
import org.terraform.utils.blockdata.OrientableBuilder;

import java.util.Random;

import org.terraform.biome.BiomeBank;

public class ErodedSavannaHandler extends BiomeHandler {

    @Override
    public boolean isOcean() {
        return false;
    }

    // Prevents suppression of rivers. Yes maybe this was the wrong approach for height variety but in the end it accidentally
    // resulted in beautiful basins and beach shores with shallow waters, so I kept the formula and worked in an alternative method to
    // guarantee proper river depth. Actually ended up making the biome more unique by lowering parts of the biome into rivers and beaches.
    @Override
    public double calculateHeight(TerraformWorld tw, int x, int z) {
        double base = HeightMap.CORE.getHeight(tw, x, z);

        double macro = Math.sin(x * 0.01) * 10;
        double micro = Math.sin(x * 0.03) * 3 + Math.cos(z * 0.03) * 3;

        double seaLevel = 62;

        // Valley detection (how far above sea level we are)
        double valleyFactor = Math.min(1.0, Math.max(0.0, (base - seaLevel) / 12.0));

        // Push low terrain down (creates basins)
        double valleyPush = (1.0 - valleyFactor) * -4;

        double height = base
                        + valleyPush
                        + (macro * valleyFactor)
                        + (micro * (0.4 + 0.6 * valleyFactor));

        // --- SOFT CONSTRAINT FOR RIVERS ---
        double riverFloor = seaLevel - 3;

        if (height > riverFloor) {
            double diff = height - riverFloor;
            height -= diff * 0.5; // soften instead of snapping
        }

        return height;
    }

    @Override
    public @NotNull Biome getBiome() {
        return Biome.SAVANNA;
    }

    @Override
    public @NotNull CustomBiomeType getCustomBiome() {
        return CustomBiomeType.ERODED_SAVANNA;
    }

    @Override
    public Material @NotNull [] getSurfaceCrust(@NotNull Random rand) {
        return new Material[] {
                Material.GRASS_BLOCK,
                Material.DIRT,
                Material.DIRT,
                GenUtils.randChoice(rand, Material.DIRT, Material.STONE),
                GenUtils.randChoice(rand, Material.DIRT, Material.STONE)
        };
    }

    @Override
    public void populateSmallItems(TerraformWorld world,
                                   @NotNull Random random,
                                   int rawX,
                                   int surfaceY,
                                   int rawZ,
                                   @NotNull PopulatorDataAbstract data)
    {
        if (data.getType(rawX, surfaceY, rawZ) == Material.GRASS_BLOCK
            && data.getType(rawX, surfaceY+1, rawZ) == Material.AIR
            && !BlockUtils.isWet(new SimpleBlock(data,
                rawX,
                surfaceY,
                rawZ)))
        {

            if (GenUtils.chance(random, 2, 10)) { // Grass
                if (GenUtils.chance(random, 6, 10)) {
                    PlantBuilder.GRASS.build(data, rawX, surfaceY + 1, rawZ);
                    if (random.nextBoolean()) {
                        PlantBuilder.TALL_GRASS.build(data, rawX, surfaceY + 1, rawZ);
                    }
                }
                else {
                    switch (GenUtils.randInt(random, 1, 10)) {
                        case 0, 1 -> PlantBuilder.BUSH.build(data, rawX, surfaceY + 1, rawZ);
                        default -> PlantBuilder.TALL_GRASS.build(data, rawX, surfaceY + 1, rawZ);
                    }
                }
            }
        }
    }

    @Override
    public void populateLargeItems(@NotNull TerraformWorld tw,
                                   @NotNull Random random,
                                   @NotNull PopulatorDataAbstract data)
    {
        // Large trees
        SimpleLocation[] trees = GenUtils.randomObjectPositions(tw, data.getChunkX(), data.getChunkZ(), 70, 0.15f);

        for (SimpleLocation sLoc : trees) {
            int treeY = GenUtils.getHighestGround(data, sLoc.getX(), sLoc.getZ());
            sLoc = sLoc.getAtY(treeY);
            if (tw.getBiomeBank(sLoc.getX(), sLoc.getZ()) == BiomeBank.ERODED_SAVANNA
                && BlockUtils.isDirtLike(data.getType(sLoc.getX(), sLoc.getY(), sLoc.getZ())))
            {
                new FractalTreeBuilder(FractalTypes.Tree.ANCIENT_BIG).build(
                        tw,
                        data,
                        sLoc.getX(),
                        sLoc.getY(),
                        sLoc.getZ()
                );
            }
        }

        // Small trees
        SimpleLocation[] smalltrees = GenUtils.randomObjectPositions(tw, data.getChunkX(), data.getChunkZ(), 18);

        for (SimpleLocation sLoc : smalltrees) {
            int highestY = GenUtils.getHighestGround(data, sLoc.getX(), sLoc.getZ());
            if (BlockUtils.isWet(new SimpleBlock(data, sLoc.getX(), highestY + 1, sLoc.getZ()))) {
                continue;
            }

            sLoc = sLoc.getAtY(highestY);
            switch (random.nextInt(6)) {
                case 0, 1, 2, 3 -> {
                    if (tw.getBiomeBank(sLoc.getX(), sLoc.getZ()) == BiomeBank.ERODED_SAVANNA
                        && BlockUtils.isDirtLike(data.getType(sLoc.getX(), sLoc.getY(), sLoc.getZ())))
                    {
                        new FractalTreeBuilder(FractalTypes.Tree.ANCIENT_SMALL).build(
                                tw,
                                data,
                                sLoc.getX(),
                                sLoc.getY(),
                                sLoc.getZ()
                        );
                    }
                }
                // Fallen tree
                case 5 -> {
                    Wall w = new Wall(data, sLoc.getUp(), BlockUtils.getDirectBlockFace(random));
                    int length = GenUtils.randInt(1, 3);
                    for (int i = -length; i <= length; i++) {
                        if (!w.getFront(i).isAir()
                            || !w.getFront(i).getDown().isSolid()) break;
                        w.getFront(i).setBlockData(new OrientableBuilder(Material.OAK_LOG)
                                .setAxis(BlockUtils.getAxisFromBlockFace(w.getDirection())).get());
                    }
                }
            }
        }
    }
}
