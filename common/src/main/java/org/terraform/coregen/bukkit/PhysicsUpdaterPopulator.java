package org.terraform.coregen.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.generator.BlockPopulator;
import org.jetbrains.annotations.NotNull;
import org.terraform.data.SimpleChunkLocation;
import org.terraform.data.SimpleLocation;
import org.terraform.main.TerraformGeneratorPlugin;
import org.terraform.main.config.TConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class PhysicsUpdaterPopulator extends BlockPopulator implements Listener {

    // SimpleChunkLocation to a collection of simplelocations
    public static final @NotNull Map<SimpleChunkLocation, Queue<SimpleLocation>> cache = new ConcurrentHashMap<>();
    private static final @NotNull AtomicBoolean flushIsQueued = new AtomicBoolean(false);
    // private final TerraformWorld tw;

    public PhysicsUpdaterPopulator() {
        // this.tw = tw;
        Bukkit.getPluginManager().registerEvents(this, TerraformGeneratorPlugin.get());
    }

    public static void pushChange(String world, @NotNull SimpleLocation loc) {
        SimpleChunkLocation scl = new SimpleChunkLocation(world, loc.getX(), loc.getY(), loc.getZ());
        cache.computeIfAbsent(scl, key -> new ConcurrentLinkedQueue<>()).add(loc);

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
                    });
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
                Queue<SimpleLocation> changes = cache.remove(scl);
                if (changes != null) {
                    loadedChunkRepairs++;
                    for (SimpleLocation entry : changes) {
                        Block target = w.getBlockAt(entry.getX(), entry.getY(), entry.getZ());
                        // Set block physics by calling setBlockData
                        // Note that this should not be used for complex blocks.
                        BlockData old = target.getBlockData();
                        target.setType(Material.AIR);
                        target.setBlockData(old, true);
                    }
                }
            }
            else {
                deferredChunkRepairs++;
            }
        }

        if (loadedChunkRepairs > 0 || deferredChunkRepairs > 0) {
            TerraformGeneratorPlugin.logger.info("[PhysicsUpdaterPopulator] Flushed loaded repairs for "
                                                 + loadedChunkRepairs
                                                 + " chunks, deferred "
                                                 + deferredChunkRepairs
                                                 + " unloaded chunks");
        }
    }

    @Override
    public void populate(@NotNull World world, @NotNull Random random, @NotNull Chunk chunk) {
        SimpleChunkLocation scl = new SimpleChunkLocation(chunk);
        Queue<SimpleLocation> changes = cache.remove(scl);
        if (changes != null) {
            // TerraformGeneratorPlugin.logger.info("[PhysicsUpdaterPopulator] Detected anomalous generation by NMS on " + scl + ". Running repairs on " + changes.size() + " blocks");
            for (SimpleLocation entry : changes) {
                Block target = world.getBlockAt(entry.getX(), entry.getY(), entry.getZ());
                // Set block physics by calling setBlockData
                // Note that this should not be used for complex blocks.
                BlockData old = target.getBlockData();
                target.setType(Material.AIR);
                target.setBlockData(old, true);
            }
        }
    }

    @EventHandler
    public void onChunkLoad(@NotNull ChunkLoadEvent event) {
        SimpleChunkLocation scl = new SimpleChunkLocation(event.getChunk());
        Queue<SimpleLocation> changes = cache.remove(scl);
        if (changes != null) {
            for (SimpleLocation entry : changes) {
                Block target = event.getChunk().getWorld().getBlockAt(entry.getX(), entry.getY(), entry.getZ());
                BlockData old = target.getBlockData();
                target.setType(Material.AIR);
                target.setBlockData(old, true);
            }
        }
    }

    @EventHandler
    public void onWorldUnload(@NotNull WorldUnloadEvent event) {
        TerraformGeneratorPlugin.logger.info("[PhysicsUpdaterPopulator] Flushing repairs for "
                                             + event.getWorld()
                                                    .getName()
                                             + " ("
                                             + cache.size()
                                             + " chunks in cache)");

        int processed = 0;
        for (SimpleChunkLocation scl : Set.copyOf(cache.keySet())) {
            if (!scl.getWorld().equals(event.getWorld().getName())) {
                continue;
            }
            Queue<SimpleLocation> changes = cache.remove(scl);
            if (changes != null) {
                // TerraformGeneratorPlugin.logger.info("[PhysicsUpdaterPopulator] Detected anomalous generation by NMS on " + scl + ". Running repairs on " + changes.size() + " blocks");
                for (SimpleLocation entry : changes) {
                    Block target = event.getWorld().getBlockAt(entry.getX(), entry.getY(), entry.getZ());
                    // Set block physics by calling setBlockData
                    // Note that this should not be used for complex blocks.
                    BlockData old = target.getBlockData();
                    target.setType(Material.AIR);
                    target.setBlockData(old, true);
                }
            }

            processed++;
            if (processed % 20 == 0) {
                TerraformGeneratorPlugin.logger.info("[PhysicsUpdaterPopulator] Processed "
                                                     + processed
                                                     + " more chunks");
            }
        }
    }

}
