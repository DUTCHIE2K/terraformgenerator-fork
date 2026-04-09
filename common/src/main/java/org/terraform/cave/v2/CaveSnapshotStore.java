package org.terraform.cave.v2;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.terraform.data.TWCoordPair;
import org.terraform.data.TerraformWorld;

import java.util.concurrent.ConcurrentHashMap;

public final class CaveSnapshotStore {
    private static final ConcurrentHashMap<TWCoordPair, CaveSnapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private CaveSnapshotStore() {
    }

    public static void publish(@NotNull TerraformWorld tw, int chunkX, int chunkZ, @NotNull CaveSnapshot snapshot) {
        SNAPSHOTS.put(new TWCoordPair(tw, chunkX, chunkZ), snapshot);
    }

    public static @Nullable CaveSnapshot take(@NotNull TerraformWorld tw, int chunkX, int chunkZ) {
        return SNAPSHOTS.remove(new TWCoordPair(tw, chunkX, chunkZ));
    }

    public static void clearWorld(@NotNull TerraformWorld tw) {
        SNAPSHOTS.keySet().removeIf(key -> key.tw().getName().equals(tw.getName()));
    }
}
