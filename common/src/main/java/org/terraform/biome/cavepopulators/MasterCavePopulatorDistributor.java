package org.terraform.biome.cavepopulators;

import org.jetbrains.annotations.NotNull;
import org.terraform.biome.BiomeBank;
import org.terraform.cave.v3.CaveIntervalMetadata;
import org.terraform.cave.v3.CaveIntervalV3;
import org.terraform.cave.v3.CaveSnapshotV3;
import org.terraform.cave.v3.SurfaceConnectivity;
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
                         @NotNull CaveSnapshotV3 snapshot)
    {
        populateInternal(
                tw,
                random,
                data,
                generateClusters,
                (localX, localZ) -> getSnapshotCandidates(snapshot, localX, localZ)
        );
    }

    private void populateInternal(@NotNull TerraformWorld tw,
                                  @NotNull Random random,
                                  @NotNull PopulatorDataAbstract data,
                                  boolean generateClusters,
                                  @NotNull CaveCandidateProvider candidateProvider)
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

                List<CaveDecorationCandidate> candidates = candidateProvider.getCandidates(x & 0xF, z & 0xF);

                // This is the index to spawn the cluster in.
                int clusterPair = getClusterCandidateIndex(candidates, random);

                for (CaveDecorationCandidate candidate : candidates) {
                    CoordPair pair = candidate.pair();

                    // Biome disallows caves above this height
                    if (pair.x() > maxHeightForCaves) {
                        if (isClusterCandidate(candidate)) {
                            clusterPair--;
                        }
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
                        if (isClusterCandidate(candidate)) {
                            clusterPair--;
                        }
                        continue;
                    }

                    AbstractCavePopulator pop;

                    if (candidate.metadata().openToSurface() == SurfaceConnectivity.YES) {
                        pop = new EmptyCavePopulator();
                    }
                    else if (reg == null && clusterDecoratedPairs.contains(AbstractCaveClusterPopulator.getDecoratedPairKey(
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
                        pop = (clusterPair == 0 && reg != null && isClusterCandidate(candidate) && TConfig.c.FEATURE_CAVECLUSTERS_ENABLED) ?
                              reg.getPopulator(random) : bank.getCavePop();
                    }
                    if (isClusterCandidate(candidate)) {
                        clusterPair--;
                    }

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

    private static @NotNull List<CaveDecorationCandidate> getSnapshotCandidates(@NotNull CaveSnapshotV3 snapshot,
                                                                                 int localX,
                                                                                 int localZ)
    {
        List<CaveIntervalV3> intervals = snapshot.getColumn(localX, localZ).getIntervals();
        if (intervals.isEmpty()) {
            return Collections.emptyList();
        }

        List<CaveDecorationCandidate> pairs = new ArrayList<>(intervals.size());
        for (CaveIntervalV3 interval : intervals) {
            pairs.add(new CaveDecorationCandidate(
                    new CoordPair(interval.ceilingAirY(), interval.floorSolidY()),
                    interval.metadata()
            ));
        }
        return pairs;
    }

    private static int getClusterCandidateIndex(@NotNull List<CaveDecorationCandidate> candidates, @NotNull Random random) {
        int eligible = 0;
        for (CaveDecorationCandidate candidate : candidates) {
            if (isClusterCandidate(candidate)) {
                eligible++;
            }
        }
        return eligible > 0 ? random.nextInt(eligible) : -1;
    }

    private static boolean isClusterCandidate(@NotNull CaveDecorationCandidate candidate) {
        return candidate.metadata().openToSurface() != SurfaceConnectivity.YES;
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

    @FunctionalInterface
    private interface CaveCandidateProvider {
        @NotNull List<CaveDecorationCandidate> getCandidates(int localX, int localZ);
    }

    private record CaveDecorationCandidate(@NotNull CoordPair pair, @NotNull CaveIntervalMetadata metadata) {
    }
}
