package org.terraform.cave.v3.generation;

import org.jetbrains.annotations.NotNull;
import org.terraform.cave.v3.CaveIntentType;
import org.terraform.cave.v3.CaveResolvedType;
import org.terraform.cave.v3.CompositeVoxelSample;
import org.terraform.cave.v3.EntranceApproval;
import org.terraform.main.config.TConfig;

import java.util.Collection;

public final class DensityCompositeCaveSampler implements CompositeCaveSampler {
    private static final float ENTRANCE_MIN_SURFACE_INFLUENCE = 0.05f;
    private static final float ENTRANCE_FORCE_CARVE_INFLUENCE = 0.24f;

    private final @NotNull CaveFieldSampler cheeseDensitySampler;
    private final @NotNull CaveFieldSampler spaghettiDensitySampler;
    private final @NotNull EntranceApproval[] entrances;

    public DensityCompositeCaveSampler(@NotNull CaveFieldSampler cheeseDensitySampler,
                                       @NotNull CaveFieldSampler spaghettiDensitySampler,
                                       @NotNull Collection<EntranceApproval> entrances)
    {
        this.cheeseDensitySampler = cheeseDensitySampler;
        this.spaghettiDensitySampler = spaghettiDensitySampler;
        this.entrances = entrances.toArray(EntranceApproval[]::new);
    }

    @Override
    public boolean canCarve(int rawX, int y, int rawZ, double baseSurfaceHeight) {
        float baseDensity = cheeseDensitySampler.sampleDensity(rawX, y, rawZ, baseSurfaceHeight);
        float cheeseLocalScore = getCheeseLocalScore(baseDensity);
        float spaghettiLocalScore = spaghettiDensitySampler.sampleDensity(rawX, y, rawZ, baseSurfaceHeight);
        float entranceInfluence = getEntranceInfluence(rawX, y, rawZ);
        float entranceLocalScore = getEntranceLocalScore(cheeseLocalScore, entranceInfluence);
        float globalPenalty = DensityCarveRules.getGlobalPenalty(y, baseSurfaceHeight);
        float cheeseScore = applyStandardGlobalPenalty(y, baseSurfaceHeight, cheeseLocalScore, globalPenalty);
        float spaghettiScore = applyStandardGlobalPenalty(y, baseSurfaceHeight, spaghettiLocalScore, globalPenalty);
        float entranceScore = applyEntranceGlobalPenalty(entranceLocalScore, globalPenalty);
        return Math.max(cheeseScore, Math.max(spaghettiScore, entranceScore)) >= 0f;
    }

    @Override
    public @NotNull CompositeVoxelSample sampleDebug(int rawX, int y, int rawZ, double baseSurfaceHeight) {
        float rawCheese = cheeseDensitySampler.sampleDensity(rawX, y, rawZ, baseSurfaceHeight);
        float cheeseLocalScore = getCheeseLocalScore(rawCheese);
        float spaghettiLocalScore = spaghettiDensitySampler.sampleDensity(rawX, y, rawZ, baseSurfaceHeight);
        float rawEntrance = getEntranceInfluence(rawX, y, rawZ);
        float entranceLocalScore = getEntranceLocalScore(cheeseLocalScore, rawEntrance);
        float globalPenalty = DensityCarveRules.getGlobalPenalty(y, baseSurfaceHeight);
        float cheeseScore = applyStandardGlobalPenalty(y, baseSurfaceHeight, cheeseLocalScore, globalPenalty);
        float spaghettiScore = applyStandardGlobalPenalty(y, baseSurfaceHeight, spaghettiLocalScore, globalPenalty);
        float entranceScore = applyEntranceGlobalPenalty(entranceLocalScore, globalPenalty);
        float finalScore = Math.max(cheeseScore, Math.max(spaghettiScore, entranceScore));

        CaveIntentType intentType = resolveIntentType(cheeseLocalScore, spaghettiLocalScore, entranceLocalScore);
        CaveResolvedType resolvedType = resolveResolvedType(cheeseScore, spaghettiScore, entranceScore);
        float confidenceScale = Math.max(0.0001f, TConfig.c.CAVES_DENSITY_V1_CONFIDENCE_SCALE);
        float confidence = finalScore <= NEGATIVE_INFINITY_SCORE / 2f
                           ? 0f
                           : DensityCarveRules.clamp01(Math.abs(finalScore) / confidenceScale);
        return new CompositeVoxelSample(intentType, resolvedType, finalScore, confidence);
    }

