package org.terraform.cave.v3;

import org.jetbrains.annotations.NotNull;

public final class CaveSnapshotV3 {
    private final int chunkX;
    private final int chunkZ;
    private final CaveColumnV3 @NotNull [] columns;

    public CaveSnapshotV3(int chunkX, int chunkZ, CaveColumnV3 @NotNull [] columns) {
        if (columns.length != 256) {
            throw new IllegalArgumentException("CaveSnapshotV3 requires exactly 256 columns");
        }
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.columns = columns.clone();
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public @NotNull CaveColumnV3 getColumn(int localX, int localZ) {
        return columns[index(localX, localZ)];
    }

    private static int index(int localX, int localZ) {
        return localX + (localZ << 4);
    }
}
