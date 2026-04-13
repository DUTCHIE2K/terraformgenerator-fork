package org.terraform.cave.v3.generation;

import org.jetbrains.annotations.NotNull;
import org.terraform.coregen.ChunkCache;
import org.terraform.coregen.bukkit.TerraformGenerator;
import org.terraform.data.TerraformWorld;
import org.terraform.main.TerraformGeneratorPlugin;
import org.terraform.main.config.TConfig;
import org.terraform.utils.noise.FastNoise;
import org.terraform.utils.noise.NoiseCacheHandler;

public final class DensityCarveRules {
    private DensityCarveRules() {}

    public static @NotNull ColumnContext createColumnContext(@NotNull TerraformWorld tw,
                                                             int rawX,
                                                             int rawZ,
                                                             double baseSurfaceHeight,
                                                             @NotNull ChunkCache cache)
    {
        int hardClearance = TConfig.c.CAVES_DENSITY_V1_SURFACE_NO_CARVE_CLEARANCE;
        int fullClearance = Math.max(hardClearance, TConfig.c.CAVES_DENSITY_V1_SURFACE_FULL_CARVE_CLEARANCE);
        double hardCeilingY = baseSurfaceHeight - hardClearance;
        double fullCarveY = baseSurfaceHeight - fullClearance;
        double inverseSurfaceFadeRange = fullClearance > hardClearance
                                         ? 1d / (hardCeilingY - fullCarveY)
                                         : 0d;

        int seaLevelFadeDepth = Math.max(0, TConfig.c.CAVES_DENSITY_V1_SEA_LEVEL_FADE_DEPTH);
        boolean seaLevelPenaltyEnabled = seaLevelFadeDepth > 0
                                         && baseSurfaceHeight <= TerraformGenerator.seaLevel
                                                                  + TConfig.c.CAVES_DENSITY_V1_SEA_LEVEL_COLUMN_BUFFER;
        int seaLevelLowerBound = TerraformGenerator.seaLevel - seaLevelFadeDepth;
        float inverseSeaLevelFadeDepth = seaLevelFadeDepth > 0 ? 1f / seaLevelFadeDepth : 0f;

        boolean bottomSealEnabled = TConfig.c.CAVES_DENSITY_V1_BOTTOM_SEAL_ENABLED;
        float bottomSealFloorY = ChunkCache.CHUNKCACHE_INVAL;
        int bottomSealFadeHeight = 0;
        if (bottomSealEnabled) {
            bottomSealFloorY = getBottomSealFloorY(tw, rawX, rawZ, cache);
            bottomSealFadeHeight = Math.max(0, TConfig.c.CAVES_DENSITY_V1_BOTTOM_SEAL_FADE_HEIGHT);
        }
        float bottomSealFadeTop = bottomSealFloorY + bottomSealFadeHeight;
        float inverseBottomSealFadeHeight = bottomSealFadeHeight > 0 ? 1f / bottomSealFadeHeight : 0f;

        return new ColumnContext(
                hardCeilingY,
                fullCarveY,
                inverseSurfaceFadeRange,
                TConfig.c.CAVES_DENSITY_V1_SURFACE_MAX_THRESHOLD_PENALTY,
                seaLevelPenaltyEnabled,
                seaLevelLowerBound,
                inverseSeaLevelFadeDepth,
                TConfig.c.CAVES_DENSITY_V1_SEA_LEVEL_MAX_THRESHOLD_PENALTY,
                bottomSealEnabled,
                bottomSealFloorY,
                bottomSealFadeTop,
                inverseBottomSealFadeHeight,
                TConfig.c.CAVES_DENSITY_V1_BOTTOM_SEAL_MAX_THRESHOLD_PENALTY
        );
    }

    public static float getBaseThreshold() {
        return TConfig.c.CAVES_DENSITY_V1_THRESHOLD;
    }

    public static float getCarveThreshold(int y, double baseSurfaceHeight) {
        return getBaseThreshold() + getGlobalPenalty(y, baseSurfaceHeight);
    }

    public static float getGlobalPenalty(int y, double baseSurfaceHeight) {
        return getSurfacePenalty(y, baseSurfaceHeight) + getSeaLevelPenalty(y, baseSurfaceHeight);
    }

    public static float getCarveThreshold(@NotNull TerraformWorld tw,
                                          int rawX,
                                          int y,
                                          int rawZ,
                                          double baseSurfaceHeight,
                                          @NotNull ChunkCache cache)
    {
        return getBaseThreshold() + getGlobalPenalty(tw, rawX, y, rawZ, baseSurfaceHeight, cache);
    }

