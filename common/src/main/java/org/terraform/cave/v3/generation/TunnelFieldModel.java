package org.terraform.cave.v3.generation;

import org.jetbrains.annotations.NotNull;
import org.terraform.cave.v3.CaveV3Profiler;
import org.terraform.data.TerraformWorld;
import org.terraform.main.TerraformGeneratorPlugin;
import org.terraform.main.config.TConfig;
import org.terraform.utils.noise.FastNoise;
import org.terraform.utils.noise.NoiseCacheHandler;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class TunnelFieldModel {
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
    private static final String FULL_REMAINING_MAX_STOP_PROFILER_KEY =
            "cave-v3.sample.field.tunnel.full-remaining-max-stop";
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
    private static final String PROBE_GEOMETRY_REMAINING_MAX_STOP_PROFILER_KEY =
            "cave-v3.sample.field.tunnel.probe.geometry-remaining-max-stop";
    private static final int SUPPORTED_BRANCH_SLOT_COUNT = 10;
    private static final int MAX_VERIFIER_MISMATCH_LOGS = 16;
    private static final long VERIFIER_SUCCESS_LOG_INTERVAL_MILLIS = 5000L;
    private static final AtomicInteger VERIFIER_MISMATCH_LOGS = new AtomicInteger();
    private static final AtomicInteger VERIFIER_MATCHES_SINCE_LOG = new AtomicInteger();
    private static final AtomicLong LAST_VERIFIER_SUCCESS_LOG_MILLIS = new AtomicLong(System.currentTimeMillis());

    private final @NotNull Params params;
    private final int[] clearanceOrder;
    private final float[] remainingClearanceMaxContributions;

    TunnelFieldModel(@NotNull Params params) {
        this.params = params;
        validateParams(params);
        this.clearanceOrder = createClearanceOrder(params.clearances().length);
        this.remainingClearanceMaxContributions = computeRemainingClearanceMaxContributions(
                params.clearances(),
                clearanceOrder
        );
    }

    static @NotNull TunnelFieldModel createDefault() {
        float tunnelHorizontalStretchA = 0.05f;
        float tunnelHorizontalStretchB = 0.08f;
        float tunnelVerticalStretchA = 0.12f;
        float tunnelVerticalStretchB = 0.08f;
        float tunnelWidthA = 0.18f;
        float tunnelWidthB = 0.13f;
        float verticalClearanceOffset = 2.35f;
        float verticalClearanceSecondaryOffset = 4.45f;
        float verticalClearanceSecondaryWeight = 0.82f;
        float secondaryClearanceWidthScale = 0.92f;

        BranchShape primaryBranch = new BranchShape(
                0f,
                0f,
                0f,
                tunnelHorizontalStretchA,
                tunnelVerticalStretchA,
                tunnelHorizontalStretchB,
                tunnelVerticalStretchB,
                tunnelWidthA,
                1f,
                tunnelWidthB,
                0.9f,
                31.7f,
                -18.4f,
                -26.1f,
                1f
        );
        BranchShape secondaryBranch = new BranchShape(
                41.3f,
                -12.7f,
                -37.9f,
                tunnelHorizontalStretchB,
                tunnelVerticalStretchB,
                tunnelHorizontalStretchA,
                tunnelVerticalStretchA,
                tunnelWidthA * 0.92f,
                0.95f,
                tunnelWidthB * 0.92f,
                0.85f,
                -22.6f,
                14.1f,
                33.4f,
                0.94f
        );

        return new TunnelFieldModel(new Params(
                new NoiseSpec(NoiseCacheHandler.NoiseCacheEntry.CAVE_V3_TUNNEL_AXIS_NOISE_A, 97L, 0x37A61, 1.02f, 3),
                new NoiseSpec(NoiseCacheHandler.NoiseCacheEntry.CAVE_V3_TUNNEL_AXIS_NOISE_B, 101L, 0x4C8F3, 0.94f, 3),
                new NoiseSpec(NoiseCacheHandler.NoiseCacheEntry.CAVE_V3_TUNNEL_CONTINUITY_NOISE, 103L, 0x52D9B, 0.42f, 2),
                new NoiseSpec(NoiseCacheHandler.NoiseCacheEntry.CAVE_V3_TUNNEL_JUNCTION_NOISE, 109L, 0x6B42D, 0.56f, 2),
                4f,
                10f,
                16f,
                26f,
                -0.11f,
                0.66f,
                0.08f,
                0.12f,
                0.11f,
                0.7f,
                0.3f,
                0.05f,
                0.38f,
                0.36f,
                0.75f,
                0.35f,
                0.65f,
                0.08f,
                0.11f,
                0.22f,
                0.56f,
                0.1f,
                0.16f,
                0.62f,
                0.24f,
                0.45f,
                0.55f,
                0.03f,
                0.09f,
                0.07f,
                0.06f,
                new ClearanceShape[] {
                        new ClearanceShape(0f, 1f, 1f, primaryBranch, secondaryBranch),
                        new ClearanceShape(verticalClearanceOffset, 1f, 1f, primaryBranch, secondaryBranch),
                        new ClearanceShape(-verticalClearanceOffset, 1f, 1f, primaryBranch, secondaryBranch),
                        new ClearanceShape(
                                verticalClearanceSecondaryOffset,
                                verticalClearanceSecondaryWeight,
                                secondaryClearanceWidthScale,
                                primaryBranch,
                                secondaryBranch
                        ),
                        new ClearanceShape(
                                -verticalClearanceSecondaryOffset,
                                verticalClearanceSecondaryWeight,
                                secondaryClearanceWidthScale,
                                primaryBranch,
                                secondaryBranch
                        )
                }
        ));
    }

    @NotNull CaveFieldSampler createSampler(@NotNull TerraformWorld tw) {
        NoiseSet noiseSet = createNoiseSet(tw);
        return new CaveFieldSampler() {
            @Override
            public float sampleLocalScore(@NotNull DensitySampleContext context) {
                try (CaveV3Profiler.Scope ignored = CaveV3Profiler.start("cave-v3.sample.field.tunnel.full")) {
                    float localScore = sampleExactTunnelLocalScore(noiseSet, context, Float.POSITIVE_INFINITY);
                    verifyExactScore(noiseSet, context, localScore);
                    return localScore;
                }
            }

            @Override
            public float sampleLocalScore(@NotNull DensitySampleContext context, float minRelevantLocalScore) {
                try (CaveV3Profiler.Scope ignored = CaveV3Profiler.start("cave-v3.sample.field.tunnel.full")) {
                    float localScore = sampleExactTunnelLocalScore(noiseSet, context, minRelevantLocalScore);
                    verifyThresholdedSample(noiseSet, context, minRelevantLocalScore, localScore);
                    return localScore;
                }
            }

            @Override
            public float getUpperBoundLocalScore(@NotNull DensitySampleContext context) {
                return getTunnelUpperBoundLocalScore(noiseSet, context);
            }

            @Override
            public boolean canBeatLocalScore(@NotNull DensitySampleContext context, float minRelevantLocalScore) {
                boolean canBeat = canTunnelBeatLocalScore(noiseSet, context, minRelevantLocalScore);
                verifyCanBeat(noiseSet, context, minRelevantLocalScore, canBeat);
                return canBeat;
            }

            @Override
            public @NotNull String getProfilerKey() {
                return "tunnel";
            }
        };
    }

    private static void validateParams(@NotNull Params params) {
        // The current cache layout supports tuning the existing tunnel family cleanly,
        // but not changing the number of cached branch probes without a wider refactor.
        if (params.clearances().length * 2 != SUPPORTED_BRANCH_SLOT_COUNT) {
            throw new IllegalArgumentException(
                    "TunnelFieldModel currently supports " + SUPPORTED_BRANCH_SLOT_COUNT + " cached branch probe slots."
            );
        }
    }

    private @NotNull NoiseSet createNoiseSet(@NotNull TerraformWorld tw) {
        float baseFrequency = Math.max(0.0001f, TConfig.c.CAVES_DENSITY_V1_FREQUENCY);
        return new NoiseSet(
                createNoise(tw, params.axisNoiseA(), baseFrequency),
                createNoise(tw, params.axisNoiseB(), baseFrequency),
                createNoise(tw, params.continuityNoise(), baseFrequency),
                createNoise(tw, params.junctionNoise(), baseFrequency)
        );
    }

    private static @NotNull FastNoise createNoise(@NotNull TerraformWorld tw,
                                                  @NotNull NoiseSpec spec,
                                                  float baseFrequency)
    {
        return NoiseCacheHandler.getNoise(
                tw,
                spec.cacheEntry(),
                world -> {
                    FastNoise n = new FastNoise((int) (world.getSeed() * spec.seedMultiplier() + spec.seedOffset()));
                    n.SetNoiseType(FastNoise.NoiseType.SimplexFractal);
                    n.SetFrequency(baseFrequency * spec.frequencyMultiplier());
                    n.SetFractalOctaves(spec.fractalOctaves());
                    return n;
                }
        );
    }

    private void verifyCanBeat(@NotNull NoiseSet noiseSet,
                               @NotNull DensitySampleContext context,
                               float minRelevantLocalScore,
                               boolean optimizedCanBeat)
    {
        if (!shouldVerify(context)) {
            return;
        }

        boolean referenceCanBeat = sampleReferenceLocalScore(noiseSet, context) >= minRelevantLocalScore;
        // canBeatLocalScore is an admissible upper-bound gate, not an exact classifier.
        // Extra "true" results only cost performance; only false negatives can break geometry.
        if (!optimizedCanBeat && referenceCanBeat) {
            reportVerificationMismatch(
                    "canBeatLocalScore",
                    context,
                    minRelevantLocalScore,
                    optimizedCanBeat ? 1f : 0f,
                    referenceCanBeat ? 1f : 0f
            );
            return;
        }
        recordVerificationSuccess();
    }

    private void verifyExactScore(@NotNull NoiseSet noiseSet,
                                  @NotNull DensitySampleContext context,
                                  float optimizedLocalScore)
    {
        if (!shouldVerify(context)) {
            return;
        }

        float referenceLocalScore = sampleReferenceLocalScore(noiseSet, context);
        if (Float.floatToIntBits(optimizedLocalScore) != Float.floatToIntBits(referenceLocalScore)) {
            reportVerificationMismatch(
                    "sampleLocalScore",
                    context,
                    Float.POSITIVE_INFINITY,
                    optimizedLocalScore,
                    referenceLocalScore
            );
            return;
        }
        recordVerificationSuccess();
    }

    private void verifyThresholdedSample(@NotNull NoiseSet noiseSet,
                                         @NotNull DensitySampleContext context,
                                         float minRelevantLocalScore,
                                         float optimizedLocalScore)
    {
        if (!shouldVerify(context)) {
            return;
        }

        float referenceLocalScore = sampleReferenceLocalScore(noiseSet, context);
        boolean optimizedCrossesThreshold = optimizedLocalScore >= minRelevantLocalScore;
        boolean referenceCrossesThreshold = referenceLocalScore >= minRelevantLocalScore;
        if (optimizedCrossesThreshold != referenceCrossesThreshold) {
            if (reportVerificationMismatch(
                    "sampleLocalScore(thresholded)",
                    context,
                    minRelevantLocalScore,
                    optimizedLocalScore,
                    referenceLocalScore
            )) {
                logThresholdedMismatchDetails(
                        noiseSet,
                        context,
                        minRelevantLocalScore,
                        optimizedLocalScore,
                        referenceLocalScore
                );
            }
            return;
        }

        if (!optimizedCrossesThreshold
            && Float.floatToIntBits(optimizedLocalScore) != Float.floatToIntBits(referenceLocalScore))
        {
            reportVerificationMismatch(
                    "sampleLocalScore(thresholded-exact)",
                    context,
                    minRelevantLocalScore,
                    optimizedLocalScore,
                    referenceLocalScore
            );
            return;
        }
        recordVerificationSuccess();
    }

    private boolean shouldVerify(@NotNull DensitySampleContext context) {
        if (!TConfig.c.DEVSTUFF_CAVE_V3_VERIFY_OPTIMIZER) {
            return false;
        }

        int sampleRate = Math.max(1, TConfig.c.DEVSTUFF_CAVE_V3_VERIFY_OPTIMIZER_SAMPLE_RATE);
        int hash = (31 * context.rawX) ^ (131 * context.y) ^ (8191 * context.rawZ);
        return Math.floorMod(hash, sampleRate) == 0;
    }

    private boolean reportVerificationMismatch(@NotNull String stage,
                                               @NotNull DensitySampleContext context,
                                               float minRelevantLocalScore,
                                               float optimizedValue,
                                               float referenceValue)
    {
        int mismatchNumber = VERIFIER_MISMATCH_LOGS.incrementAndGet();
        if (mismatchNumber > MAX_VERIFIER_MISMATCH_LOGS) {
            return false;
        }

        TerraformGeneratorPlugin.logger.stdout(
                "&c[Cave V3 verifier] "
                + stage
                + " mismatch at "
                + context.rawX
                + ","
                + context.y
                + ","
                + context.rawZ
                + " minRelevant="
                + minRelevantLocalScore
                + " optimized="
                + optimizedValue
                + " reference="
                + referenceValue
                + " mismatch#="
                + mismatchNumber
        );
        return true;
    }

    private void logThresholdedMismatchDetails(@NotNull NoiseSet noiseSet,
                                               @NotNull DensitySampleContext context,
                                               float minRelevantLocalScore,
                                               float optimizedLocalScore,
                                               float referenceLocalScore)
    {
        ensureTunnelProbe(noiseSet, context);
        float requiredSmoothedCore = getRequiredSmoothedTunnelCore(context, minRelevantLocalScore);
        TerraformGeneratorPlugin.logger.stdout(
                "[Cave V3 verifier] trace baseSurface="
                + context.baseSurfaceHeight
                + " depthBelowSurface="
                + context.depthBelowSurface
                + " requiredSmoothedCore="
                + requiredSmoothedCore
                + " chamber="
                + context.chamber
                + " chamberLink="
                + context.tunnelChamberLink
                + " continuity="
                + context.tunnelContinuity
                + " junction="
                + context.tunnelJunction
                + " widthBoost="
                + context.tunnelWidthBoost
        );

        float tracedOptimizedContribution = 0f;
        float tracedReferenceContribution = 0f;
        ClearanceShape[] clearances = params.clearances();
        for (int clearanceIndex = 0; clearanceIndex < clearances.length; clearanceIndex++) {
            ClearanceShape clearance = clearances[clearanceIndex];
            float widthBoost = context.tunnelWidthBoost * clearance.widthBoostScale();
            float referenceStart = tracedReferenceContribution;
            float optimizedStart = tracedOptimizedContribution;

            float primaryReferenceContribution = computeReferenceBranchContribution(
                    noiseSet,
                    context,
                    clearance.primaryBranch(),
                    clearance.clearanceWeight(),
                    widthBoost,
                    clearance.yOffset()
            );
            BranchTrace primaryOptimizedTrace = traceOptimizedBranchContribution(
                    noiseSet,
                    context,
                    clearance.primaryBranch(),
                    clearance.clearanceWeight(),
                    tracedOptimizedContribution,
                    getTunnelBranchSlot(clearanceIndex, 0),
                    widthBoost,
                    clearance.yOffset()
            );
            tracedOptimizedContribution = primaryOptimizedTrace.resultContribution();

            float secondaryReferenceContribution = computeReferenceBranchContribution(
                    noiseSet,
                    context,
                    clearance.secondaryBranch(),
                    clearance.clearanceWeight(),
                    widthBoost,
                    clearance.yOffset()
            );
            BranchTrace secondaryOptimizedTrace = traceOptimizedBranchContribution(
                    noiseSet,
                    context,
                    clearance.secondaryBranch(),
                    clearance.clearanceWeight(),
                    tracedOptimizedContribution,
                    getTunnelBranchSlot(clearanceIndex, 1),
                    widthBoost,
                    clearance.yOffset()
            );
            tracedOptimizedContribution = secondaryOptimizedTrace.resultContribution();

            tracedReferenceContribution = Math.max(
                    referenceStart,
                    Math.max(primaryReferenceContribution, secondaryReferenceContribution)
            );

            boolean thresholdCheckReached = Float.isFinite(requiredSmoothedCore)
                                            && smoothTunnelCore(tracedOptimizedContribution) >= requiredSmoothedCore;
            String thresholdCheckSummary = "";
            if (thresholdCheckReached) {
                thresholdCheckSummary = " thresholdCheck="
                                        + getTunnelLocalScoreForCore(context, tracedOptimizedContribution);
            }

            TerraformGeneratorPlugin.logger.stdout(
                    "[Cave V3 verifier]   clearance#"
                    + clearanceIndex
                    + " yOffset="
                    + clearance.yOffset()
                    + " weight="
                    + clearance.clearanceWeight()
                    + " widthBoost="
                    + widthBoost
                    + " refStart="
                    + referenceStart
                    + " refEnd="
                    + tracedReferenceContribution
                    + " optStart="
                    + optimizedStart
                    + " optEnd="
                    + tracedOptimizedContribution
                    + thresholdCheckSummary
            );
            logThresholdedBranchTrace("primary", primaryReferenceContribution, primaryOptimizedTrace);
            logThresholdedBranchTrace("secondary", secondaryReferenceContribution, secondaryOptimizedTrace);
        }

        float tracedOptimizedLocalScore = getTunnelLocalScoreForCore(context, tracedOptimizedContribution);
        float tracedReferenceLocalScore = getTunnelLocalScoreForCore(context, tracedReferenceContribution);
        TerraformGeneratorPlugin.logger.stdout(
                "[Cave V3 verifier]   tracedOptimized="
                + tracedOptimizedLocalScore
                + " actualOptimized="
                + optimizedLocalScore
                + " tracedReference="
                + tracedReferenceLocalScore
                + " actualReference="
                + referenceLocalScore
        );
    }

    private void logThresholdedBranchTrace(@NotNull String branchName,
                                           float referenceContribution,
                                           @NotNull BranchTrace optimizedTrace)
    {
        TerraformGeneratorPlugin.logger.stdout(
                "[Cave V3 verifier]     "
                + branchName
                + " ref="
                + referenceContribution
                + " opt="
                + optimizedTrace.branchContribution()
                + " result="
                + optimizedTrace.resultContribution()
                + " firstAxis="
                + (optimizedTrace.usedAxisAFirst() ? "A" : "B")
                + " prunedBeforeAxis="
                + optimizedTrace.prunedBeforeAxis()
                + " prunedAfterFirstAxis="
                + optimizedTrace.prunedAfterFirstAxis()
                + " max="
                + optimizedTrace.maxBranchContribution()
                + " upper="
                + optimizedTrace.branchUpperBound()
                + " widthA="
                + optimizedTrace.widthA()
                + " widthB="
                + optimizedTrace.widthB()
                + " axisA="
                + optimizedTrace.axisA()
                + " axisB="
                + optimizedTrace.axisB()
        );
    }

    private float computeReferenceBranchContribution(@NotNull NoiseSet noiseSet,
                                                     @NotNull DensitySampleContext context,
                                                     @NotNull BranchShape branch,
                                                     float clearanceWeight,
                                                     float widthBoost,
                                                     float clearanceYOffset)
    {
        float warpedX = context.warpedX + branch.translateX();
        float warpedY = context.warpedY + clearanceYOffset + branch.translateY();
        float warpedZ = context.warpedZ + branch.translateZ();
        float widthA = branch.widthABase() + (widthBoost * branch.widthABoostScale());
        float widthB = branch.widthBBase() + (widthBoost * branch.widthBBoostScale());
        float axisA = noiseSet.tunnelNoiseA().GetNoise(
                warpedX * branch.stretchAX(),
                warpedY * branch.stretchAY(),
                warpedZ * branch.stretchAX()
        );
        float axisB = noiseSet.tunnelNoiseB().GetNoise(
                (warpedX * branch.stretchBX()) + branch.offsetX(),
                (warpedY * branch.stretchBY()) + branch.offsetY(),
                (warpedZ * branch.stretchBX()) + branch.offsetZ()
        );
        return clearanceWeight * branch.branchWeight() * getIntersectionMask(axisA, axisB, widthA, widthB);
    }

    private @NotNull BranchTrace traceOptimizedBranchContribution(@NotNull NoiseSet noiseSet,
                                                                  @NotNull DensitySampleContext context,
                                                                  @NotNull BranchShape branch,
                                                                  float clearanceWeight,
                                                                  float currentBestContribution,
                                                                  int branchSlot,
                                                                  float widthBoost,
                                                                  float clearanceYOffset)
    {
        float maxBranchContribution = clearanceWeight * branch.branchWeight();
        if (maxBranchContribution <= currentBestContribution) {
            return new BranchTrace(
                    false,
                    true,
                    false,
                    maxBranchContribution,
                    Float.NaN,
                    Float.NaN,
                    Float.NaN,
                    Float.NaN,
                    Float.NaN,
                    Float.NaN,
                    Float.NaN,
                    currentBestContribution
            );
        }

        float warpedX = context.warpedX + branch.translateX();
        float warpedY = context.warpedY + clearanceYOffset + branch.translateY();
        float warpedZ = context.warpedZ + branch.translateZ();
        float widthA = branch.widthABase() + (widthBoost * branch.widthABoostScale());
        float widthB = branch.widthBBase() + (widthBoost * branch.widthBBoostScale());

        if (widthA <= widthB) {
            float axisA = getTunnelBranchProbeAxis(noiseSet, context, branch, warpedX, warpedY, warpedZ, widthA, widthB, branchSlot);
            float branchUpperBound = maxBranchContribution * getSingleAxisMaskUpperBound(axisA, widthA);
            if (branchUpperBound <= currentBestContribution) {
                return new BranchTrace(
                        true,
                        false,
                        true,
                        maxBranchContribution,
                        widthA,
                        widthB,
                        axisA,
                        branchUpperBound,
                        axisA,
                        Float.NaN,
                        Float.NaN,
                        currentBestContribution
                );
            }

            float axisB = noiseSet.tunnelNoiseB().GetNoise(
                    (warpedX * branch.stretchBX()) + branch.offsetX(),
                    (warpedY * branch.stretchBY()) + branch.offsetY(),
                    (warpedZ * branch.stretchBX()) + branch.offsetZ()
            );
            float branchContribution = maxBranchContribution * getIntersectionMask(axisA, axisB, widthA, widthB);
            return new BranchTrace(
                    true,
                    false,
                    false,
                    maxBranchContribution,
                    widthA,
                    widthB,
                    axisA,
                    branchUpperBound,
                    axisA,
                    axisB,
                    branchContribution,
                    Math.max(currentBestContribution, branchContribution)
            );
        }

        float axisB = getTunnelBranchProbeAxis(noiseSet, context, branch, warpedX, warpedY, warpedZ, widthA, widthB, branchSlot);
        float branchUpperBound = maxBranchContribution * getSingleAxisMaskUpperBound(axisB, widthB);
        if (branchUpperBound <= currentBestContribution) {
            return new BranchTrace(
                    false,
                    false,
                    true,
                    maxBranchContribution,
                    widthA,
                    widthB,
                    axisB,
                    branchUpperBound,
                    Float.NaN,
                    axisB,
                    Float.NaN,
                    currentBestContribution
            );
        }

        float axisA = noiseSet.tunnelNoiseA().GetNoise(
                warpedX * branch.stretchAX(),
                warpedY * branch.stretchAY(),
                warpedZ * branch.stretchAX()
        );
        float branchContribution = maxBranchContribution * getIntersectionMask(axisA, axisB, widthA, widthB);
        return new BranchTrace(
                false,
                false,
                false,
                maxBranchContribution,
                widthA,
                widthB,
                axisB,
                branchUpperBound,
                axisA,
                axisB,
                branchContribution,
                Math.max(currentBestContribution, branchContribution)
        );
    }

    private void recordVerificationSuccess() {
        int matchesSinceLog = VERIFIER_MATCHES_SINCE_LOG.incrementAndGet();
        long now = System.currentTimeMillis();
        long lastLog = LAST_VERIFIER_SUCCESS_LOG_MILLIS.get();
        if ((now - lastLog) < VERIFIER_SUCCESS_LOG_INTERVAL_MILLIS) {
            return;
        }
        if (!LAST_VERIFIER_SUCCESS_LOG_MILLIS.compareAndSet(lastLog, now)) {
            return;
        }

        int drainedMatches = VERIFIER_MATCHES_SINCE_LOG.getAndSet(0);
        if (drainedMatches <= 0) {
            drainedMatches = matchesSinceLog;
        }

        TerraformGeneratorPlugin.logger.stdout(
                "[Cave V3 verifier] matches confirmed in last 5s: " + drainedMatches
        );
    }

    private float sampleExactTunnelLocalScore(@NotNull NoiseSet noiseSet,
                                              @NotNull DensitySampleContext context,
                                              float minRelevantLocalScore)
    {
        ensureTunnelProbe(noiseSet, context);
        float requiredStableSmoothedCore = Float.isFinite(minRelevantLocalScore)
                                           ? computeRequiredSmoothedTunnelCore(
                                                   context,
                                                   DensityFieldScore.minMonotonicBaseDensityForLocalScore(minRelevantLocalScore)
                                           )
                                           : Float.POSITIVE_INFINITY;

        float tunnelCore = 0f;
        int clearancesEntered = 0;
        ClearanceShape[] clearances = params.clearances();
        for (int orderIndex = 0; orderIndex < clearanceOrder.length; orderIndex++) {
            int clearanceIndex = clearanceOrder[orderIndex];
            ClearanceShape clearance = clearances[clearanceIndex];
            boolean clearanceEntered = canClearanceImprove(clearance.clearanceWeight(), tunnelCore);
            float updatedTunnelCore = sampleTunnelClearanceContribution(noiseSet, context, tunnelCore, clearanceIndex, clearance);
            if (clearanceEntered) {
                clearancesEntered++;
            }
            tunnelCore = updatedTunnelCore;

            float localScore = 0f;
            boolean localScoreComputed = false;
            if (Float.isFinite(requiredStableSmoothedCore)
                && smoothTunnelCore(tunnelCore) >= requiredStableSmoothedCore)
            {
                localScore = getTunnelLocalScoreForCore(context, tunnelCore);
                localScoreComputed = true;
                if (localScore >= minRelevantLocalScore) {
                    // DensityFieldScore has a soft band just below the carve threshold. Scores in that
                    // band can temporarily exceed the threshold and then dip when later clearances push
                    // density onto the post-threshold branch, so only the monotonic branch is safe to
                    // early-return from.
                    CaveV3Profiler.recordEvent(FULL_THRESHOLD_HIT_PROFILER_KEY);
                    return localScore;
                }
                CaveV3Profiler.recordEvent(FULL_THRESHOLD_FALSE_POSITIVE_PROFILER_KEY);
            }

            if ((orderIndex + 1) < clearanceOrder.length
                && tunnelCore >= remainingClearanceMaxContributions[orderIndex + 1])
            {
                if (!localScoreComputed) {
                    localScore = getTunnelLocalScoreForCore(context, tunnelCore);
                }
                CaveV3Profiler.recordEvent(FULL_REMAINING_MAX_STOP_PROFILER_KEY);
                return localScore;
            }
        }

        if (clearancesEntered == clearances.length) {
            CaveV3Profiler.recordEvent(FULL_STACK_PROFILER_KEY);
        }
        return getTunnelLocalScoreForCore(context, tunnelCore);
    }

    private float sampleReferenceLocalScore(@NotNull NoiseSet noiseSet, @NotNull DensitySampleContext context) {
        ensureTunnelProbe(noiseSet, context);

        float tunnelCore = 0f;
        ClearanceShape[] clearances = params.clearances();
        for (int clearanceIndex = 0; clearanceIndex < clearances.length; clearanceIndex++) {
            tunnelCore = sampleReferenceClearanceContribution(noiseSet, context, tunnelCore, clearances[clearanceIndex]);
        }
        return getTunnelLocalScoreForCore(context, tunnelCore);
    }

    private float sampleReferenceClearanceContribution(@NotNull NoiseSet noiseSet,
                                                       @NotNull DensitySampleContext context,
                                                       float currentBestContribution,
                                                       @NotNull ClearanceShape clearance)
    {
        float widthBoost = context.tunnelWidthBoost * clearance.widthBoostScale();
        float bestContribution = sampleReferenceBranchContribution(
                noiseSet,
                context,
                clearance.primaryBranch(),
                clearance.clearanceWeight(),
                currentBestContribution,
                widthBoost,
                clearance.yOffset()
        );
        return sampleReferenceBranchContribution(
                noiseSet,
                context,
                clearance.secondaryBranch(),
                clearance.clearanceWeight(),
                bestContribution,
                widthBoost,
                clearance.yOffset()
        );
    }

    private float sampleReferenceBranchContribution(@NotNull NoiseSet noiseSet,
                                                    @NotNull DensitySampleContext context,
                                                    @NotNull BranchShape branch,
                                                    float clearanceWeight,
                                                    float currentBestContribution,
                                                    float widthBoost,
                                                    float clearanceYOffset)
    {
        float warpedX = context.warpedX + branch.translateX();
        float warpedY = context.warpedY + clearanceYOffset + branch.translateY();
        float warpedZ = context.warpedZ + branch.translateZ();
        float widthA = branch.widthABase() + (widthBoost * branch.widthABoostScale());
        float widthB = branch.widthBBase() + (widthBoost * branch.widthBBoostScale());
        float axisA = noiseSet.tunnelNoiseA().GetNoise(
                warpedX * branch.stretchAX(),
                warpedY * branch.stretchAY(),
                warpedZ * branch.stretchAX()
        );
        float axisB = noiseSet.tunnelNoiseB().GetNoise(
                (warpedX * branch.stretchBX()) + branch.offsetX(),
                (warpedY * branch.stretchBY()) + branch.offsetY(),
                (warpedZ * branch.stretchBX()) + branch.offsetZ()
        );
        float branchContribution = clearanceWeight * branch.branchWeight() * getIntersectionMask(axisA, axisB, widthA, widthB);
        return Math.max(currentBestContribution, branchContribution);
    }

    private float getTunnelUpperBoundLocalScore(@NotNull NoiseSet noiseSet, @NotNull DensitySampleContext context) {
        ensureTunnelProbe(noiseSet, context);
        float tunnelCoreUpperBound = getTunnelCoreUpperBound(noiseSet, context);
        return getTunnelLocalScoreForCore(context, tunnelCoreUpperBound);
    }

    private boolean canTunnelBeatLocalScore(@NotNull NoiseSet noiseSet,
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

        float continuity = ensureTunnelContinuity(noiseSet.continuityNoise(), context);
        if (requiredDensity > getMaxPossibleTunnelDensityWithoutJunction(context, continuity)) {
            CaveV3Profiler.recordEvent(PROBE_CONTINUITY_PRUNED_PROFILER_KEY);
            return false;
        }

        ensureTunnelProbe(noiseSet, context);
        float requiredSmoothedCore = getRequiredSmoothedTunnelCore(context, minRelevantLocalScore);
        if (!Float.isFinite(requiredSmoothedCore)) {
            CaveV3Profiler.recordEvent(PROBE_SUPPORT_PRUNED_PROFILER_KEY);
            return false;
        }
        if (requiredSmoothedCore <= 0f) {
            return true;
        }
        return canTunnelCoreUpperBoundBeatLocalScore(noiseSet, context, requiredSmoothedCore);
    }

    private boolean canTunnelCoreUpperBoundBeatLocalScore(@NotNull NoiseSet noiseSet,
                                                          @NotNull DensitySampleContext context,
                                                          float requiredSmoothedCore)
    {
        if (context.tunnelGeometryUpperBoundComputed) {
            return smoothTunnelCore(context.tunnelGeometryUpperBound) >= requiredSmoothedCore;
        }

        try (CaveV3Profiler.Scope ignored = CaveV3Profiler.start(PROBE_GEOMETRY_PROFILER_KEY)) {
            float tunnelCoreUpperBound = 0f;
            ClearanceShape[] clearances = params.clearances();
            for (int orderIndex = 0; orderIndex < clearanceOrder.length; orderIndex++) {
                int clearanceIndex = clearanceOrder[orderIndex];
                float updatedUpperBound = sampleTunnelClearanceUpperBound(
                        noiseSet,
                        context,
                        tunnelCoreUpperBound,
                        clearanceIndex,
                        clearances[clearanceIndex]
                );
                tunnelCoreUpperBound = updatedUpperBound;
                if (smoothTunnelCore(tunnelCoreUpperBound) >= requiredSmoothedCore) {
                    CaveV3Profiler.recordEvent(PROBE_GEOMETRY_THRESHOLD_HIT_PROFILER_KEY);
                    return true;
                }
                if ((orderIndex + 1) < clearanceOrder.length
                    && tunnelCoreUpperBound >= remainingClearanceMaxContributions[orderIndex + 1])
                {
                    context.cacheTunnelGeometryUpperBound(tunnelCoreUpperBound);
                    CaveV3Profiler.recordEvent(PROBE_GEOMETRY_REMAINING_MAX_STOP_PROFILER_KEY);
                    return false;
                }
            }
            context.cacheTunnelGeometryUpperBound(tunnelCoreUpperBound);
            CaveV3Profiler.recordEvent(PROBE_GEOMETRY_FULL_BOUND_PROFILER_KEY);
            return smoothTunnelCore(tunnelCoreUpperBound) >= requiredSmoothedCore;
        }
    }

    private float getTunnelCoreUpperBound(@NotNull NoiseSet noiseSet, @NotNull DensitySampleContext context) {
        if (context.tunnelGeometryUpperBoundComputed) {
            return context.tunnelGeometryUpperBound;
        }

        float tunnelCoreUpperBound = 0f;
        try (CaveV3Profiler.Scope ignored = CaveV3Profiler.start(PROBE_GEOMETRY_PROFILER_KEY)) {
            ClearanceShape[] clearances = params.clearances();
            for (int orderIndex = 0; orderIndex < clearanceOrder.length; orderIndex++) {
                int clearanceIndex = clearanceOrder[orderIndex];
                float updatedUpperBound = sampleTunnelClearanceUpperBound(
                        noiseSet,
                        context,
                        tunnelCoreUpperBound,
                        clearanceIndex,
                        clearances[clearanceIndex]
                );
                tunnelCoreUpperBound = updatedUpperBound;
                if ((orderIndex + 1) < clearanceOrder.length
                    && tunnelCoreUpperBound >= remainingClearanceMaxContributions[orderIndex + 1])
                {
                    context.cacheTunnelGeometryUpperBound(tunnelCoreUpperBound);
                    CaveV3Profiler.recordEvent(PROBE_GEOMETRY_REMAINING_MAX_STOP_PROFILER_KEY);
                    return tunnelCoreUpperBound;
                }
            }
        }
        context.cacheTunnelGeometryUpperBound(tunnelCoreUpperBound);
        CaveV3Profiler.recordEvent(PROBE_GEOMETRY_FULL_BOUND_PROFILER_KEY);
        return tunnelCoreUpperBound;
    }

    private void ensureTunnelProbe(@NotNull NoiseSet noiseSet, @NotNull DensitySampleContext context) {
        if (context.tunnelProbeComputed) {
            return;
        }

        try (CaveV3Profiler.Scope ignored = CaveV3Profiler.start("cave-v3.sample.field.tunnel.probe")) {
            float chamberLink = getTunnelChamberLink(context);
            float continuity = ensureTunnelContinuity(noiseSet.continuityNoise(), context);

            float junction = 0.5f + (0.5f * noiseSet.junctionNoise().GetNoise(
                    context.warpedX * params.junctionHorizontalStretch(),
                    context.warpedY * params.junctionVerticalStretch(),
                    context.warpedZ * params.junctionHorizontalStretch()
            ));
            junction = DensityCarveRules.clamp01((junction - params.junctionMin()) / params.junctionRange())
                       * (params.junctionChamberBase() + (params.junctionChamberWeight() * chamberLink));

            context.cacheTunnelProbe(buildSupportProfile(context.depthBelowSurface, chamberLink, continuity, junction));
        }
    }

    private @NotNull SupportProfile buildSupportProfile(float depthBelowSurface,
                                                        float chamberLink,
                                                        float continuity,
                                                        float junction)
    {
        // Probe and exact-score paths both consume this derived profile, so changing tunnel
        // weights or density scaling later keeps the pruning math in lockstep automatically.
        float nearSurfaceFade = DensityCarveRules.clamp01(
                (depthBelowSurface - params.minFullCarveDepth()) / params.surfaceFadeDepth()
        );
        float deepBoost = DensityCarveRules.clamp01(
                (depthBelowSurface - params.deepBoostStart()) / params.deepBoostRange()
        );
        float densityScale = params.densityScaleBase() + (nearSurfaceFade * params.densityScaleNearSurfaceWeight());
        float deepDensityOffset = deepBoost * params.deepBoostDensityWeight();
        float tunnelPivotSmoothedCore = chamberLink * params.chamberLinkPivotWeight();
        float baseDensityIntercept = params.baseDensityOffset()
                                     + (chamberLink * params.chamberLinkWeight() * params.chamberLinkInterceptShare());
        float commonDensitySlope = params.tunnelWeight()
                                   + (continuity * params.continuityWeight())
                                   + (chamberLink * params.chamberLinkWeight() * params.chamberLinkSlopeShare());
        float tunnelDensityBelowPivotIntercept = (baseDensityIntercept
                                                  + (junction * params.junctionWeight() * tunnelPivotSmoothedCore))
                                                 * densityScale
                                                 + deepDensityOffset;
        float tunnelDensityBelowPivotSlope = commonDensitySlope * densityScale;
        float tunnelDensityAbovePivotIntercept = (baseDensityIntercept * densityScale) + deepDensityOffset;
        float tunnelDensityAbovePivotSlope = (commonDensitySlope + (junction * params.junctionWeight())) * densityScale;
        float tunnelWidthBoost = params.widthBoostBase()
                                 + (continuity * params.widthBoostContinuityWeight())
                                 + (chamberLink * params.widthBoostChamberWeight())
                                 + (junction * params.widthBoostJunctionWeight());
        return new SupportProfile(
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

    private float getTunnelLocalScoreForCore(@NotNull DensitySampleContext context, float tunnelCore) {
        return DensityFieldScore.toLocalScore(getTunnelDensityForSmoothedCore(context, smoothTunnelCore(tunnelCore)));
    }

    private float getMaxPossibleTunnelDensityWithoutProbe(@NotNull DensitySampleContext context) {
        float chamberLink = getTunnelChamberLink(context);
        float maxJunction = params.junctionChamberBase() + (params.junctionChamberWeight() * chamberLink);
        return getMaxPossibleTunnelDensity(context.depthBelowSurface, chamberLink, 1f, maxJunction);
    }

    private float getMaxPossibleTunnelDensityWithoutJunction(@NotNull DensitySampleContext context, float continuity) {
        float chamberLink = getTunnelChamberLink(context);
        float maxJunction = params.junctionChamberBase() + (params.junctionChamberWeight() * chamberLink);
        return getMaxPossibleTunnelDensity(context.depthBelowSurface, chamberLink, continuity, maxJunction);
    }

    private float getMaxPossibleTunnelDensity(float depthBelowSurface,
                                              float chamberLink,
                                              float continuity,
                                              float junction)
    {
        SupportProfile profile = buildSupportProfile(depthBelowSurface, chamberLink, continuity, junction);
        return profile.tunnelDensityAbovePivotIntercept() + profile.tunnelDensityAbovePivotSlope();
    }

    private float getTunnelChamberLink(@NotNull DensitySampleContext context) {
        if (context.tunnelProbeComputed) {
            return context.tunnelChamberLink;
        }
        return DensityCarveRules.clamp01((context.chamber - params.chamberLinkMin()) / params.chamberLinkRange());
    }

    private float ensureTunnelContinuity(@NotNull FastNoise continuityNoise, @NotNull DensitySampleContext context) {
        if (context.tunnelProbeComputed || context.tunnelContinuityComputed) {
            return context.tunnelContinuity;
        }

        float continuity;
        try (CaveV3Profiler.Scope ignored = CaveV3Profiler.start("cave-v3.sample.field.tunnel.probe.continuity")) {
            continuity = 0.5f + (0.5f * continuityNoise.GetNoise(
                    context.warpedX * params.continuityHorizontalStretch(),
                    context.warpedY * params.continuityVerticalStretch(),
                    context.warpedZ * params.continuityHorizontalStretch()
            ));
            continuity = DensityCarveRules.clamp01((continuity - params.continuityMin()) / params.continuityRange());
        }
        context.cacheTunnelContinuity(continuity);
        return continuity;
    }

    private float getRequiredSmoothedTunnelCore(@NotNull DensitySampleContext context, float minRelevantLocalScore) {
        if (context.hasTunnelRequiredSmoothedCore(minRelevantLocalScore)) {
            return context.getTunnelRequiredSmoothedCore();
        }

        float requiredDensity = DensityFieldScore.minBaseDensityForLocalScore(minRelevantLocalScore);
        float requiredSmoothedCore = computeRequiredSmoothedTunnelCore(context, requiredDensity);
        context.cacheTunnelRequiredSmoothedCore(minRelevantLocalScore, requiredSmoothedCore);
        return requiredSmoothedCore;
    }

    private float computeRequiredSmoothedTunnelCore(@NotNull DensitySampleContext context, float requiredDensity) {
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

    private float makeSmoothedCoreThresholdSafe(@NotNull DensitySampleContext context,
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

    private float sampleTunnelClearanceContribution(@NotNull NoiseSet noiseSet,
                                                    @NotNull DensitySampleContext context,
                                                    float currentBestContribution,
                                                    int clearanceIndex,
                                                    @NotNull ClearanceShape clearance)
    {
        if (!canClearanceImprove(clearance.clearanceWeight(), currentBestContribution)) {
            CaveV3Profiler.recordEvent(CLEARANCE_PRUNED_PROFILER_KEY);
            return currentBestContribution;
        }

        CaveV3Profiler.recordEvent(CLEARANCE_ENTERED_PROFILER_KEY);

        float widthBoost = context.tunnelWidthBoost * clearance.widthBoostScale();
        float clearanceBestContribution = sampleTunnelBranchContribution(
                noiseSet,
                context,
                clearance.primaryBranch(),
                clearance.clearanceWeight(),
                currentBestContribution,
                getTunnelBranchSlot(clearanceIndex, 0),
                widthBoost,
                clearance.yOffset()
        );
        return sampleTunnelBranchContribution(
                noiseSet,
                context,
                clearance.secondaryBranch(),
                clearance.clearanceWeight(),
                clearanceBestContribution,
                getTunnelBranchSlot(clearanceIndex, 1),
                widthBoost,
                clearance.yOffset()
        );
    }

    private float sampleTunnelBranchContribution(@NotNull NoiseSet noiseSet,
                                                 @NotNull DensitySampleContext context,
                                                 @NotNull BranchShape branch,
                                                 float clearanceWeight,
                                                 float currentBestContribution,
                                                 int branchSlot,
                                                 float widthBoost,
                                                 float clearanceYOffset)
    {
        float maxBranchContribution = clearanceWeight * branch.branchWeight();
        if (maxBranchContribution <= currentBestContribution) {
            CaveV3Profiler.recordEvent(BRANCH_PRUNED_PROFILER_KEY);
            CaveV3Profiler.recordEvent(BRANCH_PRUNED_BEFORE_AXIS_PROFILER_KEY);
            return currentBestContribution;
        }

        float warpedX = context.warpedX + branch.translateX();
        float warpedY = context.warpedY + clearanceYOffset + branch.translateY();
        float warpedZ = context.warpedZ + branch.translateZ();
        float widthA = branch.widthABase() + (widthBoost * branch.widthABoostScale());
        float widthB = branch.widthBBase() + (widthBoost * branch.widthBBoostScale());

        if (widthA <= widthB) {
            float axisA = getTunnelBranchProbeAxis(noiseSet, context, branch, warpedX, warpedY, warpedZ, widthA, widthB, branchSlot);
            float branchUpperBound = maxBranchContribution * getSingleAxisMaskUpperBound(axisA, widthA);
            if (branchUpperBound <= currentBestContribution) {
                CaveV3Profiler.recordEvent(BRANCH_PRUNED_PROFILER_KEY);
                CaveV3Profiler.recordEvent(BRANCH_PRUNED_AFTER_FIRST_AXIS_PROFILER_KEY);
                return currentBestContribution;
            }

            CaveV3Profiler.recordEvent(BRANCH_ENTERED_PROFILER_KEY);
            float axisB = noiseSet.tunnelNoiseB().GetNoise(
                    (warpedX * branch.stretchBX()) + branch.offsetX(),
                    (warpedY * branch.stretchBY()) + branch.offsetY(),
                    (warpedZ * branch.stretchBX()) + branch.offsetZ()
            );
            return Math.max(
                    currentBestContribution,
                    maxBranchContribution * getIntersectionMask(axisA, axisB, widthA, widthB)
            );
        }

        float axisB = getTunnelBranchProbeAxis(noiseSet, context, branch, warpedX, warpedY, warpedZ, widthA, widthB, branchSlot);
        float branchUpperBound = maxBranchContribution * getSingleAxisMaskUpperBound(axisB, widthB);
        if (branchUpperBound <= currentBestContribution) {
            CaveV3Profiler.recordEvent(BRANCH_PRUNED_PROFILER_KEY);
            CaveV3Profiler.recordEvent(BRANCH_PRUNED_AFTER_FIRST_AXIS_PROFILER_KEY);
            return currentBestContribution;
        }

        CaveV3Profiler.recordEvent(BRANCH_ENTERED_PROFILER_KEY);
        float axisA = noiseSet.tunnelNoiseA().GetNoise(
                warpedX * branch.stretchAX(),
                warpedY * branch.stretchAY(),
                warpedZ * branch.stretchAX()
        );
        return Math.max(
                currentBestContribution,
                maxBranchContribution * getIntersectionMask(axisA, axisB, widthA, widthB)
        );
    }

    private float sampleTunnelClearanceUpperBound(@NotNull NoiseSet noiseSet,
                                                  @NotNull DensitySampleContext context,
                                                  float currentBestUpperBound,
                                                  int clearanceIndex,
                                                  @NotNull ClearanceShape clearance)
    {
        if (!canClearanceImprove(clearance.clearanceWeight(), currentBestUpperBound)) {
            return currentBestUpperBound;
        }

        float widthBoost = context.tunnelWidthBoost * clearance.widthBoostScale();
        float clearanceUpperBound = sampleTunnelBranchUpperBound(
                noiseSet,
                context,
                clearance.primaryBranch(),
                clearance.clearanceWeight(),
                currentBestUpperBound,
                getTunnelBranchSlot(clearanceIndex, 0),
                widthBoost,
                clearance.yOffset()
        );
        return sampleTunnelBranchUpperBound(
                noiseSet,
                context,
                clearance.secondaryBranch(),
                clearance.clearanceWeight(),
                clearanceUpperBound,
                getTunnelBranchSlot(clearanceIndex, 1),
                widthBoost,
                clearance.yOffset()
        );
    }

    private float sampleTunnelBranchUpperBound(@NotNull NoiseSet noiseSet,
                                               @NotNull DensitySampleContext context,
                                               @NotNull BranchShape branch,
                                               float clearanceWeight,
                                               float currentBestUpperBound,
                                               int branchSlot,
                                               float widthBoost,
                                               float clearanceYOffset)
    {
        float maxBranchContribution = clearanceWeight * branch.branchWeight();
        if (maxBranchContribution <= currentBestUpperBound) {
            return currentBestUpperBound;
        }

        float warpedX = context.warpedX + branch.translateX();
        float warpedY = context.warpedY + clearanceYOffset + branch.translateY();
        float warpedZ = context.warpedZ + branch.translateZ();
        float widthA = branch.widthABase() + (widthBoost * branch.widthABoostScale());
        float widthB = branch.widthBBase() + (widthBoost * branch.widthBBoostScale());
        float firstAxis = getTunnelBranchProbeAxis(noiseSet, context, branch, warpedX, warpedY, warpedZ, widthA, widthB, branchSlot);
        float firstWidth = widthA <= widthB ? widthA : widthB;
        return Math.max(currentBestUpperBound, maxBranchContribution * getSingleAxisMaskUpperBound(firstAxis, firstWidth));
    }

    private static boolean canClearanceImprove(float clearanceWeight, float currentBestContribution) {
        return clearanceWeight > currentBestContribution;
    }

    private static int[] createClearanceOrder(int clearanceCount) {
        int[] order = new int[clearanceCount];
        for (int i = 0; i < clearanceCount; i++) {
            order[i] = i;
        }
        if (clearanceCount >= 5) {
            order[3] = 4;
            order[4] = 3;
        }
        return order;
    }

    private static float[] computeRemainingClearanceMaxContributions(@NotNull ClearanceShape[] clearances,
                                                                     int @NotNull [] clearanceOrder)
    {
        float[] suffixMaxContributions = new float[clearanceOrder.length + 1];
        for (int i = clearanceOrder.length - 1; i >= 0; i--) {
            suffixMaxContributions[i] = Math.max(
                    getMaxPossibleClearanceContribution(clearances[clearanceOrder[i]]),
                    suffixMaxContributions[i + 1]
            );
        }
        return suffixMaxContributions;
    }

    private static float getMaxPossibleClearanceContribution(@NotNull ClearanceShape clearance) {
        float maxBranchWeight = Math.max(
                clearance.primaryBranch().branchWeight(),
                clearance.secondaryBranch().branchWeight()
        );
        return clearance.clearanceWeight() * maxBranchWeight;
    }

    private static float getTunnelBranchProbeAxis(@NotNull NoiseSet noiseSet,
                                                  @NotNull DensitySampleContext context,
                                                  @NotNull BranchShape branch,
                                                  float warpedX,
                                                  float warpedY,
                                                  float warpedZ,
                                                  float widthA,
                                                  float widthB,
                                                  int branchSlot)
    {
        if (context.hasTunnelBranchProbeAxis(branchSlot)) {
            return context.getTunnelBranchProbeAxis(branchSlot);
        }

        float firstAxis;
        if (widthA <= widthB) {
            firstAxis = noiseSet.tunnelNoiseA().GetNoise(
                    warpedX * branch.stretchAX(),
                    warpedY * branch.stretchAY(),
                    warpedZ * branch.stretchAX()
            );
        }
        else {
            firstAxis = noiseSet.tunnelNoiseB().GetNoise(
                    (warpedX * branch.stretchBX()) + branch.offsetX(),
                    (warpedY * branch.stretchBY()) + branch.offsetY(),
                    (warpedZ * branch.stretchBX()) + branch.offsetZ()
            );
        }

        context.cacheTunnelBranchProbeAxis(branchSlot, firstAxis);
        return firstAxis;
    }

    private static int getTunnelBranchSlot(int clearanceIndex, int branchIndex) {
        return (clearanceIndex * 2) + branchIndex;
    }

    private static float getSingleAxisMaskUpperBound(float axis, float width) {
        float safeWidth = Math.max(0.0001f, width);
        float radialLowerBound = Math.abs(axis) / safeWidth;
        if (radialLowerBound >= 1f) {
            return 0f;
        }
        float mask = 1f - radialLowerBound;
        return mask * mask * (3f - (2f * mask));
    }

    private static float getIntersectionMask(float axisA, float axisB, float widthA, float widthB) {
        float safeWidthA = Math.max(0.0001f, widthA);
        float safeWidthB = Math.max(0.0001f, widthB);
        float normalizedA = axisA / safeWidthA;
        float normalizedB = axisB / safeWidthB;
        float radialDistanceSquared = (normalizedA * normalizedA) + (normalizedB * normalizedB);
        if (radialDistanceSquared >= 1f) {
            return 0f;
        }
        float radialDistance = (float) Math.sqrt(radialDistanceSquared);
        float mask = 1f - radialDistance;
        return mask * mask * (3f - (2f * mask));
    }

    record Params(@NotNull NoiseSpec axisNoiseA,
                  @NotNull NoiseSpec axisNoiseB,
                  @NotNull NoiseSpec continuityNoise,
                  @NotNull NoiseSpec junctionNoise,
                  float minFullCarveDepth,
                  float surfaceFadeDepth,
                  float deepBoostStart,
                  float deepBoostRange,
                  float baseDensityOffset,
                  float tunnelWeight,
                  float chamberLinkWeight,
                  float continuityWeight,
                  float junctionWeight,
                  float densityScaleBase,
                  float densityScaleNearSurfaceWeight,
                  float deepBoostDensityWeight,
                  float chamberLinkMin,
                  float chamberLinkRange,
                  float chamberLinkPivotWeight,
                  float chamberLinkInterceptShare,
                  float chamberLinkSlopeShare,
                  float continuityHorizontalStretch,
                  float continuityVerticalStretch,
                  float continuityMin,
                  float continuityRange,
                  float junctionHorizontalStretch,
                  float junctionVerticalStretch,
                  float junctionMin,
                  float junctionRange,
                  float junctionChamberBase,
                  float junctionChamberWeight,
                  float widthBoostBase,
                  float widthBoostContinuityWeight,
                  float widthBoostChamberWeight,
                  float widthBoostJunctionWeight,
                  @NotNull ClearanceShape[] clearances) {
    }

    record NoiseSpec(@NotNull NoiseCacheHandler.NoiseCacheEntry cacheEntry,
                     long seedMultiplier,
                     int seedOffset,
                     float frequencyMultiplier,
                     int fractalOctaves) {
    }

    record BranchShape(float translateX,
                       float translateY,
                       float translateZ,
                       float stretchAX,
                       float stretchAY,
                       float stretchBX,
                       float stretchBY,
                       float widthABase,
                       float widthABoostScale,
                       float widthBBase,
                       float widthBBoostScale,
                       float offsetX,
                       float offsetY,
                       float offsetZ,
                       float branchWeight) {
    }

    record ClearanceShape(float yOffset,
                          float clearanceWeight,
                          float widthBoostScale,
                          @NotNull BranchShape primaryBranch,
                          @NotNull BranchShape secondaryBranch) {
    }

    record SupportProfile(float chamberLink,
                          float continuity,
                          float junction,
                          float tunnelWidthBoost,
                          float tunnelDensityBelowPivotIntercept,
                          float tunnelDensityBelowPivotSlope,
                          float tunnelDensityAbovePivotIntercept,
                          float tunnelDensityAbovePivotSlope,
                          float tunnelPivotSmoothedCore) {
    }

    private record BranchTrace(boolean usedAxisAFirst,
                               boolean prunedBeforeAxis,
                               boolean prunedAfterFirstAxis,
                               float maxBranchContribution,
                               float widthA,
                               float widthB,
                               float firstAxis,
                               float branchUpperBound,
                               float axisA,
                               float axisB,
                               float branchContribution,
                               float resultContribution) {
    }

    private record NoiseSet(@NotNull FastNoise tunnelNoiseA,
                            @NotNull FastNoise tunnelNoiseB,
                            @NotNull FastNoise continuityNoise,
                            @NotNull FastNoise junctionNoise) {
    }
}
