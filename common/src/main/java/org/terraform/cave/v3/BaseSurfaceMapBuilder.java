package org.terraform.cave.v3;

import org.jetbrains.annotations.NotNull;

public final class BaseSurfaceMapBuilder {
    private final int minRawX;
    private final int minRawZ;
    private final int width;
    private final int depth;
    private final BaseSurfaceColumn[] columns;
    private final boolean[] recorded;

    public BaseSurfaceMapBuilder(int chunkX, int chunkZ, int padding) {
        this((chunkX << 4) - padding, (chunkZ << 4) - padding, 16 + (padding * 2), 16 + (padding * 2));
    }

    public BaseSurfaceMapBuilder(int minRawX, int minRawZ, int width, int depth) {
        if (width <= 0 || depth <= 0) {
            throw new IllegalArgumentException("BaseSurfaceMapBuilder dimensions must be positive");
        }
        this.minRawX = minRawX;
        this.minRawZ = minRawZ;
        this.width = width;
        this.depth = depth;
        this.columns = new BaseSurfaceColumn[width * depth];
        this.recorded = new boolean[width * depth];
    }

    public void recordColumn(int rawX,
                             int rawZ,
                             int baseSurfaceY,
                             @NotNull SurfaceSafety safety,
                             @NotNull SurfaceTopState topState)
    {
        int index = index(rawX, rawZ);
        columns[index] = new BaseSurfaceColumn((short) baseSurfaceY, safety, topState);
        recorded[index] = true;
    }

    public @NotNull BaseSurfaceMap build() {
        for (int i = 0; i < columns.length; i++) {
            if (!recorded[i]) {
                throw new IllegalStateException("BaseSurfaceMapBuilder missing column " + i);
            }
        }
        return new BaseSurfaceMap(minRawX, minRawZ, width, depth, columns);
    }

    private int index(int rawX, int rawZ) {
        int localX = rawX - minRawX;
        int localZ = rawZ - minRawZ;
        if (localX < 0 || localX >= width || localZ < 0 || localZ >= depth) {
            throw new IndexOutOfBoundsException("Column outside BaseSurfaceMapBuilder: " + rawX + "," + rawZ);
        }
        return localX + (localZ * width);
    }
}
