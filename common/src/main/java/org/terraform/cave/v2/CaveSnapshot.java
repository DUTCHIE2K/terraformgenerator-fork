package org.terraform.cave.v2;

import org.jetbrains.annotations.NotNull;

public final class CaveSnapshot {
    private final int chunkX;
    private final int chunkZ;
    private final CaveColumn @NotNull [] columns;

    public CaveSnapshot(int chunkX, int chunkZ, CaveColumn @NotNull [] columns) {
        if (columns.length != 256) {
            throw new IllegalArgumentException("CaveSnapshot requires exactly 256 columns");
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

    public @NotNull CaveColumn getColumn(int localX, int localZ) {
        return columns[index(localX, localZ)];
    }

    private static int index(int localX, int localZ) {
        return localX + (localZ << 4);
    }
}