    public static float getGlobalPenalty(@NotNull TerraformWorld tw,
                                         int rawX,
                                         int y,
                                         int rawZ,
                                         double baseSurfaceHeight,
                                         @NotNull ChunkCache cache)
    {
        return getGlobalPenalty(y, baseSurfaceHeight) + getBottomSealPenalty(tw, rawX, y, rawZ, cache);
    }

    public static boolean canCarveAtY(int y, double baseSurfaceHeight) {
        return y <= baseSurfaceHeight - TConfig.c.CAVES_DENSITY_V1_SURFACE_NO_CARVE_CLEARANCE;
    }

    public static boolean canCarveAtY(@NotNull TerraformWorld tw,
                                      int rawX,
                                      int y,
                                      int rawZ,
                                      double baseSurfaceHeight,
                                      @NotNull ChunkCache cache)
    {
        return canCarveAtY(y, baseSurfaceHeight) && !isBelowBottomSeal(tw, rawX, y, rawZ, cache);
    }

    public static float getSurfacePenalty(int y, double baseSurfaceHeight) {
        int hardClearance = TConfig.c.CAVES_DENSITY_V1_SURFACE_NO_CARVE_CLEARANCE;
        int fullClearance = Math.max(hardClearance, TConfig.c.CAVES_DENSITY_V1_SURFACE_FULL_CARVE_CLEARANCE);
        if (fullClearance <= hardClearance) {
            return 0f;
        }

        double hardCeiling = baseSurfaceHeight - hardClearance;
        double fullCarveY = baseSurfaceHeight - fullClearance;
        if (y <= fullCarveY) {
            return 0f;
        }

        float ratio = (float) ((y - fullCarveY) / (hardCeiling - fullCarveY));
        return TConfig.c.CAVES_DENSITY_V1_SURFACE_MAX_THRESHOLD_PENALTY * clamp01(ratio);
    }

    public static float getSeaLevelPenalty(int y, double baseSurfaceHeight) {
        if (baseSurfaceHeight > TerraformGenerator.seaLevel + TConfig.c.CAVES_DENSITY_V1_SEA_LEVEL_COLUMN_BUFFER) {
            return 0f;
        }

        int fadeDepth = TConfig.c.CAVES_DENSITY_V1_SEA_LEVEL_FADE_DEPTH;
        if (fadeDepth <= 0) {
            return 0f;
        }

        int lowerBound = TerraformGenerator.seaLevel - fadeDepth;
        if (y <= lowerBound) {
            return 0f;
        }

        float ratio = (float) (y - lowerBound) / fadeDepth;
        return TConfig.c.CAVES_DENSITY_V1_SEA_LEVEL_MAX_THRESHOLD_PENALTY * clamp01(ratio);
    }

    public static float getBottomSealPenalty(@NotNull TerraformWorld tw,
                                             int rawX,
                                             int y,
                                             int rawZ,
                                             @NotNull ChunkCache cache)
    {
        if (!TConfig.c.CAVES_DENSITY_V1_BOTTOM_SEAL_ENABLED) {
            return 0f;
        }

        int fadeHeight = Math.max(0, TConfig.c.CAVES_DENSITY_V1_BOTTOM_SEAL_FADE_HEIGHT);
        if (fadeHeight == 0) {
            return 0f;
        }

        float sealY = getBottomSealFloorY(tw, rawX, rawZ, cache);
        if (y <= sealY) {
            return 0f;
        }

        float fadeTop = sealY + fadeHeight;
        if (y >= fadeTop) {
            return 0f;
        }

        float ratio = 1f - ((y - sealY) / fadeHeight);
        return TConfig.c.CAVES_DENSITY_V1_BOTTOM_SEAL_MAX_THRESHOLD_PENALTY * clamp01(ratio);
    }

    public static boolean isBelowBottomSeal(@NotNull TerraformWorld tw,
                                            int rawX,
                                            int y,
                                            int rawZ,
                                            @NotNull ChunkCache cache)
    {
        if (!TConfig.c.CAVES_DENSITY_V1_BOTTOM_SEAL_ENABLED) {
            return false;
        }

        return y <= getBottomSealFloorY(tw, rawX, rawZ, cache);
    }

