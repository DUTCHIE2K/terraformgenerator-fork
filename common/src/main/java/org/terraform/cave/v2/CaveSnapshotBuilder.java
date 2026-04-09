package org.terraform.cave.v2;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class CaveSnapshotBuilder {
    private final int chunkX;
    private final int chunkZ;
    private final short[] transformedSurfaceY = new short[256];
    private final ArrayList<CaveInterval>[] intervals = new ArrayList[256];
    private final boolean[] recorded = new boolean[256];

    @SuppressWarnings("unchecked")
    public CaveSnapshotBuilder(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public void recordColumn(int localX, int localZ, int transformedSurfaceY, @NotNull List<CaveInterval> caveIntervals) {
        int index = index(localX, localZ);
        this.transformedSurfaceY[index] = (short) transformedSurfaceY;
        this.intervals[index] = new ArrayList<>(caveIntervals);
        this.recorded[index] = true;
    }

    public @NotNull CaveSnapshot build() {
        CaveColumn[] columns = new CaveColumn[256];
        for (int i = 0; i < columns.length; i++) {
            if (!recorded[i]) {
                throw new IllegalStateException("CaveSnapshotBuilder missing column " + i + " for chunk " + chunkX + "," + chunkZ);
            }
            List<CaveInterval> caveIntervals = intervals[i] == null ? List.of() : List.copyOf(intervals[i]);
            columns[i] = new CaveColumn(transformedSurfaceY[i], caveIntervals);
        }
        return new CaveSnapshot(chunkX, chunkZ, columns);
    }

    private static int index(int localX, int localZ) {
        return localX + (localZ << 4);
    }
}
