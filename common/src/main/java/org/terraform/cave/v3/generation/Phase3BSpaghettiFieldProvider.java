package org.terraform.cave.v3.generation;

import org.jetbrains.annotations.NotNull;
import org.terraform.coregen.bukkit.TerraformGenerator;
import org.terraform.data.TerraformWorld;
import org.terraform.main.config.TConfig;
import org.terraform.utils.noise.FastNoise;
import org.terraform.utils.noise.NoiseCacheHandler;

public final class Phase3BSpaghettiFieldProvider implements CaveFieldProvider {
    private static final float DEPTH_ENVELOPE_SCALE = 0.5885f;
    private static final float HORIZONTAL_SCALE = 3f;
    private static final float VERTICAL_SCALE = 0.4f;

    @Override
    public @NotNull CaveFieldSampler createSampler(@NotNull TerraformWorld tw) {
        FastNoise noise = NoiseCacheHandler.getNoise(
                tw,
                NoiseCacheHandler.NoiseCacheEntry.CAVE_V3_SPAGHETTI_NOISE,
                world -> {
                    FastNoise n = new FastNoise((int) (world.getSeed() * 47L + 101L));
                    n.SetNoiseType(FastNoise.NoiseType.SimplexFractal);
                    n.SetFrequency(TConfig.c.CAVES_DENSITY_V1_SPAGHETTI_FREQUENCY);
                    n.SetFractalOctaves(3);
                    return n;
                }
        );
        return (rawX, y, rawZ, baseSurfaceHeight) -> getSpaghettiScore(noise, rawX, y, rawZ, baseSurfaceHeight);
    }

    private static float getSpaghettiScore(@NotNull FastNoise noise,
                                           int rawX,
                                           int y,
                                           int rawZ,
                                           double baseSurfaceHeight)
    {
        if (baseSurfaceHeight < TerraformGenerator.seaLevel) {
            return CompositeCaveSampler.NEGATIVE_INFINITY_SCORE;
        }

        int maxDepth = Math.max(1, TConfig.c.CAVES_DENSITY_V1_SPAGHETTI_MAX_DEPTH);
        double depthBelowSurface = baseSurfaceHeight - y;
        if (depthBelowSurface < 0d || depthBelowSurface > maxDepth) {
            return CompositeCaveSampler.NEGATIVE_INFINITY_SCORE;
        }

        float rawNoise = noise.GetNoise(HORIZONTAL_SCALE * rawX, y * VERTICAL_SCALE, HORIZONTAL_SCALE * rawZ);
        float depthEnvelope = (float) (DEPTH_ENVELOPE_SCALE * Math.log(maxDepth + 1d - depthBelowSurface));
        return TConfig.c.CAVES_DENSITY_V1_SPAGHETTI_THRESHOLD - (rawNoise * depthEnvelope);
    }
}
