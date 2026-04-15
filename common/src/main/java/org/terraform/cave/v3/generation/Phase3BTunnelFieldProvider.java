package org.terraform.cave.v3.generation;

import org.jetbrains.annotations.NotNull;
import org.terraform.cave.v3.CaveV3Profiler;
import org.terraform.data.TerraformWorld;
import org.terraform.main.config.TConfig;
import org.terraform.utils.noise.FastNoise;
import org.terraform.utils.noise.NoiseCacheHandler;

public final class Phase3BTunnelFieldProvider implements CaveFieldProvider {
    private static final int TUNNEL_CLEARANCE_COUNT = 5;
    private static final String BRANCH_PRUNED_BEFORE_AXIS_PROFILER_KEY =
            "cave-v3.sample.field.tunnel.branch-pruned-before-axis";
    private static final String BRANCH_PRUNED_AFTER_FIRST_AXIS_PROFILER_KEY =
            "cave-v3.sample.field.tunnel.branch-pruned-after-first-axis";
    private static final String BRANCH_ENTERED_PROFILER_KEY = "cave-v3.sample.field.tunnel.branch-entered";
    private static final String BRANCH_PRUNED_PROFILER_KEY = "cave-v3.sample.field.tunnel.branch-pruned";
    private static final String CLEARANCE_ENTERED_PROFILER_KEY = "cave-v3.sample.field.tunnel.clearance-entered";
    private static final String CLEARANCE_PRUNED_PROFILER_KEY = "cave-v3.sample.field.tunnel.clearance-pruned";
    private static final String FULL_STACK_PROFILER_KEY = "cave-v3.sample.field.tunnel.full-stack";
    private static final String FULL_THRESHOLD_HIT_PROFILER_KEY = "cave-v3.sample.field.tunnel.full-threshold-hit";
    private static final String FULL_THRESHOLD_FALSE_POSITIVE_PROFILER_KEY =
            "cave-v3.sample.field.tunnel.full-threshold-false-positive";
    private static final String PROBE_GEOMETRY_PROFILER_KEY = "cave-v3.sample.field.tunnel.probe.geometry";
    private static final String PROBE_PRESUPPORT_PRUNED_PROFILER_KEY =
            "cave-v3.sample.field.tunnel.probe.pre-support-pruned";
    private static final String PROBE_CONTINUITY_PRUNED_PROFILER_KEY =
            "cave-v3.sample.field.tunnel.probe.continuity-pruned";
    private static final String PROBE_SUPPORT_PRUNED_PROFILER_KEY = "cave-v3.sample.field.tunnel.probe.support-pruned";
    private static final String PROBE_GEOMETRY_THRESHOLD_HIT_PROFILER_KEY =
            "cave-v3.sample.field.tunnel.probe.geometry-threshold-hit";
    private static final String PROBE_GEOMETRY_FULL_BOUND_PROFILER_KEY =
            "cave-v3.sample.field.tunnel.probe.geometry-full-bound";

    private static final float TUNNEL_HORIZONTAL_STRETCH_A = 0.17f;
    private static final float TUNNEL_HORIZONTAL_STRETCH_B = 0.15f;
    private static final float TUNNEL_VERTICAL_STRETCH_A = 0.24f;
    private static final float TUNNEL_VERTICAL_STRETCH_B = 0.22f;
    private static final float TUNNEL_WIDTH_A = 0.23f;
    private static final float TUNNEL_WIDTH_B = 0.2f;
    private static final float TUNNEL_VERTICAL_CLEARANCE_OFFSET = 2.35f;
    private static final float TUNNEL_VERTICAL_CLEARANCE_SECONDARY_OFFSET = 4.45f;
    private static final float TUNNEL_VERTICAL_CLEARANCE_SECONDARY_WEIGHT = 0.82f;
    private static final float SECONDARY_CLEARANCE_WIDTH_SCALE = 0.92f;

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
    private static final float[] TUNNEL_CLEARANCE_Y_OFFSETS = {
            0f,
            TUNNEL_VERTICAL_CLEARANCE_OFFSET,
            -TUNNEL_VERTICAL_CLEARANCE_OFFSET,
            TUNNEL_VERTICAL_CLEARANCE_SECONDARY_OFFSET,
            -TUNNEL_VERTICAL_CLEARANCE_SECONDARY_OFFSET
    };
    private static final float[] TUNNEL_CLEARANCE_WEIGHTS = {
            1f,
            1f,
            1f,
            TUNNEL_VERTICAL_CLEARANCE_SECONDARY_WEIGHT,
            TUNNEL_VERTICAL_CLEARANCE_SECONDARY_WEIGHT
    };
    private static final float[] TUNNEL_CLEARANCE_WIDTH_SCALES = {
            1f,
            1f,
            1f,
            SECONDARY_CLEARANCE_WIDTH_SCALE,
            SECONDARY_CLEARANCE_WIDTH_SCALE
    };

