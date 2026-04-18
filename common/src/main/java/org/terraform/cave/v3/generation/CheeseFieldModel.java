package org.terraform.cave.v3.generation;

import org.jetbrains.annotations.NotNull;
import org.terraform.cave.v3.CaveV3Profiler;
import org.terraform.data.TerraformWorld;
import org.terraform.utils.noise.FastNoise;
import org.terraform.utils.noise.NoiseCacheHandler;

final class CheeseFieldModel {
    private final @NotNull DensityParams densityParams;
    private final @NotNull DomainWarp domainWarp;
    private final @NotNull NoiseParams noiseParams;

    CheeseFieldModel(@NotNull DensityParams densityParams,
                     @NotNull DomainWarp domainWarp,
                     @NotNull NoiseParams noiseParams)
    {
        this.densityParams = densityParams;
        this.domainWarp = domainWarp;
        this.noiseParams = noiseParams;
    }

    static @NotNull CheeseFieldModel createDefault() {
        return new CheeseFieldModel(
                new DensityParams(
                2f,
                8f,
                18f,
                28f,
                -0.05f,
                0.63f,
                0.75f,
                0.25f,
                0.08f
                ),
                new DomainWarp(
                        0.7f,
                        0.3f,
                        5f,
                        4f,
                        0.28f,
                        0.41f
                ),
                new NoiseParams(
                        0.53f,
                        2,
                        0.68f,
                        3
                )
        );
    }

    @NotNull CaveFieldSampler createSampler() {
        return new CaveFieldSampler() {
            @Override
            public float sampleLocalScore(@NotNull DensitySampleContext context) {
                try (CaveV3Profiler.Scope ignored = CaveV3Profiler.start("cave-v3.sample.field.cheese")) {
                    return getLocalScore(context);
                }
            }

            @Override
            public @NotNull String getProfilerKey() {
                return "cheese";
            }
        };
    }

    @NotNull FastNoise createWarpNoise(@NotNull TerraformWorld tw, float baseFrequency) {
        return NoiseCacheHandler.getNoise(
                tw,
                NoiseCacheHandler.NoiseCacheEntry.CAVE_V3_CHEESE_WARP_NOISE,
                world -> {
                    FastNoise n = new FastNoise((int) (world.getSeed() * 67L + 0x14D51));
                    n.SetNoiseType(FastNoise.NoiseType.SimplexFractal);
                    n.SetFrequency(baseFrequency * noiseParams.warpFrequencyMultiplier());
                    n.SetFractalOctaves(noiseParams.warpFractalOctaves());
                    return n;
                }
        );
    }

    @NotNull FastNoise createChamberNoise(@NotNull TerraformWorld tw, float baseFrequency) {
        return NoiseCacheHandler.getNoise(
                tw,
                NoiseCacheHandler.NoiseCacheEntry.CAVE_V3_CHEESE_BODY_NOISE,
                world -> {
                    FastNoise n = new FastNoise((int) (world.getSeed() * 79L + 0x2C771));
                    n.SetNoiseType(FastNoise.NoiseType.SimplexFractal);
                    n.SetFrequency(baseFrequency * noiseParams.chamberFrequencyMultiplier());
                    n.SetFractalOctaves(noiseParams.chamberFractalOctaves());
                    return n;
                }
        );
    }

    @NotNull DomainWarp getDomainWarp() {
        return domainWarp;
    }

    float getLocalScore(@NotNull DensitySampleContext context) {
        // Keep cheese density shaping behind one parameterized model so future tuning
        // stays aligned with the chamber-domain shaping that feeds it.
        float nearSurfaceFade = DensityCarveRules.clamp01(
                (context.depthBelowSurface - densityParams.minFullCarveDepth()) / densityParams.surfaceFadeDepth()
        );
        float deepBoost = DensityCarveRules.clamp01(
                (context.depthBelowSurface - densityParams.deepBoostStart()) / densityParams.deepBoostRange()
        );

        float density = densityParams.baseDensityOffset() + (context.chamber * densityParams.chamberWeight());
        density *= densityParams.surfaceDensityScaleBase()
                   + (nearSurfaceFade * densityParams.surfaceDensityScaleNearSurfaceWeight());
        density += deepBoost * densityParams.deepBoostDensityWeight();
        return DensityFieldScore.toLocalScore(density);
    }

    record DensityParams(float minFullCarveDepth,
                         float surfaceFadeDepth,
                         float deepBoostStart,
                         float deepBoostRange,
                         float baseDensityOffset,
                         float chamberWeight,
                         float surfaceDensityScaleBase,
                         float surfaceDensityScaleNearSurfaceWeight,
                         float deepBoostDensityWeight) {
    }

    record DomainWarp(float horizontalWarpScale,
                      float verticalWarpScale,
                      float horizontalWarpAmplitude,
                      float verticalWarpAmplitude,
                      float chamberHorizontalStretch,
                      float chamberVerticalStretch) {
    }

    record NoiseParams(float warpFrequencyMultiplier,
                       int warpFractalOctaves,
                       float chamberFrequencyMultiplier,
                       int chamberFractalOctaves) {
    }
}
