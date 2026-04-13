package org.terraform.cave.v3.generation;

import org.jetbrains.annotations.NotNull;
import org.terraform.data.TerraformWorld;
import org.terraform.main.config.TConfig;
import org.terraform.utils.noise.FastNoise;
import org.terraform.utils.noise.NoiseCacheHandler;

public final class Phase3BTunnelFieldProvider implements CaveFieldProvider {
    private static final float WARP_SCALE = 0.7f;
    private static final float WARP_VERTICAL_SCALE = 0.7f;
    private static final float WARP_HORIZONTAL_AMPLITUDE = 7f;
    private static final float WARP_VERTICAL_AMPLITUDE = 3.25f;

    private static final float CHAMBER_HORIZONTAL_STRETCH = 0.28f;
    private static final float CHAMBER_VERTICAL_STRETCH = 0.41f;

    private static final float TUNNEL_HORIZONTAL_STRETCH_A = 0.17f;
    private static final float TUNNEL_HORIZONTAL_STRETCH_B = 0.15f;
    private static final float TUNNEL_VERTICAL_STRETCH_A = 0.24f;
    private static final float TUNNEL_VERTICAL_STRETCH_B = 0.22f;
    private static final float TUNNEL_WIDTH_A = 0.23f;
    private static final float TUNNEL_WIDTH_B = 0.2f;
    private static final float TUNNEL_VERTICAL_CLEARANCE_OFFSET = 2.35f;
    private static final float TUNNEL_VERTICAL_CLEARANCE_SECONDARY_OFFSET = 4.45f;
    private static final float TUNNEL_VERTICAL_CLEARANCE_SECONDARY_WEIGHT = 0.82f;

    private static final float CONTINUITY_HORIZONTAL_STRETCH = 0.08f;
    private static final float CONTINUITY_VERTICAL_STRETCH = 0.11f;
    private static final float JUNCTION_HORIZONTAL_STRETCH = 0.1f;
    private static final float JUNCTION_VERTICAL_STRETCH = 0.16f;

    private static final float MIN_FULL_CARVE_DEPTH = 4f;
    private static final float SURFACE_FADE_DEPTH = 10f;
    private static final float DEEP_BOOST_START = 16f;
    private static final float DEEP_BOOST_RANGE = 26f;

    private static final float BASE_DENSITY_OFFSET = -0.11f;
    private static final float TUNNEL_WEIGHT = 0.66f;
    private static final float CHAMBER_LINK_WEIGHT = 0.08f;
    private static final float CONTINUITY_WEIGHT = 0.12f;
    private static final float JUNCTION_WEIGHT = 0.11f;

    @Override
    public @NotNull CaveFieldSampler createSampler(@NotNull TerraformWorld tw) {
        float baseFrequency = Math.max(0.0001f, TConfig.c.CAVES_DENSITY_V1_FREQUENCY);
        FastNoise warpNoise = NoiseCacheHandler.getNoise(
                tw,
                NoiseCacheHandler.NoiseCacheEntry.CAVE_V3_CHEESE_WARP_NOISE,
                world -> {
                    FastNoise n = new FastNoise((int) (world.getSeed() * 67L + 0x14D51));
                    n.SetNoiseType(FastNoise.NoiseType.SimplexFractal);
                    n.SetFrequency(baseFrequency * 0.53f);
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
                    n.SetFrequency(baseFrequency * 0.68f);
                    n.SetFractalOctaves(3);
                    return n;
                }
        );
        FastNoise tunnelNoiseA = NoiseCacheHandler.getNoise(
                tw,
                NoiseCacheHandler.NoiseCacheEntry.CAVE_V3_TUNNEL_AXIS_NOISE_A,
                world -> {
                    FastNoise n = new FastNoise((int) (world.getSeed() * 97L + 0x37A61));
                    n.SetNoiseType(FastNoise.NoiseType.SimplexFractal);
                    n.SetFrequency(baseFrequency * 1.02f);
                    n.SetFractalOctaves(3);
                    return n;
                }
        );
        FastNoise tunnelNoiseB = NoiseCacheHandler.getNoise(
                tw,
                NoiseCacheHandler.NoiseCacheEntry.CAVE_V3_TUNNEL_AXIS_NOISE_B,
                world -> {
                    FastNoise n = new FastNoise((int) (world.getSeed() * 101L + 0x4C8F3));
                    n.SetNoiseType(FastNoise.NoiseType.SimplexFractal);
                    n.SetFrequency(baseFrequency * 0.94f);
                    n.SetFractalOctaves(3);
                    return n;
                }
        );
        FastNoise continuityNoise = NoiseCacheHandler.getNoise(
                tw,
                NoiseCacheHandler.NoiseCacheEntry.CAVE_V3_TUNNEL_CONTINUITY_NOISE,
                world -> {
                    FastNoise n = new FastNoise((int) (world.getSeed() * 103L + 0x52D9B));
                    n.SetNoiseType(FastNoise.NoiseType.SimplexFractal);
                    n.SetFrequency(baseFrequency * 0.42f);
                    n.SetFractalOctaves(2);
                    return n;
                }
        );
        FastNoise junctionNoise = NoiseCacheHandler.getNoise(
                tw,
                NoiseCacheHandler.NoiseCacheEntry.CAVE_V3_TUNNEL_JUNCTION_NOISE,
                world -> {
                    FastNoise n = new FastNoise((int) (world.getSeed() * 109L + 0x6B42D));
                    n.SetNoiseType(FastNoise.NoiseType.SimplexFractal);
                    n.SetFrequency(baseFrequency * 0.56f);
                    n.SetFractalOctaves(2);
                    return n;
                }
        );
        return (rawX, y, rawZ, baseSurfaceHeight) -> getTunnelDensity(
                warpNoise,
                chamberNoise,
                tunnelNoiseA,
                tunnelNoiseB,
                continuityNoise,
                junctionNoise,
                rawX,
                y,
                rawZ,
                baseSurfaceHeight
        );
    }