    public static float getBottomSealFloorY(@NotNull TerraformWorld tw,
                                            int rawX,
                                            int rawZ,
                                            @NotNull ChunkCache cache)
    {
        float cached = cache.getBottomSealY(rawX & 0xF, rawZ & 0xF);
        if (cached != ChunkCache.CHUNKCACHE_INVAL) {
            return cached;
        }

        FastNoise bottomSealNoise = NoiseCacheHandler.getNoise(
                tw,
                NoiseCacheHandler.NoiseCacheEntry.CAVE_V3_BOTTOM_SEAL_NOISE,
                world -> {
                    FastNoise n = new FastNoise((int) (world.getSeed() * 83L + 0x55E17));
                    n.SetNoiseType(FastNoise.NoiseType.SimplexFractal);
                    n.SetFrequency(Math.max(0.0001f, TConfig.c.CAVES_DENSITY_V1_BOTTOM_SEAL_FREQUENCY));
                    n.SetFractalOctaves(2);
                    return n;
                }
        );

        float normalizedNoise = 0.5f + (0.5f * bottomSealNoise.GetNoise(rawX, rawZ));
        int baseSealY = TerraformGeneratorPlugin.injector.getMinY()
                        + TConfig.c.HEIGHT_MAP_BEDROCK_HEIGHT
                        + Math.max(0, TConfig.c.CAVES_DENSITY_V1_BOTTOM_SEAL_MIN_COVER);
        float sealY = baseSealY + (normalizedNoise * Math.max(0, TConfig.c.CAVES_DENSITY_V1_BOTTOM_SEAL_NOISE_RISE));
        cache.cacheBottomSealY(rawX & 0xF, rawZ & 0xF, sealY);
        return sealY;
    }

    public static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    public static final class ColumnContext {
        private final double hardCeilingY;
        private final double fullCarveY;
        private final double inverseSurfaceFadeRange;
        private final float surfaceMaxPenalty;
        private final boolean seaLevelPenaltyEnabled;
        private final int seaLevelLowerBound;
        private final float inverseSeaLevelFadeDepth;
        private final float seaLevelMaxPenalty;
        private final boolean bottomSealEnabled;
        private final float bottomSealFloorY;
        private final float bottomSealFadeTop;
        private final float inverseBottomSealFadeHeight;
        private final float bottomSealMaxPenalty;

        private ColumnContext(double hardCeilingY,
                              double fullCarveY,
                              double inverseSurfaceFadeRange,
                              float surfaceMaxPenalty,
                              boolean seaLevelPenaltyEnabled,
                              int seaLevelLowerBound,
                              float inverseSeaLevelFadeDepth,
                              float seaLevelMaxPenalty,
                              boolean bottomSealEnabled,
                              float bottomSealFloorY,
                              float bottomSealFadeTop,
                              float inverseBottomSealFadeHeight,
                              float bottomSealMaxPenalty)
        {
            this.hardCeilingY = hardCeilingY;
            this.fullCarveY = fullCarveY;
            this.inverseSurfaceFadeRange = inverseSurfaceFadeRange;
            this.surfaceMaxPenalty = surfaceMaxPenalty;
            this.seaLevelPenaltyEnabled = seaLevelPenaltyEnabled;
            this.seaLevelLowerBound = seaLevelLowerBound;
            this.inverseSeaLevelFadeDepth = inverseSeaLevelFadeDepth;
            this.seaLevelMaxPenalty = seaLevelMaxPenalty;
            this.bottomSealEnabled = bottomSealEnabled;
            this.bottomSealFloorY = bottomSealFloorY;
            this.bottomSealFadeTop = bottomSealFadeTop;
            this.inverseBottomSealFadeHeight = inverseBottomSealFadeHeight;
            this.bottomSealMaxPenalty = bottomSealMaxPenalty;
        }

        public boolean canCarveAtY(int y) {
            return y <= hardCeilingY && (!bottomSealEnabled || y > bottomSealFloorY);
        }

        public float getGlobalPenalty(int y) {
            return getSurfacePenalty(y) + getSeaLevelPenalty(y) + getBottomSealPenalty(y);
        }

        private float getSurfacePenalty(int y) {
            if (inverseSurfaceFadeRange == 0d || y <= fullCarveY) {
                return 0f;
            }

            float ratio = (float) ((y - fullCarveY) * inverseSurfaceFadeRange);
            return surfaceMaxPenalty * clamp01(ratio);
        }

        private float getSeaLevelPenalty(int y) {
            if (!seaLevelPenaltyEnabled || y <= seaLevelLowerBound) {
                return 0f;
            }

            float ratio = (y - seaLevelLowerBound) * inverseSeaLevelFadeDepth;
            return seaLevelMaxPenalty * clamp01(ratio);
        }

        private float getBottomSealPenalty(int y) {
            if (!bottomSealEnabled
                || inverseBottomSealFadeHeight == 0f
                || y <= bottomSealFloorY
                || y >= bottomSealFadeTop)
            {
                return 0f;
            }

            float ratio = 1f - ((y - bottomSealFloorY) * inverseBottomSealFadeHeight);
            return bottomSealMaxPenalty * clamp01(ratio);
        }
    }
}
