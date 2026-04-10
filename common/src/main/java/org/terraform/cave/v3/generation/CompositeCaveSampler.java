package org.terraform.cave.v3.generation;

import org.jetbrains.annotations.NotNull;
import org.terraform.cave.v3.CaveResolvedType;
import org.terraform.cave.v3.CompositeVoxelSample;

public interface CompositeCaveSampler {
    float NEGATIVE_INFINITY_SCORE = -1_000_000f;

    boolean canCarve(int rawX, int y, int rawZ, double baseSurfaceHeight);

    @NotNull CompositeVoxelSample sampleDebug(int rawX, int y, int rawZ, double baseSurfaceHeight);

    default @NotNull CaveResolvedType resolveType(int rawX, int y, int rawZ, double baseSurfaceHeight) {
        return sampleDebug(rawX, y, rawZ, baseSurfaceHeight).resolvedType();
    }
}
