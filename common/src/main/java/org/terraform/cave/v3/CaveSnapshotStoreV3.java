package org.terraform.cave.v3;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.terraform.data.TWCoordPair;
import org.terraform.data.TerraformWorld;

import java.util.concurrent.ConcurrentHashMap;

public final class CaveSnapshotStoreV3 {
    private static final ConcurrentHashMap<TWCoordPair, CaveSnapshotV3> GAMEPLAY = new ConcurrentHashMap<>();

    private CaveSnapshotStoreV3() {
    }

    public static void publishGameplay(@NotNull TerraformWorld tw, int chunkX, int chunkZ, @NotNull CaveSnapshotV3 snapshot) {
        GAMEPLAY.put(new TWCoordPair(tw, chunkX, chunkZ), snapshot);
    }

    public static @Nullable CaveSnapshotV3 takeGameplay(@NotNull TerraformWorld tw, int chunkX, int chunkZ) {
        return GAMEPLAY.remove(new TWCoordPair(tw, chunkX, chunkZ));
    }

    public static void clearWorld(@NotNull TerraformWorld tw) {
        GAMEPLAY.keySet().removeIf(key -> key.tw().getName().equals(tw.getName()));
    }
}
