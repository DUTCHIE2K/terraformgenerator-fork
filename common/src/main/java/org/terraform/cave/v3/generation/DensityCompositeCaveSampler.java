package org.terraform.cave.v3.generation;

import org.jetbrains.annotations.NotNull;
import org.terraform.coregen.ChunkCache;
import org.terraform.data.TerraformWorld;

public final class DensityCompositeCaveSampler implements CompositeCaveSampler {
    private final @NotNull TerraformWorld tw;
    private final @NotNull CaveFieldSampler cheeseDensitySampler;

    public DensityCompositeCaveSampler(@NotNull TerraformWorld tw, @NotNull CaveFieldSampler cheeseDensitySampler) {
        this.tw = tw;
        this.cheeseDensitySampler = cheeseDensitySampler;
    }

    @Override
    public boolean canCarve(int rawX, int y, int rawZ, double baseSurfaceHeight, @NotNull ChunkCache cache) {
        return evaluate(rawX, y, rawZ, baseSurfaceHeight, cache) >= 0f;
    }

    private float getCheeseLocalScore(float baseDensity) {
        float score = baseDensity - DensityCarveRules.getBaseThreshold();

        // Soft threshold band (edge smoothing)
        if (score > -0.08f && score < 0f) {
            float t = (score + 0.08f) / 0.08f; // 0 → 1
            score += 0.05f * t;
        }

        return score;
    }

    private float applyStandardGlobalPenalty(int rawX,
                                             int y,
                                             int rawZ,
                                             double baseSurfaceHeight,
                                             float localScore,
                                             @NotNull ChunkCache cache)
    {
        if (localScore <= NEGATIVE_INFINITY_SCORE / 2f) {
            return NEGATIVE_INFINITY_SCORE;
        }
        if (!DensityCarveRules.canCarveAtY(tw, rawX, y, rawZ, baseSurfaceHeight, cache)) {
            return NEGATIVE_INFINITY_SCORE;
        }
        return localScore - DensityCarveRules.getGlobalPenalty(tw, rawX, y, rawZ, baseSurfaceHeight, cache);
    }

    private float evaluate(int rawX, int y, int rawZ, double baseSurfaceHeight, @NotNull ChunkCache cache)
    {
        float rawCheese = cheeseDensitySampler.sampleDensity(rawX, y, rawZ, baseSurfaceHeight);
        float cheeseLocalScore = getCheeseLocalScore(rawCheese);
        return applyStandardGlobalPenalty(rawX,
                y,
                rawZ,
                baseSurfaceHeight,
                cheeseLocalScore,
                cache);
    }
}
