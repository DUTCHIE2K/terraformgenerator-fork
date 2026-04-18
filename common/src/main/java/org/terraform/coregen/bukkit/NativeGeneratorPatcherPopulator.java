package org.terraform.coregen.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.generator.BlockPopulator;
import org.jetbrains.annotations.NotNull;
import org.terraform.data.SimpleChunkLocation;
import org.terraform.main.TerraformGeneratorPlugin;
import org.terraform.main.config.TConfig;
import org.terraform.utils.BlockUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class NativeGeneratorPatcherPopulator extends BlockPopulator implements Listener {

    // SimpleChunkLocation to a collection of location:blockdata entries marked for repair.
    private static final @NotNull Map<SimpleChunkLocation, Queue<Object[]>> cache = new ConcurrentHashMap<>();
    private static final @NotNull AtomicBoolean flushIsQueued = new AtomicBoolean(false);

    public NativeGeneratorPatcherPopulator() {
        // this.tw = tw;
        Bukkit.getPluginManager().registerEvents(this, TerraformGeneratorPlugin.get());
    }

    public static void pushChange(String world, int x, int y, int z, BlockData data) {
        SimpleChunkLocation scl = new SimpleChunkLocation(world, x, y, z);
        cache.computeIfAbsent(scl, key -> new ConcurrentLinkedQueue<>())
             .add(new Object[] {new int[] {x, y, z}, data});

        if (cache.size() > TConfig.c.DEVSTUFF_FLUSH_PATCHER_CACHE_FREQUENCY
            && flushIsQueued.compareAndSet(false, true))
        {
            TerraformGeneratorPlugin.taskScheduler.execSyncRegion(
                    Objects.requireNonNull(Bukkit.getWorld(world)),
                    scl.getX(),
                    scl.getZ(),
                    () -> {
                        try {
                            flushChanges();
                        }
                        finally {
                            flushIsQueued.set(false);
                        }
                    }
            );
        }
    }

    public static void flushChanges() {
        if (cache.isEmpty()) {
            return;
        }
        int loadedChunkRepairs = 0;
        int deferredChunkRepairs = 0;
        ArrayList<SimpleChunkLocation> locs = new ArrayList<>(cache.keySet());
        for (SimpleChunkLocation scl : locs) {
            World w = Bukkit.getWorld(scl.getWorld());
            if (w == null) {
                continue;
            }
            if (w.isChunkLoaded(scl.getX(), scl.getZ())) {
                Queue<Object[]> changes = cache.remove(scl);
                if (changes != null) {
                    loadedChunkRepairs++;
                    for (Object[] entry : changes) {
                        applyChange(w, entry);
                    }
                }
            }
            else {
                deferredChunkRepairs++;
            }
        }

        if (loadedChunkRepairs > 0 || deferredChunkRepairs > 0) {
            TerraformGeneratorPlugin.logger.info("[NativeGeneratorPatcher] Flushed loaded repairs for "
                                                 + loadedChunkRepairs
                                                 + " chunks, deferred "
                                                 + deferredChunkRepairs
                                                 + " unloaded chunks");
        }
    }

    @Override
    public void populate(@NotNull World world, @NotNull Random random, @NotNull Chunk chunk) {
        SimpleChunkLocation scl = new SimpleChunkLocation(chunk);
        Queue<Object[]> changes = cache.remove(scl);
        if (changes != null) {
            // TerraformGeneratorPlugin.logger.info("[NativeGeneratorPatcher] Flushing repairs (" + cache.size() + " chunks), pushed by BlockPopulator");
            for (Object[] entry : changes) {
                applyChange(world, entry);
            }
        }
    }

    @EventHandler
    public void onChunkLoad(@NotNull ChunkLoadEvent event) {
        SimpleChunkLocation scl = new SimpleChunkLocation(event.getChunk());
        Queue<Object[]> changes = cache.remove(scl);
        if (changes != null) {
            // TerraformGeneratorPlugin.logger.info("[NativeGeneratorPatcher] Flushing repairs for 1 chunk (" + scl.getX() + "," + scl.getZ() + "), pushed by chunkloadevent");
            for (Object[] entry : changes) {
                applyChange(event.getChunk().getWorld(), entry);
            }
        }
    }

    @EventHandler
    public void onWorldUnload(@NotNull WorldUnloadEvent event) {
        TerraformGeneratorPlugin.logger.info("[NativeGeneratorPatcher] Flushing repairs for "
                                             + event.getWorld()
                                                    .getName()
                                             + " ("
                                             + cache.size()
                                             + " chunks in cache), triggered by world unload");

        int processed = 0;
        for (SimpleChunkLocation scl : Set.copyOf(cache.keySet())) {
            if (!scl.getWorld().equals(event.getWorld().getName())) {
                continue;
            }
            Queue<Object[]> changes = cache.remove(scl);
            if (changes != null) {
                for (Object[] entry : changes) {
                    applyChange(event.getWorld(), entry);
                }
            }

            processed++;
            if (processed % 20 == 0) {
                TerraformGeneratorPlugin.logger.info("[NativeGeneratorPatcher] Processed "
                                                     + processed
                                                     + "/"
                                                     + cache.size()
                                                     + " chunks");
            }
        }
    }

    private static void applyChange(@NotNull World world, Object @NotNull [] entry) {
        int[] loc = (int[]) entry[0];
        BlockData data = (BlockData) entry[1];
        if (data instanceof org.bukkit.block.data.Waterlogged) {
            BlockData replacedData = world.getBlockAt(loc[0], loc[1], loc[2]).getBlockData();
            data = BlockUtils.correctWaterloggedData(
                    data,
                    replacedData.getMaterial(),
                    replacedData,
                    loc[1]
            );
        }
        world.getBlockAt(loc[0], loc[1], loc[2]).setBlockData(data, false);
    }

}