    private static float getTunnelDensity(@NotNull FastNoise warpNoise,
                                          @NotNull FastNoise chamberNoise,
                                          @NotNull FastNoise tunnelNoiseA,
                                          @NotNull FastNoise tunnelNoiseB,
                                          @NotNull FastNoise continuityNoise,
                                          @NotNull FastNoise junctionNoise,
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
        float chamberLink = DensityCarveRules.clamp01((chamber - 0.38f) / 0.36f);

        float continuity = 0.5f + (0.5f * continuityNoise.GetNoise(
                warpedX * CONTINUITY_HORIZONTAL_STRETCH,
                warpedY * CONTINUITY_VERTICAL_STRETCH,
                warpedZ * CONTINUITY_HORIZONTAL_STRETCH
        ));
        continuity = DensityCarveRules.clamp01((continuity - 0.22f) / 0.56f);

        float junction = 0.5f + (0.5f * junctionNoise.GetNoise(
                warpedX * JUNCTION_HORIZONTAL_STRETCH,
                warpedY * JUNCTION_VERTICAL_STRETCH,
                warpedZ * JUNCTION_HORIZONTAL_STRETCH
        ));
        junction = DensityCarveRules.clamp01((junction - 0.62f) / 0.24f) * (0.45f + (0.55f * chamberLink));

        float tunnelWidthBoost = 0.03f + (continuity * 0.09f) + (chamberLink * 0.07f) + (junction * 0.06f);
        float tunnelCore = sampleTunnelCore(tunnelNoiseA, tunnelNoiseB, warpedX, warpedY, warpedZ, tunnelWidthBoost);
        tunnelCore = Math.max(
                tunnelCore,
                sampleTunnelCore(
                        tunnelNoiseA,
                        tunnelNoiseB,
                        warpedX,
                        warpedY + TUNNEL_VERTICAL_CLEARANCE_OFFSET,
                        warpedZ,
                        tunnelWidthBoost
                )
        );
        tunnelCore = Math.max(
                tunnelCore,
                sampleTunnelCore(
                        tunnelNoiseA,
                        tunnelNoiseB,
                        warpedX,
                        warpedY - TUNNEL_VERTICAL_CLEARANCE_OFFSET,
                        warpedZ,
                        tunnelWidthBoost
                )
        );
        tunnelCore = Math.max(
                tunnelCore,
                sampleTunnelCore(
                        tunnelNoiseA,
                        tunnelNoiseB,
                        warpedX,
                        warpedY + TUNNEL_VERTICAL_CLEARANCE_SECONDARY_OFFSET,
                        warpedZ,
                        tunnelWidthBoost * 0.92f
                ) * TUNNEL_VERTICAL_CLEARANCE_SECONDARY_WEIGHT
        );
        tunnelCore = Math.max(
                tunnelCore,
                sampleTunnelCore(
                        tunnelNoiseA,
                        tunnelNoiseB,
                        warpedX,
                        warpedY - TUNNEL_VERTICAL_CLEARANCE_SECONDARY_OFFSET,
                        warpedZ,
                        tunnelWidthBoost * 0.92f
                ) * TUNNEL_VERTICAL_CLEARANCE_SECONDARY_WEIGHT
        );
        tunnelCore = tunnelCore * tunnelCore * (3f - (2f * tunnelCore));

        float depthBelowSurface = (float) (baseSurfaceHeight - y);
        float nearSurfaceFade = DensityCarveRules.clamp01((depthBelowSurface - MIN_FULL_CARVE_DEPTH) / SURFACE_FADE_DEPTH);
        float deepBoost = DensityCarveRules.clamp01((depthBelowSurface - DEEP_BOOST_START) / DEEP_BOOST_RANGE);

        float density = BASE_DENSITY_OFFSET + (tunnelCore * (TUNNEL_WEIGHT + (continuity * CONTINUITY_WEIGHT)));
        density += chamberLink * CHAMBER_LINK_WEIGHT * (0.35f + (0.65f * tunnelCore));
        density += junction * JUNCTION_WEIGHT * Math.max(tunnelCore, chamberLink * 0.75f);
        density *= 0.7f + (nearSurfaceFade * 0.3f);
        density += deepBoost * 0.05f;
        return density;
    }

