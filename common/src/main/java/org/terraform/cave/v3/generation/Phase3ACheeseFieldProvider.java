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
    private static final float RIBBON_HORIZONTAL_STRETCH = 1.45f;
    private static final float RIBBON_VERTICAL_STRETCH = 0.24f;
    private static final float DETAIL_HORIZONTAL_STRETCH = 1.95f;
    private static final float DETAIL_VERTICAL_STRETCH = 0.72f;
    private static final float RIBBON_SHARPNESS_A = 2.35f;
    private static final float RIBBON_SHARPNESS_B = 2.1f;
    private static final float MIN_FULL_CARVE_DEPTH = 6f;
    private static final float SURFACE_FADE_DEPTH = 14f;
    private static final float DEEP_BOOST_START = 18f;
    private static final float DEEP_BOOST_RANGE = 28f;
    private static final float BASE_DENSITY_OFFSET = -0.05f;
    private static final float CHAMBER_WEIGHT = 0.56f;
    private static final float CORRIDOR_WEIGHT = 0.44f;
    private static final float DETAIL_PENALTY = 0.12f;

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
        FastNoise ribbonNoiseA = NoiseCacheHandler.getNoise(
                tw,
                NoiseCacheHandler.NoiseCacheEntry.CAVE_V3_CHEESE_RIBBON_NOISE_A,
                world -> {
                    FastNoise n = new FastNoise((int) (world.getSeed() * 83L + 0x39AF1));
                    n.SetNoiseType(FastNoise.NoiseType.SimplexFractal);
                    n.SetFrequency(baseFrequency * 1.28f);
                    n.SetFractalOctaves(2);
                    return n;
                }
        );
        FastNoise ribbonNoiseB = NoiseCacheHandler.getNoise(
                tw,
                NoiseCacheHandler.NoiseCacheEntry.CAVE_V3_CHEESE_RIBBON_NOISE_B,
                world -> {
                    FastNoise n = new FastNoise((int) (world.getSeed() * 89L + 0x4BE61));
                    n.SetNoiseType(FastNoise.NoiseType.SimplexFractal);
                    n.SetFrequency(baseFrequency * 1.17f);
                    n.SetFractalOctaves(2);
                    return n;
                }
        );
        FastNoise detailNoise = NoiseCacheHandler.getNoise(
                tw,
                NoiseCacheHandler.NoiseCacheEntry.CAVE_V3_CHEESE_DETAIL_NOISE,
                world -> {
                    FastNoise n = new FastNoise((int) (world.getSeed() * 97L + 0x5D8F3));
                    n.SetNoiseType(FastNoise.NoiseType.PerlinFractal);
                    n.SetFrequency(baseFrequency * 2.15f);
                    n.SetFractalOctaves(2);
                    return n;
                }
        );
        return (rawX, y, rawZ, baseSurfaceHeight) -> getCheeseDensity(
                warpNoise,
                chamberNoise,
                ribbonNoiseA,
                ribbonNoiseB,
                detailNoise,
                rawX,
                y,
                rawZ,
                baseSurfaceHeight
        );
    }

    private static float getCheeseDensity(@NotNull FastNoise warpNoise,
                                          @NotNull FastNoise chamberNoise,
                                          @NotNull FastNoise ribbonNoiseA,
                                          @NotNull FastNoise ribbonNoiseB,
                                          @NotNull FastNoise detailNoise,
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
        float ribbonA = 1f - DensityCarveRules.clamp01(Math.abs(ribbonNoiseA.GetNoise(
                warpedX * RIBBON_HORIZONTAL_STRETCH,
                warpedY * RIBBON_VERTICAL_STRETCH,
                warpedZ * RIBBON_HORIZONTAL_STRETCH
        )) * RIBBON_SHARPNESS_A);
        float rotatedX = (warpedX + warpedZ) * 0.70710677f;
        float rotatedZ = (warpedZ - warpedX) * 0.70710677f;
        float ribbonB = 1f - DensityCarveRules.clamp01(Math.abs(ribbonNoiseB.GetNoise(
                rotatedX * RIBBON_HORIZONTAL_STRETCH,
                warpedY * (RIBBON_VERTICAL_STRETCH * 1.1f),
                rotatedZ * RIBBON_HORIZONTAL_STRETCH
        )) * RIBBON_SHARPNESS_B);
        float corridor = Math.max(ribbonA, ribbonB);
        float detail = 0.5f + (0.5f * detailNoise.GetNoise(
                warpedX * DETAIL_HORIZONTAL_STRETCH,
                warpedY * DETAIL_VERTICAL_STRETCH,
                warpedZ * DETAIL_HORIZONTAL_STRETCH
        ));
        float depthBelowSurface = (float) (baseSurfaceHeight - y);
        float nearSurfaceFade = DensityCarveRules.clamp01((depthBelowSurface - MIN_FULL_CARVE_DEPTH) / SURFACE_FADE_DEPTH);
        float deepBoost = DensityCarveRules.clamp01((depthBelowSurface - DEEP_BOOST_START) / DEEP_BOOST_RANGE);

        float density = BASE_DENSITY_OFFSET
                        + (chamber * CHAMBER_WEIGHT)
                        + (corridor * CORRIDOR_WEIGHT)
                        + (corridor * chamber * 0.12f)
                        - (detail * DETAIL_PENALTY);
        density *= 0.55f + (nearSurfaceFade * 0.45f);
        density += deepBoost * 0.08f;
        return density;
    }
}
