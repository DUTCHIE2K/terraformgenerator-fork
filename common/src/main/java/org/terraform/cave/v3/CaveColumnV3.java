package org.terraform.cave.v3;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class CaveColumnV3 {
    private final short baseSurfaceY;
    private final short topSolidY;
    private final @NotNull List<CaveIntervalV3> intervals;

    public CaveColumnV3(short baseSurfaceY, short topSolidY, @NotNull List<CaveIntervalV3> intervals) {
        this.baseSurfaceY = baseSurfaceY;
        this.topSolidY = topSolidY;
        this.intervals = List.copyOf(intervals);
    }

    public short getBaseSurfaceY() {
        return baseSurfaceY;
    }

    public short getTopSolidY() {
        return topSolidY;
    }

    public @NotNull List<CaveIntervalV3> getIntervals() {
        return intervals;
    }
}