    private float getCheeseLocalScore(float baseDensity) {
        return baseDensity - DensityCarveRules.getBaseThreshold();
    }

    private float getEntranceLocalScore(float cheeseLocalScore, float entranceInfluence) {
        if (entranceInfluence < ENTRANCE_MIN_SURFACE_INFLUENCE) {
            return NEGATIVE_INFINITY_SCORE;
        }
        float combinedScore = cheeseLocalScore + entranceInfluence;
        float forceScore = entranceInfluence - ENTRANCE_FORCE_CARVE_INFLUENCE;
        return Math.max(combinedScore, forceScore);
    }

    private float applyStandardGlobalPenalty(int y, double baseSurfaceHeight, float localScore, float globalPenalty) {
        if (localScore <= NEGATIVE_INFINITY_SCORE / 2f) {
            return NEGATIVE_INFINITY_SCORE;
        }
        if (!DensityCarveRules.canCarveAtY(y, baseSurfaceHeight)) {
            return NEGATIVE_INFINITY_SCORE;
        }
        return localScore - globalPenalty;
    }

    private float applyEntranceGlobalPenalty(float localScore, float globalPenalty) {
        if (localScore <= NEGATIVE_INFINITY_SCORE / 2f) {
            return NEGATIVE_INFINITY_SCORE;
        }
        return localScore - globalPenalty;
    }

    private float getEntranceInfluence(int rawX, int y, int rawZ) {
        float bestInfluence = 0f;
        for (EntranceApproval approval : entrances) {
            bestInfluence = Math.max(bestInfluence, getEntranceInfluence(approval, rawX, y, rawZ));
        }
        return bestInfluence;
    }

    private float getEntranceInfluence(@NotNull EntranceApproval approval, int rawX, int y, int rawZ) {
        double ax = approval.mouthRawX();
        double ay = approval.mouthY();
        double az = approval.mouthRawZ();
        double bx = approval.targetRawX();
        double by = approval.targetY();
        double bz = approval.targetRawZ();
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        if (lengthSquared <= 0.0001) {
            return 0f;
        }

        double px = rawX + 0.5;
        double py = y + 0.5;
        double pz = rawZ + 0.5;
        double t = ((px - ax) * dx + (py - ay) * dy + (pz - az) * dz) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        double closestX = ax + dx * t;
        double closestY = ay + dy * t;
        double closestZ = az + dz * t;
        double radius = approval.mouthRadius() + (approval.targetRadius() - approval.mouthRadius()) * t;
        double distX = px - closestX;
        double distY = py - closestY;
        double distZ = pz - closestZ;
        double distanceSquared = distX * distX + distY * distY + distZ * distZ;
        double radiusSquared = radius * radius;
        if (distanceSquared > radiusSquared) {
            return 0f;
        }

        double normalizedDistance = Math.sqrt(distanceSquared) / radius;
        double profile = 1.0 - (normalizedDistance * normalizedDistance);
        return (float) (1.25f * profile);
    }

    private static @NotNull CaveIntentType resolveIntentType(float cheeseLocalScore,
                                                             float spaghettiLocalScore,
                                                             float entranceLocalScore)
    {
        float finalLocalScore = Math.max(cheeseLocalScore, Math.max(spaghettiLocalScore, entranceLocalScore));
        if (finalLocalScore < 0f) {
            return CaveIntentType.NONE;
        }
        if (entranceLocalScore >= cheeseLocalScore && entranceLocalScore >= spaghettiLocalScore) {
            return CaveIntentType.ENTRANCE;
        }
        if (spaghettiLocalScore >= cheeseLocalScore) {
            return CaveIntentType.SPAGHETTI;
        }
        return CaveIntentType.CHEESE;
    }

    private static @NotNull CaveResolvedType resolveResolvedType(float cheeseScore,
                                                                 float spaghettiScore,
                                                                 float entranceScore)
    {
        if (entranceScore >= cheeseScore && entranceScore >= spaghettiScore) {
            return CaveResolvedType.ENTRANCE;
        }
        if (spaghettiScore >= cheeseScore) {
            return CaveResolvedType.SPAGHETTI;
        }
        return CaveResolvedType.CHEESE;
    }
}