    @Override
    public @NotNull CaveFieldSampler createSampler(@NotNull TerraformWorld tw) {
        float baseFrequency = Math.max(0.0001f, TConfig.c.CAVES_DENSITY_V1_FREQUENCY);
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
        return new CaveFieldSampler() {
            @Override
            public float sampleLocalScore(@NotNull DensitySampleContext context) {
                try (CaveV3Profiler.Scope ignored = CaveV3Profiler.start("cave-v3.sample.field.tunnel.full")) {
                    return sampleExactTunnelLocalScore(
                            tunnelNoiseA,
                            tunnelNoiseB,
                            continuityNoise,
                            junctionNoise,
                            context,
                            Float.POSITIVE_INFINITY
                    );
                }
            }

            @Override
            public float sampleLocalScore(@NotNull DensitySampleContext context, float minRelevantLocalScore) {
                try (CaveV3Profiler.Scope ignored = CaveV3Profiler.start("cave-v3.sample.field.tunnel.full")) {
                    return sampleExactTunnelLocalScore(
                            tunnelNoiseA,
                            tunnelNoiseB,
                            continuityNoise,
                            junctionNoise,
                            context,
                            minRelevantLocalScore
                    );
                }
            }

            @Override
            public float getUpperBoundLocalScore(@NotNull DensitySampleContext context) {
                return getTunnelUpperBoundLocalScore(
                        tunnelNoiseA,
                        tunnelNoiseB,
                        continuityNoise,
                        junctionNoise,
                        context
                );
            }

            @Override
            public boolean canBeatLocalScore(@NotNull DensitySampleContext context, float minRelevantLocalScore) {
                return canTunnelBeatLocalScore(
                        tunnelNoiseA,
                        tunnelNoiseB,
                        continuityNoise,
                        junctionNoise,
                        context,
                        minRelevantLocalScore
                );
            }

            @Override
            public @NotNull String getProfilerKey() {
                return "tunnel";
            }
        };
    }

    private static float sampleExactTunnelLocalScore(@NotNull FastNoise tunnelNoiseA,
                                                     @NotNull FastNoise tunnelNoiseB,
                                                     @NotNull FastNoise continuityNoise,
                                                     @NotNull FastNoise junctionNoise,
                                                     @NotNull DensitySampleContext context,
                                                     float minRelevantLocalScore)
    {
        // The support probe stays separate from exact geometry accumulation on purpose:
        // it is the cheap exact-negative stage that keeps tunnel.full from exploding.
        ensureTunnelProbe(continuityNoise, junctionNoise, context);
        float requiredSmoothedCore = Float.isFinite(minRelevantLocalScore)
                                     ? getRequiredSmoothedTunnelCore(context, minRelevantLocalScore)
                                     : Float.POSITIVE_INFINITY;

        float tunnelCore = 0f;
        int clearancesEntered = 0;
        for (int clearanceIndex = 0; clearanceIndex < TUNNEL_CLEARANCE_COUNT; clearanceIndex++) {
            float clearanceWeight = TUNNEL_CLEARANCE_WEIGHTS[clearanceIndex];
            boolean clearanceEntered = canClearanceImprove(clearanceWeight, tunnelCore);
            tunnelCore = sampleTunnelClearanceContribution(
                    tunnelNoiseA,
                    tunnelNoiseB,
                    context,
                    context.warpedX,
                    context.warpedY + TUNNEL_CLEARANCE_Y_OFFSETS[clearanceIndex],
                    context.warpedZ,
                    context.tunnelWidthBoost * TUNNEL_CLEARANCE_WIDTH_SCALES[clearanceIndex],
                    clearanceWeight,
                    tunnelCore,
                    clearanceIndex
            );
            if (clearanceEntered) {
                clearancesEntered++;
            }

            if (Float.isFinite(requiredSmoothedCore) && smoothTunnelCore(tunnelCore) >= requiredSmoothedCore) {
                float localScore = getTunnelLocalScoreForCore(context, tunnelCore);
                if (localScore >= minRelevantLocalScore) {
                    CaveV3Profiler.recordEvent(FULL_THRESHOLD_HIT_PROFILER_KEY);
                    return localScore;
                }
                CaveV3Profiler.recordEvent(FULL_THRESHOLD_FALSE_POSITIVE_PROFILER_KEY);
            }

        }

        if (clearancesEntered == TUNNEL_CLEARANCE_COUNT) {
            CaveV3Profiler.recordEvent(FULL_STACK_PROFILER_KEY);
        }
        return getTunnelLocalScoreForCore(context, tunnelCore);
    }

