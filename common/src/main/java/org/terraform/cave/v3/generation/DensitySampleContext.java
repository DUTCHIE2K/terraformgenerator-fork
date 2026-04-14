package org.terraform.cave.v3.generation;

import org.jetbrains.annotations.NotNull;
import org.terraform.utils.noise.FastNoise;

import java.util.Arrays;

public final class DensitySampleContext {
    private static final int TUNNEL_BRANCH_SLOT_COUNT = 10;
    private static final float WARP_SCALE = 0.7f;
    private static final float WARP_VERTICAL_SCALE = 0.7f;
    private static final float WARP_HORIZONTAL_AMPLITUDE = 7f;
    private static final float WARP_VERTICAL_AMPLITUDE = 3.25f;
    private static final float CHAMBER_HORIZONTAL_STRETCH = 0.28f;
    private static final float CHAMBER_VERTICAL_STRETCH = 0.41f;

    private final @NotNull FastNoise warpNoise;
    private final @NotNull FastNoise chamberNoise;

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
    private final boolean[] tunnelBranchProbeAxisComputed = new boolean[TUNNEL_BRANCH_SLOT_COUNT];

    DensitySampleContext(@NotNull FastNoise warpNoise, @NotNull FastNoise chamberNoise) {
        this.warpNoise = warpNoise;
        this.chamberNoise = chamberNoise;
    }

    void prepare(int rawX, int y, int rawZ, double baseSurfaceHeight) {
        this.rawX = rawX;
        this.y = y;
        this.rawZ = rawZ;
        this.baseSurfaceHeight = baseSurfaceHeight;

        float warpSampleX = rawX * WARP_SCALE;
        float warpSampleY = y * WARP_VERTICAL_SCALE;
        float warpSampleZ = rawZ * WARP_SCALE;
        warpedX = rawX + (warpNoise.GetNoise(warpSampleX + 13.2f, warpSampleY - 7.4f, warpSampleZ + 5.1f)
                          * WARP_HORIZONTAL_AMPLITUDE);
        warpedY = y + (warpNoise.GetNoise(warpSampleX - 11.7f, warpSampleY + 17.6f, warpSampleZ - 9.3f)
                       * WARP_VERTICAL_AMPLITUDE);
        warpedZ = rawZ + (warpNoise.GetNoise(warpSampleX + 7.8f, warpSampleY + 3.1f, warpSampleZ - 15.4f)
                          * WARP_HORIZONTAL_AMPLITUDE);

        chamber = 0.5f + (0.5f * chamberNoise.GetNoise(
                warpedX * CHAMBER_HORIZONTAL_STRETCH,
                warpedY * CHAMBER_VERTICAL_STRETCH,
                warpedZ * CHAMBER_HORIZONTAL_STRETCH
        ));
        depthBelowSurface = (float) (baseSurfaceHeight - y);
        tunnelProbeComputed = false;
        tunnelContinuityComputed = false;
        tunnelGeometryUpperBoundComputed = false;
        tunnelRequiredSmoothedCoreComputed = false;
        Arrays.fill(tunnelBranchProbeAxisComputed, false);
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
        return tunnelBranchProbeAxisComputed[slot];
    }

    float getTunnelBranchProbeAxis(int slot) {
        return tunnelBranchProbeAxisValues[slot];
    }

    void cacheTunnelBranchProbeAxis(int slot, float value) {
        tunnelBranchProbeAxisValues[slot] = value;
        tunnelBranchProbeAxisComputed[slot] = true;
    }
}
