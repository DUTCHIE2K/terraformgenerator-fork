package org.terraform.cave.v3;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class CaveSnapshotV3Builder {
    private final int chunkX;
    private final int chunkZ;
    private final short[] baseSurfaceY = new short[256];
    private final short[] topSolidY = new short[256];
    private final ArrayList<CaveIntervalV3>[] intervals;
    private final boolean[] recorded = new boolean[256];

    @SuppressWarnings("unchecked")
    public CaveSnapshotV3Builder(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.intervals = new ArrayList[256];
    }

    public void recordColumn(int localX,
                             int localZ,
                             int baseSurfaceY,
                             int topSolidY,
                             @NotNull List<CaveIntervalV3> caveIntervals)
    {
        int index = index(localX, localZ);
        this.baseSurfaceY[index] = (short) baseSurfaceY;
        this.topSolidY[index] = (short) topSolidY;
        this.intervals[index] = new ArrayList<>(caveIntervals);
        this.recorded[index] = true;
    }

    public @NotNull CaveSnapshotV3 build() {
        CaveColumnV3[] columns = new CaveColumnV3[256];
        for (int i = 0; i < columns.length; i++) {
            if (!recorded[i]) {
                throw new IllegalStateException("CaveSnapshotV3Builder missing column " + i + " for chunk " + chunkX + "," + chunkZ);
            }
            List<CaveIntervalV3> caveIntervals = intervals[i] == null ? List.of() : List.copyOf(intervals[i]);
            columns[i] = new CaveColumnV3(baseSurfaceY[i], topSolidY[i], caveIntervals);
        }
        return new CaveSnapshotV3(chunkX, chunkZ, columns);
    }

    private static int index(int localX, int localZ) {
        return localX + (localZ << 4);
    }
}