    private static float sampleTunnelCore(@NotNull FastNoise tunnelNoiseA,
                                          @NotNull FastNoise tunnelNoiseB,
                                          float warpedX,
                                          float warpedY,
                                          float warpedZ,
                                          float widthBoost)
    {
        float primaryBranch = sampleTunnelBranch(
                tunnelNoiseA,
                tunnelNoiseB,
                warpedX,
                warpedY,
                warpedZ,
                TUNNEL_HORIZONTAL_STRETCH_A,
                TUNNEL_VERTICAL_STRETCH_A,
                TUNNEL_HORIZONTAL_STRETCH_B,
                TUNNEL_VERTICAL_STRETCH_B,
                TUNNEL_WIDTH_A + widthBoost,
                TUNNEL_WIDTH_B + (widthBoost * 0.9f),
                31.7f,
                -18.4f,
                -26.1f
        );
        float secondaryBranch = sampleTunnelBranch(
                tunnelNoiseA,
                tunnelNoiseB,
                warpedX + 41.3f,
                warpedY - 12.7f,
                warpedZ - 37.9f,
                TUNNEL_HORIZONTAL_STRETCH_B,
                TUNNEL_VERTICAL_STRETCH_B,
                TUNNEL_HORIZONTAL_STRETCH_A,
                TUNNEL_VERTICAL_STRETCH_A,
                (TUNNEL_WIDTH_A * 0.92f) + (widthBoost * 0.95f),
                (TUNNEL_WIDTH_B * 0.92f) + (widthBoost * 0.85f),
                -22.6f,
                14.1f,
                33.4f
        );

        return Math.max(primaryBranch, secondaryBranch * 0.94f);
    }

    private static float sampleTunnelBranch(@NotNull FastNoise tunnelNoiseA,
                                            @NotNull FastNoise tunnelNoiseB,
                                            float warpedX,
                                            float warpedY,
                                            float warpedZ,
                                            float stretchAX,
                                            float stretchAY,
                                            float stretchBX,
                                            float stretchBY,
                                            float widthA,
                                            float widthB,
                                            float offsetX,
                                            float offsetY,
                                            float offsetZ)
    {
        float axisA = tunnelNoiseA.GetNoise(
                warpedX * stretchAX,
                warpedY * stretchAY,
                warpedZ * stretchAX
        );
        float axisB = tunnelNoiseB.GetNoise(
                (warpedX * stretchBX) + offsetX,
                (warpedY * stretchBY) + offsetY,
                (warpedZ * stretchBX) + offsetZ
        );
        return getIntersectionMask(axisA, axisB, widthA, widthB);
    }

    private static float getIntersectionMask(float axisA, float axisB, float widthA, float widthB) {
        float safeWidthA = Math.max(0.0001f, widthA);
        float safeWidthB = Math.max(0.0001f, widthB);
        float normalizedA = axisA / safeWidthA;
        float normalizedB = axisB / safeWidthB;
        float radialDistance = (float) Math.sqrt((normalizedA * normalizedA) + (normalizedB * normalizedB));
        float mask = 1f - DensityCarveRules.clamp01(radialDistance);
        return mask * mask * (3f - (2f * mask));
    }
}
