package org.terraform.biome.cavepopulators;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.terraform.data.SimpleBlock;
import org.terraform.data.TerraformWorld;
import org.terraform.main.TerraformGeneratorPlugin;
import org.terraform.main.config.TConfig;
import org.terraform.utils.BlockUtils;
import org.terraform.utils.GenUtils;

import java.util.Random;

public class CaveFluidClusterPopulator extends AbstractCaveClusterPopulator {
    Random rand;
    @Nullable
    Material fluid;
    int rY;

    public CaveFluidClusterPopulator(float radius) {
        super(radius);
    }

    @Override
    public void oneUnit(@NotNull TerraformWorld tw,
                        Random doNotUse,
                        @Nullable SimpleBlock ceil,
                        @Nullable SimpleBlock floor,
                        boolean boundary)
    {
        if (ceil == null || floor == null) {
            return;
        }
        if (rand == null) {
            rand = tw.getHashedRand(center.getX(), center.getY(), center.getZ());
            if (center.getY() < TerraformGeneratorPlugin.injector.getMinY() + TConfig.c.BIOME_CAVE_FLUIDCLUSTER_DEEP_LAVA_DEPTH) {
                fluid = Material.LAVA;
            }
            else if (GenUtils.chance(rand, TConfig.c.BIOME_CAVE_FLUIDCLUSTER_LAVA_CHANCE, 100)) {
                fluid = Material.LAVA;
            }
            else {
                fluid = Material.WATER;
            }
            rY = 3 + rand.nextInt(3);
        }
        Material original = floor.getType();
        for (int i = 0; i < rY; i++) {
            if (boundary) {
                floor.setType(original);
            }
            else if (floor.getY() <= lowestYCenter.getY()) {
                floor.setType(fluid);
            }
            else if (!BlockUtils.isExposedToMaterial(floor, BlockUtils.fluids)
                     && !BlockUtils.fluids.contains(floor.getUp().getType())) // isExposed only checks NSEW
            {
                floor.setType(Material.CAVE_AIR);
            }

            floor = floor.getDown();

            // Fix floating fluids
            if (!floor.isSolid()) {
                floor.setType(original);
                break;
            }
        }

    }


}
