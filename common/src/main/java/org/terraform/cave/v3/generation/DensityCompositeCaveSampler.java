package org.terraform.cave.v3.generation;

import org.jetbrains.annotations.NotNull;
import org.terraform.coregen.ChunkCache;
import org.terraform.data.TerraformWorld;

public final class DensityCompositeCaveSampler implements CompositeCaveSampler {
    private static final float SOFT_THRESHOLD_START = -0.15f;
    private static final float SOFT_THRESHOLD_RANGE = 0.15f;
    private static final float SOFT_THRESHOLD_BONUS = 0.08f;

    private final @NotNull TerraformWorld tw;
    private final @NotNull CaveFieldSampler cheeseDensitySampler;

    public DensityCompositeCaveSampler(@NotNull TerraformWorld tw, @NotNull CaveFieldSampler cheeseDensitySampler) {
        this.tw = tw;
        this.cheeseDensitySampler = cheeseDensitySampler;
    }

    @Override
    public boolean canCarve(int rawX, int y, int rawZ, double baseSurfaceHeight, @NotNull ChunkCache cache) {
        DensityCarveRules.ColumnContext columnContext = DensityCarveRules.createColumnContext(
                tw,
                rawX,
                rawZ,
                baseSurfaceHeight,
                cache
        );
        return canCarve(rawX, y, rawZ, baseSurfaceHeight, columnContext);
    }

    @Override
    public @NotNull CompositeCaveColumnSampler createColumnSampler(int rawX,
                                                                   int rawZ,
                                                                   double baseSurfaceHeight,
                                                                   @NotNull ChunkCache cache)
    {
        DensityCarveRules.ColumnContext columnContext = DensityCarveRules.createColumnContext(
                tw,
                rawX,
                rawZ,
                baseSurfaceHeight,
                cache
        );
        return new DensityColumnSampler(rawX, rawZ, baseSurfaceHeight, columnContext);
    }

    private boolean canCarve(int rawX,
                             int y,
                             int rawZ,
                             double baseSurfaceHeight,
                             @NotNull DensityCarveRules.ColumnContext columnContext)
    {
        if (!columnContext.canCarveAtY(y)) {
            return false;
        }

        float rawCheese = cheeseDensitySampler.sampleDensity(rawX, y, rawZ, baseSurfaceHeight);
        float cheeseLocalScore = getCheeseLocalScore(rawCheese);
        if (cheeseLocalScore <= NEGATIVE_INFINITY_SCORE / 2f) {
            return false;
        }
        return cheeseLocalScore - columnContext.getGlobalPenalty(y) >= 0f;
    }

    private float getCheeseLocalScore(float baseDensity) {
        float score = baseDensity - DensityCarveRules.getBaseThreshold();

        // Soft threshold band (edge smoothing)
        if (score > SOFT_THRESHOLD_START && score < 0f) {
            float t = (score - SOFT_THRESHOLD_START) / SOFT_THRESHOLD_RANGE;
            score += SOFT_THRESHOLD_BONUS * t;
        }

        return score;
    }

    private final class DensityColumnSampler implements CompositeCaveColumnSampler {
        private final int rawX;
        private final int rawZ;
        private final double baseSurfaceHeight;
        private final @NotNull DensityCarveRules.ColumnContext columnContext;

        private DensityColumnSampler(int rawX,
                                     int rawZ,
                                     double baseSurfaceHeight,
                                     @NotNull DensityCarveRules.ColumnContext columnContext)
        {
            this.rawX = rawX;
            this.rawZ = rawZ;
            this.baseSurfaceHeight = baseSurfaceHeight;
            this.columnContext = columnContext;
        }

        @Override
        public boolean canCarve(int y) {
            return DensityCompositeCaveSampler.this.canCarve(rawX, y, rawZ, baseSurfaceHeight, columnContext);
        }
    }
}