    private static float getTunnelUpperBoundLocalScore(@NotNull FastNoise tunnelNoiseA,
                                                       @NotNull FastNoise tunnelNoiseB,
                                                       @NotNull FastNoise continuityNoise,
                                                       @NotNull FastNoise junctionNoise,
                                                       @NotNull DensitySampleContext context)
    {
        ensureTunnelProbe(continuityNoise, junctionNoise, context);
        float tunnelCoreUpperBound = getTunnelCoreUpperBound(tunnelNoiseA, tunnelNoiseB, context);
        return getTunnelLocalScoreForCore(context, tunnelCoreUpperBound);
    }

    private static boolean canTunnelBeatLocalScore(@NotNull FastNoise tunnelNoiseA,
                                                   @NotNull FastNoise tunnelNoiseB,
                                                   @NotNull FastNoise continuityNoise,
                                                   @NotNull FastNoise junctionNoise,
                                                   @NotNull DensitySampleContext context,
                                                   float minRelevantLocalScore)
    {
        if (!Float.isFinite(minRelevantLocalScore)) {
            return true;
        }

        float requiredDensity = DensityFieldScore.minBaseDensityForLocalScore(minRelevantLocalScore);
        if (!Float.isFinite(requiredDensity)) {
            return requiredDensity == Float.NEGATIVE_INFINITY;
        }
        if (requiredDensity > getMaxPossibleTunnelDensityWithoutProbe(context)) {
            CaveV3Profiler.recordEvent(PROBE_PRESUPPORT_PRUNED_PROFILER_KEY);
            return false;
        }

        float continuity = ensureTunnelContinuity(continuityNoise, context);
        if (requiredDensity > getMaxPossibleTunnelDensityWithoutJunction(context, continuity)) {
            CaveV3Profiler.recordEvent(PROBE_CONTINUITY_PRUNED_PROFILER_KEY);
            return false;
        }

        ensureTunnelProbe(continuityNoise, junctionNoise, context);
        float requiredSmoothedCore = getRequiredSmoothedTunnelCore(context, minRelevantLocalScore);
        if (!Float.isFinite(requiredSmoothedCore)) {
            CaveV3Profiler.recordEvent(PROBE_SUPPORT_PRUNED_PROFILER_KEY);
            return false;
        }
        if (requiredSmoothedCore <= 0f) {
            return true;
        }
        return canTunnelCoreUpperBoundBeatLocalScore(
                tunnelNoiseA,
                tunnelNoiseB,
                context,
                requiredSmoothedCore
        );
    }

    private static boolean canTunnelCoreUpperBoundBeatLocalScore(@NotNull FastNoise tunnelNoiseA,
                                                                 @NotNull FastNoise tunnelNoiseB,
                                                                 @NotNull DensitySampleContext context,
                                                                 float requiredSmoothedCore)
    {
        if (context.tunnelGeometryUpperBoundComputed) {
            return smoothTunnelCore(context.tunnelGeometryUpperBound) >= requiredSmoothedCore;
        }

        try (CaveV3Profiler.Scope ignored = CaveV3Profiler.start(PROBE_GEOMETRY_PROFILER_KEY)) {
            float tunnelCoreUpperBound = 0f;
            // This geometry probe is intentionally conservative and separate from tunnel.full.
            // Past attempts to fuse the two without preserving equally strong negative rejection
            // caused tunnel.full sampling to spike and regressed total generation time.
            for (int clearanceIndex = 0; clearanceIndex < TUNNEL_CLEARANCE_COUNT; clearanceIndex++) {
                tunnelCoreUpperBound = Math.max(
                        tunnelCoreUpperBound,
                        sampleTunnelClearanceUpperBound(
                                tunnelNoiseA,
                                tunnelNoiseB,
                                context,
                                context.warpedX,
                                context.warpedY + TUNNEL_CLEARANCE_Y_OFFSETS[clearanceIndex],
                                context.warpedZ,
                                context.tunnelWidthBoost * TUNNEL_CLEARANCE_WIDTH_SCALES[clearanceIndex],
                                TUNNEL_CLEARANCE_WEIGHTS[clearanceIndex],
                                tunnelCoreUpperBound,
                                clearanceIndex
                        )
                );
                if (smoothTunnelCore(tunnelCoreUpperBound) >= requiredSmoothedCore) {
                    CaveV3Profiler.recordEvent(PROBE_GEOMETRY_THRESHOLD_HIT_PROFILER_KEY);
                    return true;
                }
            }
            context.cacheTunnelGeometryUpperBound(tunnelCoreUpperBound);
            CaveV3Profiler.recordEvent(PROBE_GEOMETRY_FULL_BOUND_PROFILER_KEY);
            return smoothTunnelCore(tunnelCoreUpperBound) >= requiredSmoothedCore;
        }
    }

