package org.terraform.biome.cavepopulators;

import org.jetbrains.annotations.NotNull;
import org.terraform.biome.BiomeBank;
import org.terraform.cave.v2.CaveInterval;
import org.terraform.cave.v2.CaveSnapshot;
import org.terraform.coregen.populatordata.PopulatorDataAbstract;
import org.terraform.data.CoordPair;
import org.terraform.data.SimpleBlock;
import org.terraform.data.TerraformWorld;
import org.terraform.main.TerraformGeneratorPlugin;
import org.terraform.main.config.TConfig;
import org.terraform.utils.BlockUtils;
import org.terraform.utils.GenUtils;

import java.util.*;

/**
 * This class will distribute ALL cave post-population to the right populators,
 * as well as handle placements for special small clusters like dripzone, lush and
 * deep zones.
 */
public class MasterCavePopulatorDistributor {
    public static final int AMBIENT_MINIMUM_CAVE_HEIGHT = 4;

    private static final HashSet<Class<?>> populatedBefore = new HashSet<>();

    public void populate(@NotNull TerraformWorld tw,
                         @NotNull Random random,
                         @NotNull PopulatorDataAbstract data,
                         boolean generateClusters,
                         @NotNull CaveSnapshot snapshot)
    {
        HashMap<CoordPair, CaveClusterRegistry> clusters = generateClusters ?
           calculateClusterLocations(
                random,
                tw,
                data.getChunkX(),
                data.getChunkZ()
           ) : new HashMap<>();
        Set<String> clusterDecoratedPairs = new HashSet<>();

        for (int x = data.getChunkX() * 16; x < data.getChunkX() * 16 + 16; x++) {
            for (int z = data.getChunkZ() * 16; z < data.getChunkZ() * 16 + 16; z++) {

                BiomeBank bank = tw.getBiomeBank(x, z);
                int maxHeightForCaves = bank.getHandler().getMaxHeightForCaves(tw, x, z);

                // Remove clusters when they're spawned.
                CaveClusterRegistry reg = clusters.remove(new CoordPair(x, z));

                Collection<CoordPair> pairs = getSnapshotCaveCeilFloors(snapshot, x & 0xF, z & 0xF);

                // This is the index to spawn the cluster in.
                int clusterPair = !pairs.isEmpty() ? random.nextInt(pairs.size()) : 0;

                for (CoordPair pair : pairs) {

                    // Biome disallows caves above this height
                    if (pair.x() > maxHeightForCaves) {
                        continue;
                    }

                    SimpleBlock ceil = new SimpleBlock(data, x, pair.x(), z); // non-solid
                    SimpleBlock floor = new SimpleBlock(data, x, pair.z(), z); // solid

                    // If this is wet, don't touch it.
                    // Don't populate inside amethysts
                    if (BlockUtils.amethysts.contains(floor.getType())
                        || BlockUtils.fluids.contains(floor.getUp()
                                                           .getType())
                        || BlockUtils.amethysts.contains(ceil.getDown().getType()))
                    {
                        continue;
                    }

                    AbstractCavePopulator pop;

                    if (reg == null && clusterDecoratedPairs.contains(AbstractCaveClusterPopulator.getDecoratedPairKey(
                            x,
                            z,
                            pair.x(),
                            pair.z()
                    ))) {
                        pop = new EmptyCavePopulator();
                    }
                    /*
                     * Deep cave floors will use the deep cave populator.
                     * This has to happen, as most surfaces
                     * too low down will be lava. Hard to decorate.
                     */
                    else if (floor.getY() < TerraformGeneratorPlugin.injector.getMinY() + 32) {
                        pop = new DeepCavePopulator();
                    }
                    else {
                        /*
                         * Cluster Populators won't just decorate one block, they
                         * will populate the surrounding surfaces in a fuzzy
                         * radius.
                         */
                        // If there is no cluster to spawn, then revert to the
                        // basic biome-based cave populator
                        pop = (clusterPair == 0 && reg != null && TConfig.c.FEATURE_CAVECLUSTERS_ENABLED) ?
                              reg.getPopulator(random) : bank.getCavePop();
                    }
                    clusterPair--;

                    if(!(pop instanceof AbstractCaveClusterPopulator)
                        && !TConfig.c.FEATURE_CAVEDECORATORS_ENABLED)
                        pop = new EmptyCavePopulator();

                    pop.populate(tw, random, ceil, floor);
                    if (pop instanceof AbstractCaveClusterPopulator clusterPop) {
                        clusterDecoratedPairs.addAll(clusterPop.getDecoratedPairs());
                    }

                    // Locating and debug print
                    if (populatedBefore.add(pop.getClass())) {
                        TerraformGeneratorPlugin.logger.info("Spawning "
                                                             + pop.getClass().getSimpleName()
                                                             + " at "
                                                             + floor);
                    }
                }
            }
        }
    }

    private @NotNull HashMap<CoordPair, CaveClusterRegistry> calculateClusterLocations(@NotNull Random rand,
                                                                                       @NotNull TerraformWorld tw,
                                                                                       int chunkX,
                                                                                       int chunkZ)
    {
        HashMap<CoordPair, CaveClusterRegistry> locs = new HashMap<>();
        //Don't waste compute if caves don't exist
        if(!TConfig.areCavesEnabled()) return locs;

        for (CaveClusterRegistry type : CaveClusterRegistry.values()) {
            CoordPair[] positions = getClusterChunkPositions(tw, chunkX, chunkZ, type);
            for (CoordPair pos : positions) {
                if (locs.containsKey(pos))
                // give a chance to replace the old one
                {
                    if (rand.nextBoolean()) {
                        continue;
                    }
                }

                locs.put(pos, type);
            }

        }

        return locs;
    }

    static CoordPair @NotNull [] getClusterChunkPositions(@NotNull TerraformWorld tw,
                                                          int chunkX,
                                                          int chunkZ,
                                                          @NotNull CaveClusterRegistry type)
    {
        return GenUtils.vectorRandomObjectPositions(tw.getHashedRand(
                        chunkX,
                        type.getHashSeed(),
                        chunkZ
                ).nextInt(9999999),
                chunkX,
                chunkZ,
                type.getSeparation(),
                type.getPertub()
        );
    }

    private static @NotNull Collection<CoordPair> getSnapshotCaveCeilFloors(@NotNull CaveSnapshot snapshot,
                                                                             int localX,
                                                                             int localZ)
    {
        List<CaveInterval> intervals = snapshot.getColumn(localX, localZ).getIntervals();
        if (intervals.isEmpty()) {
            return Collections.emptyList();
        }

        List<CoordPair> pairs = new ArrayList<>(intervals.size());
        for (CaveInterval interval : intervals) {
            pairs.add(new CoordPair(interval.ceilingAirY(), interval.floorSolidY()));
        }
        return pairs;
    }

    public static @NotNull Collection<CoordPair> getFilteredPairs(@NotNull Collection<CoordPair> pairs,
                                                                  int minimumHeight)
    {
        if (minimumHeight <= 1 || pairs.isEmpty()) {
            return pairs;
        }

        List<CoordPair> filtered = new ArrayList<>(pairs.size());
        for (CoordPair pair : pairs) {
            if (pair.x() - pair.z() >= minimumHeight) {
                filtered.add(pair);
            }
        }
        return filtered;
    }
}
