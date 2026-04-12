package org.terraform.cave.v3.generation;

import org.jetbrains.annotations.NotNull;
import org.terraform.cave.v3.CaveResolvedType;
import org.terraform.cave.v3.CompositeVoxelSample;
import org.terraform.main.config.TConfig;

public final class DensityCompositeCaveSampler implements CompositeCaveSampler {
    private final @NotNull CaveFieldSampler cheeseDensitySampler;

    public DensityCompositeCaveSampler(@NotNull CaveFieldSampler cheeseDensitySampler) {
        this.cheeseDensitySampler = cheeseDensitySampler;
    }

    @Override
    public boolean canCarve(int rawX, int y, int rawZ, double baseSurfaceHeight) {
        return evaluate(rawX, y, rawZ, baseSurfaceHeight).finalScore() >= 0f;
    }

    @Override
    public @NotNull CompositeVoxelSample sampleDebug(int rawX, int y, int rawZ, double baseSurfaceHeight) {
        ReductionResult result = evaluate(rawX, y, rawZ, baseSurfaceHeight);
        float confidenceScale = Math.max(0.0001f, TConfig.c.CAVES_DENSITY_V1_CONFIDENCE_SCALE);
        float confidence = result.finalScore() <= NEGATIVE_INFINITY_SCORE / 2f
                           ? 0f
                           : DensityCarveRules.clamp01(Math.abs(result.finalScore()) / confidenceScale);
        return new CompositeVoxelSample(result.resolvedType(), result.finalScore(), confidence);
    }

    private float getCheeseLocalScore(float baseDensity) {
        return baseDensity - DensityCarveRules.getBaseThreshold();
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

    private @NotNull ReductionResult evaluate(int rawX, int y, int rawZ, double baseSurfaceHeight) {
        float rawCheese = cheeseDensitySampler.sampleDensity(rawX, y, rawZ, baseSurfaceHeight);
        float cheeseLocalScore = getCheeseLocalScore(rawCheese);
        float globalPenalty = DensityCarveRules.getGlobalPenalty(y, baseSurfaceHeight);
        float cheeseScore = applyStandardGlobalPenalty(y, baseSurfaceHeight, cheeseLocalScore, globalPenalty);
        return new ReductionResult(CaveResolvedType.CHEESE, cheeseScore);
    }

    private record ReductionResult(@NotNull CaveResolvedType resolvedType,
                                   float finalScore) {
    }
}
