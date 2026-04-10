package org.terraform.cave.v3;

import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.terraform.cave.v3.generation.CaveFieldSampler;
import org.terraform.cave.v3.generation.DensityCarveRules;
import org.terraform.cave.v3.generation.Phase3ACheeseFieldProvider;
import org.terraform.data.CoordPair;
import org.terraform.data.TerraformWorld;
import org.terraform.main.config.TConfig;
import org.terraform.utils.BlockUtils;
import org.terraform.utils.GenUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

public final class EntranceApprovalResolverV3 {
    private static final long ENTRANCE_SEED_SALT = 0x51A1E5EEL;
    private static final float ENTRANCE_PERTURB_MULTIPLIER = 0.35f;
    private static final int ENTRANCE_DOMINANT_MARGIN = 1;
    private static final int ENTRANCE_SEA_LEVEL_CLEARANCE = 2;
    private static final int ENTRANCE_SEARCH_RADIUS = 6;
    private static final int ENTRANCE_MIN_HORIZONTAL_LENGTH = 5;
    private static final int ENTRANCE_MAX_HORIZONTAL_LENGTH = 18;
    private static final int ENTRANCE_SIDE_SEARCH_RADIUS = 1;
    private static final int ENTRANCE_TARGET_DEPTH_STEP = 2;
    private static final int ENTRANCE_MOUTH_Y_OFFSET = 1;
    private static final float ENTRANCE_MOUTH_RADIUS = 3.4f;
    private static final float ENTRANCE_TARGET_RADIUS = 2.1f;
    private static final float ENTRANCE_TARGET_THRESHOLD_SLACK = 0.12f;
    private static final float ENTRANCE_MAX_DROP_PER_HORIZONTAL = 2.5f;
    private static final int REQUIRED_PADDING = (ENTRANCE_SEARCH_RADIUS * 2)
                                                + (ENTRANCE_MAX_HORIZONTAL_LENGTH * 2)
                                                + ENTRANCE_SIDE_SEARCH_RADIUS
                                                + getEffectPadding(ENTRANCE_MOUTH_RADIUS, ENTRANCE_TARGET_RADIUS)
                                                + 1;

    private EntranceApprovalResolverV3() {
    }

    public static int getRequiredPadding() {
        return REQUIRED_PADDING;
    }

    public static int ownerChunkX(@NotNull EntranceAnchor anchor) {
        return Math.floorDiv(anchor.cellX(), 16);
    }

    public static int ownerChunkZ(@NotNull EntranceAnchor anchor) {
        return Math.floorDiv(anchor.cellZ(), 16);
    }

