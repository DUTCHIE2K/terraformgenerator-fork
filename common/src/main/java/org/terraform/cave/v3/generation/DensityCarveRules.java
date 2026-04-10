package org.terraform.cave.v3.generation;

import org.terraform.coregen.bukkit.TerraformGenerator;
import org.terraform.main.config.TConfig;

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

    public static boolean canCarveAtY(int y, double baseSurfaceHeight) {
        return y <= baseSurfaceHeight - TConfig.c.CAVES_DENSITY_V1_SURFACE_NO_CARVE_CLEARANCE;
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

    public static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