    private static float getTunnelCoreUpperBound(@NotNull FastNoise tunnelNoiseA,
                                                 @NotNull FastNoise tunnelNoiseB,
                                                 @NotNull DensitySampleContext context)
    {
        if (context.tunnelGeometryUpperBoundComputed) {
            return context.tunnelGeometryUpperBound;
        }

        float tunnelCoreUpperBound = 0f;
        try (CaveV3Profiler.Scope ignored = CaveV3Profiler.start(PROBE_GEOMETRY_PROFILER_KEY)) {
            for (int clearanceIndex = 0; clearanceIndex < TUNNEL_CLEARANCE_COUNT; clearanceIndex++) {
                tunnelCoreUpperBound = Math.max(
                        tunnelCoreUpperBound,
                        sampleTunnelClearanceUpperBound(
                                tunnelNoiseA,
                                tunnelNoiseB,
                                context,
                                context.warpedX,
                                context.warpedY + TUNNEL_CLEARANCE_Y_OFFSETS[clearanceIndex],
                                context.warpedZ,
                                context.tunnelWidthBoost * TUNNEL_CLEARANCE_WIDTH_SCALES[clearanceIndex],
                                TUNNEL_CLEARANCE_WEIGHTS[clearanceIndex],
                                tunnelCoreUpperBound,
                                clearanceIndex
                        )
                );
            }
        }
        context.cacheTunnelGeometryUpperBound(tunnelCoreUpperBound);
        CaveV3Profiler.recordEvent(PROBE_GEOMETRY_FULL_BOUND_PROFILER_KEY);
        return tunnelCoreUpperBound;
    }

    private static void ensureTunnelProbe(@NotNull FastNoise continuityNoise,
                                          @NotNull FastNoise junctionNoise,
                                          @NotNull DensitySampleContext context)
    {
        if (context.tunnelProbeComputed) {
            return;
        }

        try (CaveV3Profiler.Scope ignored = CaveV3Profiler.start("cave-v3.sample.field.tunnel.probe")) {
            float chamberLink = getTunnelChamberLink(context);
            float continuity = ensureTunnelContinuity(continuityNoise, context);

            float junction = 0.5f + (0.5f * junctionNoise.GetNoise(
                    context.warpedX * JUNCTION_HORIZONTAL_STRETCH,
                    context.warpedY * JUNCTION_VERTICAL_STRETCH,
                    context.warpedZ * JUNCTION_HORIZONTAL_STRETCH
            ));
            junction = DensityCarveRules.clamp01((junction - 0.62f) / 0.24f) * (0.45f + (0.55f * chamberLink));

            float tunnelWidthBoost = 0.03f + (continuity * 0.09f) + (chamberLink * 0.07f) + (junction * 0.06f);
            float nearSurfaceFade = DensityCarveRules.clamp01(
                    (context.depthBelowSurface - MIN_FULL_CARVE_DEPTH) / SURFACE_FADE_DEPTH
            );
            float deepBoost = DensityCarveRules.clamp01((context.depthBelowSurface - DEEP_BOOST_START) / DEEP_BOOST_RANGE);
            float densityScale = 0.7f + (nearSurfaceFade * 0.3f);
            float deepDensityOffset = deepBoost * 0.05f;
            float tunnelPivotSmoothedCore = chamberLink * 0.75f;
            float baseDensityIntercept = BASE_DENSITY_OFFSET + (chamberLink * CHAMBER_LINK_WEIGHT * 0.35f);
            float commonDensitySlope = TUNNEL_WEIGHT
                                       + (continuity * CONTINUITY_WEIGHT)
                                       + (chamberLink * CHAMBER_LINK_WEIGHT * 0.65f);
            float tunnelDensityBelowPivotIntercept = (baseDensityIntercept
                                                      + (junction * JUNCTION_WEIGHT * tunnelPivotSmoothedCore))
                                                     * densityScale
                                                     + deepDensityOffset;
            float tunnelDensityBelowPivotSlope = commonDensitySlope * densityScale;
            float tunnelDensityAbovePivotIntercept = (baseDensityIntercept * densityScale) + deepDensityOffset;
            float tunnelDensityAbovePivotSlope = (commonDensitySlope + (junction * JUNCTION_WEIGHT)) * densityScale;
            context.cacheTunnelProbe(
                    chamberLink,
                    continuity,
                    junction,
                    tunnelWidthBoost,
                    tunnelDensityBelowPivotIntercept,
                    tunnelDensityBelowPivotSlope,
                    tunnelDensityAbovePivotIntercept,
                    tunnelDensityAbovePivotSlope,
                    tunnelPivotSmoothedCore
            );
        }
    }

