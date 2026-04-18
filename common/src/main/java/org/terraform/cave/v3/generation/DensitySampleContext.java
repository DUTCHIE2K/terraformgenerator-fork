package org.terraform.cave.v3.generation;

import org.jetbrains.annotations.NotNull;
import org.terraform.utils.noise.FastNoise;

import java.util.Arrays;

public final class DensitySampleContext {
    private static final int TUNNEL_BRANCH_SLOT_COUNT = 10;

    private final @NotNull FastNoise warpNoise;
    private final @NotNull FastNoise chamberNoise;
    private final @NotNull CheeseFieldModel.DomainWarp cheeseDomainWarp;

    int rawX;
    int y;
    int rawZ;
    double baseSurfaceHeight;
    float warpedX;
    float warpedY;
    float warpedZ;
    float chamber;
    float depthBelowSurface;
    boolean tunnelProbeComputed;
    boolean tunnelContinuityComputed;
    float tunnelChamberLink;
    float tunnelContinuity;
    float tunnelJunction;
    float tunnelWidthBoost;
    float tunnelDensityBelowPivotIntercept;
    float tunnelDensityBelowPivotSlope;
    float tunnelDensityAbovePivotIntercept;
    float tunnelDensityAbovePivotSlope;
    float tunnelPivotSmoothedCore;
    boolean tunnelGeometryUpperBoundComputed;
    float tunnelGeometryUpperBound;
    boolean tunnelRequiredSmoothedCoreComputed;
    int tunnelRequiredSmoothedCoreScoreBits;
    float tunnelRequiredSmoothedCore;
    private final float[] tunnelBranchProbeAxisValues = new float[TUNNEL_BRANCH_SLOT_COUNT];
    private final int[] tunnelBranchProbeAxisEpochs = new int[TUNNEL_BRANCH_SLOT_COUNT];
    private int tunnelBranchProbeEpoch = 1;

    DensitySampleContext(@NotNull FastNoise warpNoise,
                         @NotNull FastNoise chamberNoise,
                         @NotNull CheeseFieldModel.DomainWarp cheeseDomainWarp)
    {
        this.warpNoise = warpNoise;
        this.chamberNoise = chamberNoise;
        this.cheeseDomainWarp = cheeseDomainWarp;
    }

    void prepare(int rawX, int y, int rawZ, double baseSurfaceHeight) {
        this.rawX = rawX;
        this.y = y;
        this.rawZ = rawZ;
        this.baseSurfaceHeight = baseSurfaceHeight;

        float warpSampleX = rawX * cheeseDomainWarp.horizontalWarpScale();
        float warpSampleY = y * cheeseDomainWarp.verticalWarpScale();
        float warpSampleZ = rawZ * cheeseDomainWarp.horizontalWarpScale();
        warpedX = rawX + (warpNoise.GetNoise(warpSampleX + 13.2f, warpSampleY - 7.4f, warpSampleZ + 5.1f)
                          * cheeseDomainWarp.horizontalWarpAmplitude());
        warpedY = y + (warpNoise.GetNoise(warpSampleX - 11.7f, warpSampleY + 17.6f, warpSampleZ - 9.3f)
                       * cheeseDomainWarp.verticalWarpAmplitude());
        warpedZ = rawZ + (warpNoise.GetNoise(warpSampleX + 7.8f, warpSampleY + 3.1f, warpSampleZ - 15.4f)
                          * cheeseDomainWarp.horizontalWarpAmplitude());

        chamber = 0.5f + (0.5f * chamberNoise.GetNoise(
                warpedX * cheeseDomainWarp.chamberHorizontalStretch(),
                warpedY * cheeseDomainWarp.chamberVerticalStretch(),
                warpedZ * cheeseDomainWarp.chamberHorizontalStretch()
        ));
        depthBelowSurface = (float) (baseSurfaceHeight - y);
        tunnelProbeComputed = false;
        tunnelContinuityComputed = false;
        tunnelGeometryUpperBoundComputed = false;
        tunnelRequiredSmoothedCoreComputed = false;
        advanceTunnelBranchProbeEpoch();
    }

