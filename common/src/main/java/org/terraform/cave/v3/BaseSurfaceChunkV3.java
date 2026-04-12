package org.terraform.cave.v3;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

public final class BaseSurfaceChunkV3 {
    static final SurfaceBlockWrite[] NO_WRITES = new SurfaceBlockWrite[0];

    private final short[] rawTerrainHeights;
    private final BaseSurfaceColumn[] surfaceColumns;
    private final SurfaceBlockWrite[][] surfaceWritesByColumn;

    BaseSurfaceChunkV3(short @NotNull [] rawTerrainHeights,
                       BaseSurfaceColumn @NotNull [] surfaceColumns,
                       SurfaceBlockWrite[] @NotNull [] surfaceWritesByColumn)
    {
        if (rawTerrainHeights.length != 256) {
            throw new IllegalArgumentException("BaseSurfaceChunkV3 requires exactly 256 raw terrain heights");
        }
        if (surfaceColumns.length != 256) {
            throw new IllegalArgumentException("BaseSurfaceChunkV3 requires exactly 256 surface columns");
        }
        if (surfaceWritesByColumn.length != 256) {
            throw new IllegalArgumentException("BaseSurfaceChunkV3 requires exactly 256 surface write columns");
        }
        this.rawTerrainHeights = rawTerrainHeights;
        this.surfaceColumns = surfaceColumns;
        this.surfaceWritesByColumn = surfaceWritesByColumn;
        for (int i = 0; i < this.surfaceWritesByColumn.length; i++) {
            if (this.surfaceWritesByColumn[i] == null) {
                this.surfaceWritesByColumn[i] = NO_WRITES;
            }
        }
    }

    public short getRawTerrainHeight(int localX, int localZ) {
        return rawTerrainHeights[index(localX, localZ)];
    }

    public short getBaseSurfaceY(int localX, int localZ) {
        return surfaceColumns[index(localX, localZ)].baseSurfaceY();
    }

    public @NotNull BaseSurfaceColumn getColumn(int localX, int localZ) {
        return surfaceColumns[index(localX, localZ)];
    }

    public void replaySurfaceWrites(ChunkGenerator.@NotNull ChunkData chunkData, int localX, int localZ) {
        for (SurfaceBlockWrite write : surfaceWritesByColumn[index(localX, localZ)]) {
            write.apply(chunkData);
        }
    }

    private int index(int localX, int localZ) {
        if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16) {
            throw new IndexOutOfBoundsException("Column outside BaseSurfaceChunkV3: " + localX + "," + localZ);
        }
        return localX + (localZ << 4);
    }

    static final class SurfaceBlockWrite {
        private final byte localX;
        private final short y;
        private final byte localZ;
        private final @NotNull Material material;

        SurfaceBlockWrite(int localX, int y, int localZ, @NotNull Material material) {
            this.localX = (byte) localX;
            this.y = (short) y;
            this.localZ = (byte) localZ;
            this.material = material;
        }

        private void apply(ChunkGenerator.@NotNull ChunkData chunkData) {
            chunkData.setBlock(localX, y, localZ, material);
        }
    }
}