    public static @NotNull Collection<EntranceAnchor> getCandidateAnchorsTouchingChunk(@NotNull TerraformWorld tw,
                                                                                        int chunkX,
                                                                                        int chunkZ)
    {
        if (!TConfig.c.CAVES_DENSITY_V1_ENTRANCES_ENABLED || !TConfig.areCavesEnabled()) {
            return Collections.emptyList();
        }

        int spacing = Math.max(32, TConfig.c.CAVES_DENSITY_V1_ENTRANCES_SPACING);
        int chunkMinX = chunkX << 4;
        int chunkMinZ = chunkZ << 4;
        int chunkMaxX = chunkMinX + 15;
        int chunkMaxZ = chunkMinZ + 15;
        int seedPadding = ENTRANCE_SEARCH_RADIUS + ENTRANCE_MAX_HORIZONTAL_LENGTH + getEffectPadding(
                ENTRANCE_MOUTH_RADIUS,
                ENTRANCE_TARGET_RADIUS
        );
        int minSeedX = chunkMinX - seedPadding;
        int maxSeedX = chunkMaxX + seedPadding;
        int minSeedZ = chunkMinZ - seedPadding;
        int maxSeedZ = chunkMaxZ + seedPadding;
        int minSeedChunkX = Math.floorDiv(minSeedX, 16);
        int maxSeedChunkX = Math.floorDiv(maxSeedX, 16);
        int minSeedChunkZ = Math.floorDiv(minSeedZ, 16);
        int maxSeedChunkZ = Math.floorDiv(maxSeedZ, 16);

        ArrayList<EntranceAnchor> anchors = new ArrayList<>();
        HashSet<Long> seenSeeds = new HashSet<>();
        for (int seedChunkX = minSeedChunkX; seedChunkX <= maxSeedChunkX; seedChunkX++) {
            for (int seedChunkZ = minSeedChunkZ; seedChunkZ <= maxSeedChunkZ; seedChunkZ++) {
                CoordPair[] seedCandidates = GenUtils.vectorRandomObjectPositions(
                        Long.hashCode(tw.getSeed() ^ ENTRANCE_SEED_SALT),
                        seedChunkX,
                        seedChunkZ,
                        spacing,
                        ENTRANCE_PERTURB_MULTIPLIER * spacing
                );
                for (CoordPair seedCandidate : seedCandidates) {
                    int seedX = (int) seedCandidate.x();
                    int seedZ = (int) seedCandidate.z();
                    if (seedX < minSeedX || seedX > maxSeedX || seedZ < minSeedZ || seedZ > maxSeedZ) {
                        continue;
                    }
                    long seedKey = (((long) seedX) << 32) ^ (seedZ & 0xffffffffL);
                    if (seenSeeds.add(seedKey)) {
                        anchors.add(new EntranceAnchor(seedX, seedZ));
                    }
                }
            }
        }
        return anchors.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(anchors);
    }

    public static @Nullable EntranceApproval resolve(@NotNull TerraformWorld tw, @NotNull EntranceAnchor anchor) {
        return resolve(tw, anchor, null);
    }

    public static @Nullable EntranceApproval resolve(@NotNull TerraformWorld tw,
                                                     @NotNull EntranceAnchor anchor,
                                                     @Nullable EntranceApprovalTrace trace)
    {
        if (!TConfig.c.CAVES_DENSITY_V1_ENTRANCES_ENABLED || !TConfig.areCavesEnabled()) {
            return null;
        }

        BaseSurfaceMap baseSurfaceMap = BaseSurfaceMapStoreV3.getBaseSurfaceMap(
                tw,
                ownerChunkX(anchor),
                ownerChunkZ(anchor),
                REQUIRED_PADDING
        );
        CaveFieldSampler densitySampler = new Phase3ACheeseFieldProvider().createSampler(tw);
        return evaluateDensityEntranceApproval(densitySampler, baseSurfaceMap, anchor, trace);
    }

    public static boolean touchesChunk(@NotNull EntranceApproval approval, int chunkX, int chunkZ) {
        int chunkMinX = chunkX << 4;
        int chunkMinZ = chunkZ << 4;
        int chunkMaxX = chunkMinX + 15;
        int chunkMaxZ = chunkMinZ + 15;
        int minX = Math.min(approval.mouthRawX(), approval.targetRawX()) - approval.effectPadding();
        int maxX = Math.max(approval.mouthRawX(), approval.targetRawX()) + approval.effectPadding();
        int minZ = Math.min(approval.mouthRawZ(), approval.targetRawZ()) - approval.effectPadding();
        int maxZ = Math.max(approval.mouthRawZ(), approval.targetRawZ()) + approval.effectPadding();
        return maxX >= chunkMinX && minX <= chunkMaxX && maxZ >= chunkMinZ && minZ <= chunkMaxZ;
    }

