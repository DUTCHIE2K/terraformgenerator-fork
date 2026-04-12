package org.terraform.cave.v3;

import org.jetbrains.annotations.NotNull;

public final class BaseSurfaceMap {
    private final int minRawX;
    private final int minRawZ;
    private final int width;
    private final int depth;
    private final BaseSurfaceColumn @NotNull [] columns;

    BaseSurfaceMap(int minRawX,
                   int minRawZ,
                   int width,
                   int depth,
                   BaseSurfaceColumn @NotNull [] columns)
    {
        if (width <= 0 || depth <= 0) {
            throw new IllegalArgumentException("BaseSurfaceMap dimensions must be positive");
        }
        if (columns.length != width * depth) {
            throw new IllegalArgumentException("BaseSurfaceMap column count mismatch");
        }
        this.minRawX = minRawX;
        this.minRawZ = minRawZ;
        this.width = width;
        this.depth = depth;
        this.columns = columns;
    }

    public int getMinRawX() {
        return minRawX;
    }

    public int getMinRawZ() {
        return minRawZ;
    }

    public int getMaxRawX() {
        return minRawX + width - 1;
    }

    public int getMaxRawZ() {
        return minRawZ + depth - 1;
    }

    public int getWidth() {
        return width;
    }

    public int getDepth() {
        return depth;
    }

    public @NotNull BaseSurfaceColumn getColumn(int rawX, int rawZ) {
        return columns[index(rawX, rawZ)];
    }

    public boolean contains(int rawX, int rawZ) {
        return rawX >= minRawX && rawX <= getMaxRawX() && rawZ >= minRawZ && rawZ <= getMaxRawZ();
    }

    private int index(int rawX, int rawZ) {
        if (!contains(rawX, rawZ)) {
            throw new IndexOutOfBoundsException("Column outside BaseSurfaceMap: " + rawX + "," + rawZ);
        }
        return (rawX - minRawX) + ((rawZ - minRawZ) * width);
    }
}
