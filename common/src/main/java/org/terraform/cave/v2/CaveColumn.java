package org.terraform.cave.v2;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class CaveColumn {
    private final short transformedSurfaceY;
    private final @NotNull List<CaveInterval> intervals;

    public CaveColumn(short transformedSurfaceY, @NotNull List<CaveInterval> intervals) {
        this.transformedSurfaceY = transformedSurfaceY;
        this.intervals = List.copyOf(intervals);
    }

    public short getTransformedSurfaceY() {
        return transformedSurfaceY;
    }

    public @NotNull List<CaveInterval> getIntervals() {
        return intervals;
    }
}
