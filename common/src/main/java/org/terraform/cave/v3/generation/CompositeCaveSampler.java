package org.terraform.cave.v3.generation;

import org.jetbrains.annotations.NotNull;
import org.terraform.coregen.ChunkCache;

public interface CompositeCaveSampler {
    float NEGATIVE_INFINITY_SCORE = -1_000_000f;

    boolean canCarve(int rawX, int y, int rawZ, double baseSurfaceHeight, @NotNull ChunkCache cache);
}