    private static float getTunnelLocalScoreForCore(@NotNull DensitySampleContext context, float tunnelCore) {
        return DensityFieldScore.toLocalScore(getTunnelDensityForSmoothedCore(context, smoothTunnelCore(tunnelCore)));
    }

    private static float getMaxPossibleTunnelDensityWithoutProbe(@NotNull DensitySampleContext context) {
        float chamberLink = getTunnelChamberLink(context);
        float maxJunction = 0.45f + (0.55f * chamberLink);
        return getMaxPossibleTunnelDensity(context, chamberLink, 1f, maxJunction);
    }

    private static float getMaxPossibleTunnelDensityWithoutJunction(@NotNull DensitySampleContext context,
                                                                    float continuity)
    {
        float chamberLink = getTunnelChamberLink(context);
        float maxJunction = 0.45f + (0.55f * chamberLink);
        return getMaxPossibleTunnelDensity(context, chamberLink, continuity, maxJunction);
    }

    private static float getMaxPossibleTunnelDensity(@NotNull DensitySampleContext context,
                                                     float chamberLink,
                                                     float continuity,
                                                     float junction)
    {
        return getMaxPossibleTunnelDensity(context.depthBelowSurface, chamberLink, continuity, junction);
    }

    private static float getMaxPossibleTunnelDensity(float depthBelowSurface,
                                                     float chamberLink,
                                                     float continuity,
                                                     float junction)
    {
        float nearSurfaceFade = DensityCarveRules.clamp01(
                (depthBelowSurface - MIN_FULL_CARVE_DEPTH) / SURFACE_FADE_DEPTH
        );
        float deepBoost = DensityCarveRules.clamp01((depthBelowSurface - DEEP_BOOST_START) / DEEP_BOOST_RANGE);
        float densityScale = 0.7f + (nearSurfaceFade * 0.3f);
        float deepDensityOffset = deepBoost * 0.05f;
        float baseDensityIntercept = BASE_DENSITY_OFFSET + (chamberLink * CHAMBER_LINK_WEIGHT * 0.35f);
        float commonDensitySlope = TUNNEL_WEIGHT + (continuity * CONTINUITY_WEIGHT)
                                   + (chamberLink * CHAMBER_LINK_WEIGHT * 0.65f);
        float tunnelDensityAbovePivotIntercept = (baseDensityIntercept * densityScale) + deepDensityOffset;
        float tunnelDensityAbovePivotSlope = (commonDensitySlope + (junction * JUNCTION_WEIGHT)) * densityScale;
        return tunnelDensityAbovePivotIntercept + tunnelDensityAbovePivotSlope;
    }

    private static float getTunnelChamberLink(@NotNull DensitySampleContext context) {
        if (context.tunnelProbeComputed) {
            return context.tunnelChamberLink;
        }
        return DensityCarveRules.clamp01((context.chamber - 0.38f) / 0.36f);
    }

    private static float ensureTunnelContinuity(@NotNull FastNoise continuityNoise,
                                                @NotNull DensitySampleContext context)
    {
        if (context.tunnelProbeComputed || context.tunnelContinuityComputed) {
            return context.tunnelContinuity;
        }

        float continuity;
        try (CaveV3Profiler.Scope ignored = CaveV3Profiler.start("cave-v3.sample.field.tunnel.probe.continuity")) {
            continuity = 0.5f + (0.5f * continuityNoise.GetNoise(
                    context.warpedX * CONTINUITY_HORIZONTAL_STRETCH,
                    context.warpedY * CONTINUITY_VERTICAL_STRETCH,
                    context.warpedZ * CONTINUITY_HORIZONTAL_STRETCH
            ));
            continuity = DensityCarveRules.clamp01((continuity - 0.22f) / 0.56f);
        }
        context.cacheTunnelContinuity(continuity);
        return continuity;
    }

