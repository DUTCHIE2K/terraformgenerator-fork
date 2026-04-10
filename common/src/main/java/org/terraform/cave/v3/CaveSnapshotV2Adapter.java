package org.terraform.cave.v3;

import org.jetbrains.annotations.NotNull;
import org.terraform.cave.v2.CaveColumn;
import org.terraform.cave.v2.CaveInterval;
import org.terraform.cave.v2.CaveSnapshot;

import java.util.ArrayList;
import java.util.List;

public final class CaveSnapshotV2Adapter {
    private CaveSnapshotV2Adapter() {
    }

    public static @NotNull CaveSnapshot toLegacy(@NotNull CaveSnapshotV3 snapshot) {
        CaveColumn[] columns = new CaveColumn[256];
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                CaveColumnV3 sourceColumn = snapshot.getColumn(localX, localZ);
                List<CaveInterval> legacyIntervals = new ArrayList<>(sourceColumn.getIntervals().size());
                for (CaveIntervalV3 interval : sourceColumn.getIntervals()) {
                    legacyIntervals.add(new CaveInterval(interval.ceilingAirY(), interval.floorSolidY()));
                }
                columns[localX + (localZ << 4)] = new CaveColumn(sourceColumn.getTopSolidY(), legacyIntervals);
            }
        }
        return new CaveSnapshot(snapshot.getChunkX(), snapshot.getChunkZ(), columns);
    }
}