    void cacheTunnelContinuity(float continuity) {
        this.tunnelContinuity = continuity;
        this.tunnelContinuityComputed = true;
    }

    void cacheTunnelProbe(float chamberLink,
                          float continuity,
                          float junction,
                          float tunnelWidthBoost,
                          float tunnelDensityBelowPivotIntercept,
                          float tunnelDensityBelowPivotSlope,
                          float tunnelDensityAbovePivotIntercept,
                          float tunnelDensityAbovePivotSlope,
                          float tunnelPivotSmoothedCore)
    {
        this.tunnelChamberLink = chamberLink;
        this.tunnelContinuity = continuity;
        this.tunnelJunction = junction;
        this.tunnelWidthBoost = tunnelWidthBoost;
        this.tunnelDensityBelowPivotIntercept = tunnelDensityBelowPivotIntercept;
        this.tunnelDensityBelowPivotSlope = tunnelDensityBelowPivotSlope;
        this.tunnelDensityAbovePivotIntercept = tunnelDensityAbovePivotIntercept;
        this.tunnelDensityAbovePivotSlope = tunnelDensityAbovePivotSlope;
        this.tunnelPivotSmoothedCore = tunnelPivotSmoothedCore;
        this.tunnelContinuityComputed = true;
        this.tunnelProbeComputed = true;
    }

    void cacheTunnelProbe(@NotNull TunnelFieldModel.SupportProfile supportProfile) {
        cacheTunnelProbe(
                supportProfile.chamberLink(),
                supportProfile.continuity(),
                supportProfile.junction(),
                supportProfile.tunnelWidthBoost(),
                supportProfile.tunnelDensityBelowPivotIntercept(),
                supportProfile.tunnelDensityBelowPivotSlope(),
                supportProfile.tunnelDensityAbovePivotIntercept(),
                supportProfile.tunnelDensityAbovePivotSlope(),
                supportProfile.tunnelPivotSmoothedCore()
        );
    }

    void cacheTunnelGeometryUpperBound(float tunnelGeometryUpperBound) {
        this.tunnelGeometryUpperBound = tunnelGeometryUpperBound;
        this.tunnelGeometryUpperBoundComputed = true;
    }

    boolean hasTunnelRequiredSmoothedCore(float minRelevantLocalScore) {
        return tunnelRequiredSmoothedCoreComputed
               && tunnelRequiredSmoothedCoreScoreBits == Float.floatToIntBits(minRelevantLocalScore);
    }

    float getTunnelRequiredSmoothedCore() {
        return tunnelRequiredSmoothedCore;
    }

    void cacheTunnelRequiredSmoothedCore(float minRelevantLocalScore, float requiredSmoothedCore) {
        this.tunnelRequiredSmoothedCoreScoreBits = Float.floatToIntBits(minRelevantLocalScore);
        this.tunnelRequiredSmoothedCore = requiredSmoothedCore;
        this.tunnelRequiredSmoothedCoreComputed = true;
    }

    boolean hasTunnelBranchProbeAxis(int slot) {
        return tunnelBranchProbeAxisEpochs[slot] == tunnelBranchProbeEpoch;
    }

    float getTunnelBranchProbeAxis(int slot) {
        return tunnelBranchProbeAxisValues[slot];
    }

    void cacheTunnelBranchProbeAxis(int slot, float value) {
        tunnelBranchProbeAxisValues[slot] = value;
        tunnelBranchProbeAxisEpochs[slot] = tunnelBranchProbeEpoch;
    }

    private void advanceTunnelBranchProbeEpoch() {
        if (tunnelBranchProbeEpoch == Integer.MAX_VALUE) {
            Arrays.fill(tunnelBranchProbeAxisEpochs, 0);
            tunnelBranchProbeEpoch = 1;
            return;
        }
        tunnelBranchProbeEpoch++;
    }
}