    private static float getRequiredSmoothedTunnelCore(@NotNull DensitySampleContext context, float minRelevantLocalScore) {
        if (context.hasTunnelRequiredSmoothedCore(minRelevantLocalScore)) {
            return context.getTunnelRequiredSmoothedCore();
        }

        float requiredDensity = DensityFieldScore.minBaseDensityForLocalScore(minRelevantLocalScore);
        float requiredSmoothedCore = computeRequiredSmoothedTunnelCore(context, requiredDensity);
        context.cacheTunnelRequiredSmoothedCore(minRelevantLocalScore, requiredSmoothedCore);
        return requiredSmoothedCore;
    }

    private static float computeRequiredSmoothedTunnelCore(@NotNull DensitySampleContext context, float requiredDensity) {
        if (Float.isNaN(requiredDensity)) {
            return Float.NaN;
        }
        if (requiredDensity == Float.NEGATIVE_INFINITY) {
            return 0f;
        }
        if (requiredDensity == Float.POSITIVE_INFINITY) {
            return Float.POSITIVE_INFINITY;
        }

        if (requiredDensity <= context.tunnelDensityBelowPivotIntercept) {
            return 0f;
        }

        float pivotDensity = getTunnelDensityForSmoothedCore(context, context.tunnelPivotSmoothedCore);
        if (requiredDensity <= pivotDensity) {
            return makeSmoothedCoreThresholdSafe(
                    context,
                    requiredDensity,
                    solveSmoothedTunnelCore(
                            requiredDensity,
                            context.tunnelDensityBelowPivotIntercept,
                            context.tunnelDensityBelowPivotSlope
                    )
            );
        }

        float maxDensity = getTunnelDensityForSmoothedCore(context, 1f);
        if (requiredDensity > maxDensity) {
            return Float.POSITIVE_INFINITY;
        }

        return makeSmoothedCoreThresholdSafe(
                context,
                requiredDensity,
                solveSmoothedTunnelCore(
                        requiredDensity,
                        context.tunnelDensityAbovePivotIntercept,
                        context.tunnelDensityAbovePivotSlope
                )
        );
    }

    private static float solveSmoothedTunnelCore(float requiredDensity, float intercept, float slope) {
        if (slope <= 0f) {
            return requiredDensity <= intercept ? 0f : Float.POSITIVE_INFINITY;
        }
        return DensityCarveRules.clamp01((requiredDensity - intercept) / slope);
    }

    private static float makeSmoothedCoreThresholdSafe(@NotNull DensitySampleContext context,
                                                       float requiredDensity,
                                                       float requiredSmoothedCore)
    {
        if (!Float.isFinite(requiredDensity) || !Float.isFinite(requiredSmoothedCore)) {
            return requiredSmoothedCore;
        }

        float safeRequiredSmoothedCore = DensityCarveRules.clamp01(requiredSmoothedCore);
        for (int i = 0; i < 4 && getTunnelDensityForSmoothedCore(context, safeRequiredSmoothedCore) < requiredDensity; i++) {
            float next = Math.nextUp(safeRequiredSmoothedCore);
            if (next == safeRequiredSmoothedCore) {
                break;
            }
            safeRequiredSmoothedCore = DensityCarveRules.clamp01(next);
        }
        return safeRequiredSmoothedCore;
    }

    private static float getTunnelDensityForSmoothedCore(@NotNull DensitySampleContext context, float smoothedTunnelCore) {
        if (smoothedTunnelCore >= context.tunnelPivotSmoothedCore) {
            return context.tunnelDensityAbovePivotIntercept
                   + (context.tunnelDensityAbovePivotSlope * smoothedTunnelCore);
        }
        return context.tunnelDensityBelowPivotIntercept
               + (context.tunnelDensityBelowPivotSlope * smoothedTunnelCore);
    }

    private static float smoothTunnelCore(float tunnelCore) {
        return tunnelCore * tunnelCore * (3f - (2f * tunnelCore));
    }