    private static @Nullable EntranceApproval evaluateDensityEntranceApproval(@NotNull CaveFieldSampler densitySampler,
                                                                              @NotNull BaseSurfaceMap baseSurfaceMap,
                                                                              @NotNull EntranceAnchor anchor,
                                                                              @Nullable EntranceApprovalTrace trace)
    {
        EntranceApproval bestApproval = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        int seedX = anchor.cellX();
        int seedZ = anchor.cellZ();

        for (int dx = -ENTRANCE_SEARCH_RADIUS; dx <= ENTRANCE_SEARCH_RADIUS; dx++) {
            for (int dz = -ENTRANCE_SEARCH_RADIUS; dz <= ENTRANCE_SEARCH_RADIUS; dz++) {
                int mouthX = seedX + dx;
                int mouthZ = seedZ + dz;
                if (trace != null) {
                    trace.recordLocalSearchCandidate();
                }

                BaseSurfaceColumn mouthColumn = baseSurfaceMap.getColumn(mouthX, mouthZ);
                int mouthSurfaceY = mouthColumn.baseSurfaceY();
                if (!isSafeEntranceSurface(mouthColumn)) {
                    continue;
                }
                if (trace != null) {
                    trace.recordSafeSurfacePass();
                }

                DominantEntranceSlope slope = getDominantEntranceSlope(baseSurfaceMap, mouthX, mouthZ);
                if (slope == null) {
                    continue;
                }
                if (trace != null) {
                    trace.recordSlopePass();
                }

                DensityEntranceTarget target = findDensityEntranceApprovalTarget(
                        densitySampler,
                        baseSurfaceMap,
                        mouthX,
                        mouthSurfaceY,
                        mouthZ,
                        slope.direction()
                );
                if (target == null) {
                    continue;
                }
                if (trace != null) {
                    trace.recordTargetPass();
                }

                double seedDistance = Math.sqrt(dx * dx + dz * dz);
                double score = target.score()
                               + (slope.drop() * 1.5)
                               - (seedDistance * 0.35)
                               - (target.depth() * 0.15)
                               - (target.horizontalDistance() * 0.1);
                if (score > bestScore) {
                    bestScore = score;
                    bestApproval = new EntranceApproval(
                            anchor,
                            ownerChunkX(anchor),
                            ownerChunkZ(anchor),
                            mouthX,
                            mouthSurfaceY + ENTRANCE_MOUTH_Y_OFFSET,
                            mouthZ,
                            target.rawX(),
                            target.y(),
                            target.rawZ(),
                            ENTRANCE_MOUTH_RADIUS,
                            ENTRANCE_TARGET_RADIUS,
                            getEffectPadding(ENTRANCE_MOUTH_RADIUS, ENTRANCE_TARGET_RADIUS)
                    );
                }
            }
        }

        return bestApproval;
    }

