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
}
