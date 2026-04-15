package org.terraform.cave.v3.generation;

import org.jetbrains.annotations.NotNull;
import org.terraform.cave.v3.CaveV3Profiler;
import org.terraform.coregen.ChunkCache;
import org.terraform.data.TerraformWorld;
import org.terraform.main.TerraformGeneratorPlugin;
import org.terraform.main.config.TConfig;
import org.terraform.utils.noise.FastNoise;
import org.terraform.utils.noise.NoiseCacheHandler;

public final class DensityCompositeCaveSampler implements CompositeCaveSampler {
    private final @NotNull TerraformWorld tw;
    private final @NotNull CaveFieldSampler[] fieldSamplers;
    private final @NotNull String[] fieldPrunedProfilerKeys;
    private final @NotNull String[] fieldPrunedByBestProfilerKeys;
    private final @NotNull String[] fieldPrunedByPenaltyProfilerKeys;
    private final @NotNull String[] fieldEarlyAcceptProfilerKeys;
    private final @NotNull FastNoise warpNoise;
    private final @NotNull FastNoise chamberNoise;
    private final @NotNull ThreadLocal<DensitySampleContext> directSampleContext;

    public DensityCompositeCaveSampler(@NotNull TerraformWorld tw, @NotNull CaveFieldSampler... fieldSamplers) {
        this.tw = tw;
        this.fieldSamplers = fieldSamplers.clone();
        this.fieldPrunedProfilerKeys = new String[this.fieldSamplers.length];
        this.fieldPrunedByBestProfilerKeys = new String[this.fieldSamplers.length];
        this.fieldPrunedByPenaltyProfilerKeys = new String[this.fieldSamplers.length];
        this.fieldEarlyAcceptProfilerKeys = new String[this.fieldSamplers.length];
        for (int i = 0; i < this.fieldSamplers.length; i++) {
            String profilerKey = this.fieldSamplers[i].getProfilerKey();
            this.fieldPrunedProfilerKeys[i] = "cave-v3.sample.field." + profilerKey + ".pruned";
            this.fieldPrunedByBestProfilerKeys[i] = "cave-v3.sample.field." + profilerKey + ".pruned-by-best";
            this.fieldPrunedByPenaltyProfilerKeys[i] = "cave-v3.sample.field." + profilerKey + ".pruned-by-penalty";
            this.fieldEarlyAcceptProfilerKeys[i] = "cave-v3.sample.field." + profilerKey + ".early-accept";
        }
        float baseFrequency = Math.max(0.0001f, TConfig.c.CAVES_DENSITY_V1_FREQUENCY);
        this.warpNoise = NoiseCacheHandler.getNoise(
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
        this.chamberNoise = NoiseCacheHandler.getNoise(
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
        this.directSampleContext = ThreadLocal.withInitial(this::createSampleContext);
    }

    @Override
    public boolean canCarve(int rawX, int y, int rawZ, double baseSurfaceHeight, @NotNull ChunkCache cache) {
        DensityCarveRules.ColumnContext columnContext = DensityCarveRules.createColumnContext(
                tw,
                rawX,
                rawZ,
                baseSurfaceHeight,
                cache
        );
        return canCarve(rawX, y, rawZ, baseSurfaceHeight, columnContext, directSampleContext.get());
    }

    @Override
    public @NotNull CompositeCaveColumnSampler createColumnSampler(int rawX,
                                                                   int rawZ,
                                                                   double baseSurfaceHeight,
                                                                   @NotNull ChunkCache cache)
    {
        DensityCarveRules.ColumnContext columnContext = DensityCarveRules.createColumnContext(
                tw,
                rawX,
                rawZ,
                baseSurfaceHeight,
                cache
        );
        return new DensityColumnSampler(rawX, rawZ, baseSurfaceHeight, columnContext, createSampleContext());
    }

    private boolean canCarve(int rawX,
                             int y,
                             int rawZ,
                             double baseSurfaceHeight,
                             @NotNull DensityCarveRules.ColumnContext columnContext,
                             @NotNull DensitySampleContext sampleContext)
    {
        if (!columnContext.canCarveAtY(y)) {
            return false;
        }

        CaveV3Profiler.recordEvent("cave-v3.sample.voxel-considered");
        try (CaveV3Profiler.Scope ignored = CaveV3Profiler.start("cave-v3.sample.shared-basis")) {
            sampleContext.prepare(rawX, y, rawZ, baseSurfaceHeight);
        }
        float globalPenalty = columnContext.getGlobalPenalty(y);
        float bestLocalScore = NEGATIVE_INFINITY_SCORE;
        for (int i = 0; i < fieldSamplers.length; i++) {
            CaveFieldSampler fieldSampler = fieldSamplers[i];
            float minRelevantLocalScore = Math.max(bestLocalScore, globalPenalty);
            // Keep the "can beat" check separate from exact scoring. In the tunnel field this looks redundant,
            // but the dedicated negative proof materially reduces how often the exact tunnel path runs.
            if (!fieldSampler.canBeatLocalScore(sampleContext, minRelevantLocalScore)) {
                CaveV3Profiler.recordEvent(fieldPrunedProfilerKeys[i]);
                if (bestLocalScore >= globalPenalty) {
                    CaveV3Profiler.recordEvent(fieldPrunedByBestProfilerKeys[i]);
                }
                else {
                    CaveV3Profiler.recordEvent(fieldPrunedByPenaltyProfilerKeys[i]);
                }
                continue;
            }

            float fieldLocalScore = fieldSampler.sampleLocalScore(sampleContext, minRelevantLocalScore);
            if (fieldLocalScore > bestLocalScore) {
                bestLocalScore = fieldLocalScore;
                if (bestLocalScore - globalPenalty >= 0f) {
                    CaveV3Profiler.recordEvent(fieldEarlyAcceptProfilerKeys[i]);
                    return true;
                }
            }
        }

        return bestLocalScore > NEGATIVE_INFINITY_SCORE / 2f && bestLocalScore - globalPenalty >= 0f;
    }

    private @NotNull DensitySampleContext createSampleContext() {
        return new DensitySampleContext(warpNoise, chamberNoise);
    }

    private final class DensityColumnSampler implements CompositeCaveColumnSampler {
        private final int rawX;
        private final int rawZ;
        private final double baseSurfaceHeight;
        private final @NotNull DensityCarveRules.ColumnContext columnContext;
        private final @NotNull DensitySampleContext sampleContext;

        private DensityColumnSampler(int rawX,
                                     int rawZ,
                                     double baseSurfaceHeight,
                                     @NotNull DensityCarveRules.ColumnContext columnContext,
                                     @NotNull DensitySampleContext sampleContext)
        {
            this.rawX = rawX;
            this.rawZ = rawZ;
            this.baseSurfaceHeight = baseSurfaceHeight;
            this.columnContext = columnContext;
            this.sampleContext = sampleContext;
        }

        @Override
        public boolean canCarve(int y) {
            return DensityCompositeCaveSampler.this.canCarve(
                    rawX,
                    y,
                    rawZ,
                    baseSurfaceHeight,
                    columnContext,
                    sampleContext
            );
        }
    }
}