    private static @Nullable DensityEntranceTarget findDensityEntranceApprovalTarget(@NotNull CaveFieldSampler densitySampler,
                                                                                     @NotNull BaseSurfaceMap baseSurfaceMap,
                                                                                     int mouthX,
                                                                                     int mouthSurfaceY,
                                                                                     int mouthZ,
                                                                                     @NotNull BlockFace downhillDirection)
    {
        BlockFace insideDirection = downhillDirection.getOppositeFace();
        BlockFace sideDirection = BlockUtils.getRight(insideDirection);
        int minDepth = Math.max(1, TConfig.c.CAVES_DENSITY_V1_ENTRANCES_MINIMUM_CAVE_DEPTH);
        int maxDepth = Math.max(minDepth, TConfig.c.CAVES_DENSITY_V1_ENTRANCES_MAXIMUM_CAVE_DEPTH);
        DensityEntranceTarget bestTarget = null;
        float bestScore = Float.NEGATIVE_INFINITY;

        for (int horizontalDistance = ENTRANCE_MIN_HORIZONTAL_LENGTH;
             horizontalDistance <= ENTRANCE_MAX_HORIZONTAL_LENGTH;
             horizontalDistance++)
        {
            int maxDepthForDistance = Math.min(
                    maxDepth,
                    Math.max(minDepth, Math.round(horizontalDistance * ENTRANCE_MAX_DROP_PER_HORIZONTAL))
            );
            for (int sideOffset = -ENTRANCE_SIDE_SEARCH_RADIUS; sideOffset <= ENTRANCE_SIDE_SEARCH_RADIUS; sideOffset++) {
                int targetX = mouthX
                              + insideDirection.getModX() * horizontalDistance
                              + sideDirection.getModX() * sideOffset;
                int targetZ = mouthZ
                              + insideDirection.getModZ() * horizontalDistance
                              + sideDirection.getModZ() * sideOffset;
                int targetSurfaceY = baseSurfaceMap.getColumn(targetX, targetZ).baseSurfaceY();
                for (int depth = minDepth; depth <= maxDepthForDistance; depth += ENTRANCE_TARGET_DEPTH_STEP) {
                    int targetY = mouthSurfaceY - depth;
                    if (targetY >= targetSurfaceY) {
                        continue;
                    }
                    float density = densitySampler.sampleDensity(targetX, targetY, targetZ, targetSurfaceY);
                    float threshold = DensityCarveRules.getCarveThreshold(targetY, targetSurfaceY);
                    float targetScore = density - (threshold - ENTRANCE_TARGET_THRESHOLD_SLACK);
                    if (targetScore < 0f) {
                        continue;
                    }

                    targetScore = (targetScore * 100f)
                                  - (depth * 0.35f)
                                  - (horizontalDistance * 0.25f)
                                  - (Math.abs(sideOffset) * 1.5f);
                    if (targetScore > bestScore) {
                        bestScore = targetScore;
                        bestTarget = new DensityEntranceTarget(
                                targetX,
                                targetY,
                                targetZ,
                                depth,
                                horizontalDistance,
                                targetScore
                        );
                    }
                }
            }
        }

        return bestTarget;
    }

    private static boolean isSafeEntranceSurface(@NotNull BaseSurfaceColumn surfaceColumn) {
        return surfaceColumn.safety() == SurfaceSafety.DRY
               && surfaceColumn.baseSurfaceY() > org.terraform.coregen.bukkit.TerraformGenerator.seaLevel + ENTRANCE_SEA_LEVEL_CLEARANCE;
    }

    private static @Nullable DominantEntranceSlope getDominantEntranceSlope(@NotNull BaseSurfaceMap baseSurfaceMap,
                                                                            int rawX,
                                                                            int rawZ)
    {
        int centerSurfaceY = baseSurfaceMap.getColumn(rawX, rawZ).baseSurfaceY();
        BlockFace bestDirection = null;
        int bestDrop = Integer.MIN_VALUE;
        int secondBestDrop = Integer.MIN_VALUE;

        for (BlockFace face : BlockUtils.directBlockFaces) {
            int neighborSurfaceY = baseSurfaceMap.getColumn(rawX + face.getModX(), rawZ + face.getModZ()).baseSurfaceY();
            int drop = centerSurfaceY - neighborSurfaceY;
            if (drop > bestDrop) {
                secondBestDrop = bestDrop;
                bestDrop = drop;
                bestDirection = face;
            }
            else if (drop > secondBestDrop) {
                secondBestDrop = drop;
            }
        }

        if (bestDirection == null || bestDrop < TConfig.c.CAVES_DENSITY_V1_ENTRANCES_MINIMUM_SLOPE_DROP) {
            return null;
        }
        if (secondBestDrop != Integer.MIN_VALUE && bestDrop < secondBestDrop + ENTRANCE_DOMINANT_MARGIN) {
            return null;
        }
        return new DominantEntranceSlope(bestDirection, bestDrop);
    }

    private static int getEffectPadding(float mouthRadius, float targetRadius) {
        return (int) Math.ceil(Math.max(mouthRadius, targetRadius));
    }

    private record DominantEntranceSlope(@NotNull BlockFace direction, int drop) {
    }

    private record DensityEntranceTarget(int rawX,
                                         int y,
                                         int rawZ,
                                         int depth,
                                         int horizontalDistance,
                                         float score) {
    }
}
