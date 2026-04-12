package org.terraform.cave.v3.generation;

import org.jetbrains.annotations.NotNull;
import org.terraform.data.TerraformWorld;
import org.terraform.main.config.TConfig;
import org.terraform.utils.noise.FastNoise;
import org.terraform.utils.noise.NoiseCacheHandler;

public final class Phase3ACheeseFieldProvider implements CaveFieldProvider {
    private static final float WARP_SCALE = 0.58f;
    private static final float WARP_VERTICAL_SCALE = 0.30f;
    private static final float WARP_HORIZONTAL_AMPLITUDE = 11.5f;
    private static final float WARP_VERTICAL_AMPLITUDE = 4.25f;
    private static final float CHAMBER_HORIZONTAL_STRETCH = 0.62f;
    private static final float CHAMBER_VERTICAL_STRETCH = 0.36f;
    private static final float MIN_FULL_CARVE_DEPTH = 6f;
    private static final float SURFACE_FADE_DEPTH = 14f;
    private static final float DEEP_BOOST_START = 18f;
    private static final float DEEP_BOOST_RANGE = 28f;
    private static final float BASE_DENSITY_OFFSET = -0.05f;
    private static final float CHAMBER_WEIGHT = 0.56f;

    @Override
    public @NotNull CaveFieldSampler createSampler(@NotNull TerraformWorld tw) {
        float baseFrequency = Math.max(0.0001f, TConfig.c.CAVES_DENSITY_V1_FREQUENCY);
        FastNoise warpNoise = NoiseCacheHandler.getNoise(
                tw,
                NoiseCacheHandler.NoiseCacheEntry.CAVE_V3_CHEESE_WARP_NOISE,
                world -> {
                    FastNoise n = new FastNoise((int) (world.getSeed() * 67L + 0x14D51));
                    n.SetNoiseType(FastNoise.NoiseType.SimplexFractal);
                    n.SetFrequency(baseFrequency * 0.6f);
                    n.SetFractalOctaves(2);
                    return n;
                }
        );
        FastNoise chamberNoise = NoiseCacheHandler.getNoise(
                tw,
                NoiseCacheHandler.NoiseCacheEntry.CAVE_V3_CHEESE_BODY_NOISE,
                world -> {
                    FastNoise n = new FastNoise((int) (world.getSeed() * 79L + 0x2C771));
                    n.SetNoiseType(FastNoise.NoiseType.SimplexFractal);
                    n.SetFrequency(baseFrequency * 0.72f);
                    n.SetFractalOctaves(3);
                    return n;
                }
        );
        return (rawX, y, rawZ, baseSurfaceHeight) -> getCheeseDensity(
                warpNoise,
                chamberNoise,
                rawX,
                y,
                rawZ,
                baseSurfaceHeight
        );
    }

    private static float getCheeseDensity(@NotNull FastNoise warpNoise,
                                          @NotNull FastNoise chamberNoise,
                                          int rawX,
                                          int y,
                                          int rawZ,
                                          double baseSurfaceHeight)
    {
        float warpSampleX = rawX * WARP_SCALE;
        float warpSampleY = y * WARP_VERTICAL_SCALE;
        float warpSampleZ = rawZ * WARP_SCALE;
        float warpedX = rawX + (warpNoise.GetNoise(warpSampleX + 13.2f, warpSampleY - 7.4f, warpSampleZ + 5.1f)
                                * WARP_HORIZONTAL_AMPLITUDE);
        float warpedY = y + (warpNoise.GetNoise(warpSampleX - 11.7f, warpSampleY + 17.6f, warpSampleZ - 9.3f)
                             * WARP_VERTICAL_AMPLITUDE);
        float warpedZ = rawZ + (warpNoise.GetNoise(warpSampleX + 7.8f, warpSampleY + 3.1f, warpSampleZ - 15.4f)
                                * WARP_HORIZONTAL_AMPLITUDE);

        float chamber = 0.5f + (0.5f * chamberNoise.GetNoise(
                warpedX * CHAMBER_HORIZONTAL_STRETCH,
                warpedY * CHAMBER_VERTICAL_STRETCH,
                warpedZ * CHAMBER_HORIZONTAL_STRETCH
        ));
        float depthBelowSurface = (float) (baseSurfaceHeight - y);
        float nearSurfaceFade = DensityCarveRules.clamp01((depthBelowSurface - MIN_FULL_CARVE_DEPTH) / SURFACE_FADE_DEPTH);
        float deepBoost = DensityCarveRules.clamp01((depthBelowSurface - DEEP_BOOST_START) / DEEP_BOOST_RANGE);

        float density = BASE_DENSITY_OFFSET + (chamber * CHAMBER_WEIGHT);
        density *= 0.55f + (nearSurfaceFade * 0.45f);
        density += deepBoost * 0.08f;
        return density;
    }
}