    private static float sampleTunnelClearanceContribution(@NotNull FastNoise tunnelNoiseA,
                                                           @NotNull FastNoise tunnelNoiseB,
                                                           @NotNull DensitySampleContext context,
                                                           float warpedX,
                                                           float warpedY,
                                                           float warpedZ,
                                                           float widthBoost,
                                                           float clearanceWeight,
                                                           float currentBestContribution,
                                                           int clearanceIndex)
    {
        if (!canClearanceImprove(clearanceWeight, currentBestContribution)) {
            CaveV3Profiler.recordEvent(CLEARANCE_PRUNED_PROFILER_KEY);
            return currentBestContribution;
        }

        CaveV3Profiler.recordEvent(CLEARANCE_ENTERED_PROFILER_KEY);

        float clearanceBestContribution = sampleTunnelBranchContribution(
                tunnelNoiseA,
                tunnelNoiseB,
                context,
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
                -26.1f,
                clearanceWeight,
                1f,
                currentBestContribution,
                getTunnelBranchSlot(clearanceIndex, false)
        );
        return sampleTunnelBranchContribution(
                tunnelNoiseA,
                tunnelNoiseB,
                context,
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
                33.4f,
                clearanceWeight,
                0.94f,
                clearanceBestContribution,
                getTunnelBranchSlot(clearanceIndex, true)
        );
    }

    private static float sampleTunnelClearanceUpperBound(@NotNull FastNoise tunnelNoiseA,
                                                         @NotNull FastNoise tunnelNoiseB,
                                                         @NotNull DensitySampleContext context,
                                                         float warpedX,
                                                         float warpedY,
                                                         float warpedZ,
                                                         float widthBoost,
                                                         float clearanceWeight,
                                                         float currentBestUpperBound,
                                                         int clearanceIndex)
    {
        if (!canClearanceImprove(clearanceWeight, currentBestUpperBound)) {
            return currentBestUpperBound;
        }

        float clearanceUpperBound = sampleTunnelBranchUpperBound(
                tunnelNoiseA,
                tunnelNoiseB,
                context,
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
                -26.1f,
                clearanceWeight,
                1f,
                currentBestUpperBound,
                getTunnelBranchSlot(clearanceIndex, false)
        );
        return sampleTunnelBranchUpperBound(
                tunnelNoiseA,
                tunnelNoiseB,
                context,
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
                33.4f,
                clearanceWeight,
                0.94f,
                clearanceUpperBound,
                getTunnelBranchSlot(clearanceIndex, true)
        );
    }

    private static boolean canClearanceImprove(float clearanceWeight, float currentBestContribution) {
        return clearanceWeight > currentBestContribution;
    }

    private static float sampleTunnelBranchContribution(@NotNull FastNoise tunnelNoiseA,
                                                        @NotNull FastNoise tunnelNoiseB,
                                                        @NotNull DensitySampleContext context,
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
                                                        float offsetZ,
                                                        float clearanceWeight,
                                                        float branchWeight,
                                                        float currentBestContribution,
                                                        int branchSlot)
    {
        float maxBranchContribution = clearanceWeight * branchWeight;
        if (maxBranchContribution <= currentBestContribution) {
            CaveV3Profiler.recordEvent(BRANCH_PRUNED_PROFILER_KEY);
            CaveV3Profiler.recordEvent(BRANCH_PRUNED_BEFORE_AXIS_PROFILER_KEY);
            return currentBestContribution;
        }

        if (widthA <= widthB) {
            float axisA = getTunnelBranchProbeAxis(
                    tunnelNoiseA,
                    tunnelNoiseB,
                    context,
                    warpedX,
                    warpedY,
                    warpedZ,
                    stretchAX,
                    stretchAY,
                    stretchBX,
                    stretchBY,
                    offsetX,
                    offsetY,
                    offsetZ,
                    widthA,
                    widthB,
                    branchSlot
            );
            float branchUpperBound = maxBranchContribution * getSingleAxisMaskUpperBound(axisA, widthA);
            if (branchUpperBound <= currentBestContribution) {
                CaveV3Profiler.recordEvent(BRANCH_PRUNED_PROFILER_KEY);
                CaveV3Profiler.recordEvent(BRANCH_PRUNED_AFTER_FIRST_AXIS_PROFILER_KEY);
                return currentBestContribution;
            }

            CaveV3Profiler.recordEvent(BRANCH_ENTERED_PROFILER_KEY);
            float axisB = tunnelNoiseB.GetNoise(
                    (warpedX * stretchBX) + offsetX,
                    (warpedY * stretchBY) + offsetY,
                    (warpedZ * stretchBX) + offsetZ
            );
            return Math.max(
                    currentBestContribution,
                    maxBranchContribution * getIntersectionMask(axisA, axisB, widthA, widthB)
            );
        }

        float axisB = getTunnelBranchProbeAxis(
                tunnelNoiseA,
                tunnelNoiseB,
                context,
                warpedX,
                warpedY,
                warpedZ,
                stretchAX,
                stretchAY,
                stretchBX,
                stretchBY,
                offsetX,
                offsetY,
                offsetZ,
                widthA,
                widthB,
                branchSlot
        );
        float branchUpperBound = maxBranchContribution * getSingleAxisMaskUpperBound(axisB, widthB);
        if (branchUpperBound <= currentBestContribution) {
            CaveV3Profiler.recordEvent(BRANCH_PRUNED_PROFILER_KEY);
            CaveV3Profiler.recordEvent(BRANCH_PRUNED_AFTER_FIRST_AXIS_PROFILER_KEY);
            return currentBestContribution;
        }

        CaveV3Profiler.recordEvent(BRANCH_ENTERED_PROFILER_KEY);
        float axisA = tunnelNoiseA.GetNoise(
                warpedX * stretchAX,
                warpedY * stretchAY,
                warpedZ * stretchAX
        );
        return Math.max(
                currentBestContribution,
                maxBranchContribution * getIntersectionMask(axisA, axisB, widthA, widthB)
        );
    }

