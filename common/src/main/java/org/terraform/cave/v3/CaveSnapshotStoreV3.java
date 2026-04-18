package org.terraform.cave.v3;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.terraform.coregen.ChunkCache;
import org.terraform.data.TWCoordPair;
import org.terraform.data.TerraformWorld;

import java.util.concurrent.ConcurrentHashMap;

public final class CaveSnapshotStoreV3 {
    private static final ConcurrentHashMap<TWCoordPair, Object> GAMEPLAY = new ConcurrentHashMap<>();

    private CaveSnapshotStoreV3() {
    }

    public static void publishGameplay(@NotNull TerraformWorld tw, int chunkX, int chunkZ, @NotNull CaveSnapshotV3 snapshot) {
        GAMEPLAY.put(new TWCoordPair(tw, chunkX, chunkZ), snapshot);
    }

    public static void publishGameplay(@NotNull TerraformWorld tw,
                                       int chunkX,
                                       int chunkZ,
                                       @NotNull ChunkCache.CompositeV3ChunkPrefill prefill)
    {
        GAMEPLAY.put(new TWCoordPair(tw, chunkX, chunkZ), prefill);
    }

    public static @Nullable CaveSnapshotV3 takeGameplay(@NotNull TerraformWorld tw, int chunkX, int chunkZ) {
        Object gameplay = GAMEPLAY.remove(new TWCoordPair(tw, chunkX, chunkZ));
        if (gameplay instanceof CaveSnapshotV3 snapshot) {
            return snapshot;
        }
        if (gameplay instanceof ChunkCache.CompositeV3ChunkPrefill prefill) {
            return prefill.toSnapshot(chunkX, chunkZ);
        }
        return null;
    }

    public static void clearWorld(@NotNull TerraformWorld tw) {
        GAMEPLAY.keySet().removeIf(key -> key.tw().getName().equals(tw.getName()));
    }
}