    private static float sampleTunnelBranchUpperBound(@NotNull FastNoise tunnelNoiseA,
                                                      @NotNull FastNoise tunnelNoiseB,
                                                      @NotNull DensitySampleContext context,
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
                                                      float offsetZ,
                                                      float clearanceWeight,
                                                      float branchWeight,
                                                      float currentBestUpperBound,
                                                      int branchSlot)
    {
        float maxBranchContribution = clearanceWeight * branchWeight;
        if (maxBranchContribution <= currentBestUpperBound) {
            return currentBestUpperBound;
        }

        float firstAxis = getTunnelBranchProbeAxis(
                tunnelNoiseA,
                tunnelNoiseB,
                context,
                warpedX,
                warpedY,
                warpedZ,
                stretchAX,
                stretchAY,
                stretchBX,
                stretchBY,
                offsetX,
                offsetY,
                offsetZ,
                widthA,
                widthB,
                branchSlot
        );
        float firstWidth = widthA <= widthB ? widthA : widthB;
        return Math.max(currentBestUpperBound, maxBranchContribution * getSingleAxisMaskUpperBound(firstAxis, firstWidth));
    }

    private static float getTunnelBranchProbeAxis(@NotNull FastNoise tunnelNoiseA,
                                                  @NotNull FastNoise tunnelNoiseB,
                                                  DensitySampleContext context,
                                                  float warpedX,
                                                  float warpedY,
                                                  float warpedZ,
                                                  float stretchAX,
                                                  float stretchAY,
                                                  float stretchBX,
                                                  float stretchBY,
                                                  float offsetX,
                                                  float offsetY,
                                                  float offsetZ,
                                                  float widthA,
                                                  float widthB,
                                                  int branchSlot)
    {
        if (context != null && context.hasTunnelBranchProbeAxis(branchSlot)) {
            return context.getTunnelBranchProbeAxis(branchSlot);
        }

        float firstAxis;
        if (widthA <= widthB) {
            firstAxis = tunnelNoiseA.GetNoise(
                    warpedX * stretchAX,
                    warpedY * stretchAY,
                    warpedZ * stretchAX
            );
        }
        else {
            firstAxis = tunnelNoiseB.GetNoise(
                    (warpedX * stretchBX) + offsetX,
                    (warpedY * stretchBY) + offsetY,
                    (warpedZ * stretchBX) + offsetZ
            );
        }

        if (context != null) {
            context.cacheTunnelBranchProbeAxis(branchSlot, firstAxis);
        }
        return firstAxis;
    }

    private static int getTunnelBranchSlot(int clearanceIndex, boolean secondaryBranch) {
        return (clearanceIndex * 2) + (secondaryBranch ? 1 : 0);
    }

    private static float getSingleAxisMaskUpperBound(float axis, float width) {
        float safeWidth = Math.max(0.0001f, width);
        float radialLowerBound = Math.abs(axis) / safeWidth;
        float mask = 1f - DensityCarveRules.clamp01(radialLowerBound);
        return mask * mask * (3f - (2f * mask));
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
