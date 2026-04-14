package org.terraform.cave.v3.generation;

final class DensityFieldScore {
    private static final float SOFT_THRESHOLD_START = -0.15f;
    private static final float SOFT_THRESHOLD_RANGE = 0.15f;
    private static final float SOFT_THRESHOLD_BONUS = 0.08f;
    private static final float SOFT_THRESHOLD_MULTIPLIER = 1f + (SOFT_THRESHOLD_BONUS / SOFT_THRESHOLD_RANGE);

    private DensityFieldScore() {
    }

    static float toLocalScore(float baseDensity) {
        float score = baseDensity - DensityCarveRules.getBaseThreshold();

        // Keep the existing soft threshold band behavior unchanged.
        if (score > SOFT_THRESHOLD_START && score < 0f) {
            float t = (score - SOFT_THRESHOLD_START) / SOFT_THRESHOLD_RANGE;
            score += SOFT_THRESHOLD_BONUS * t;
        }

        return score;
    }

    static float minBaseDensityForLocalScore(float localScore) {
        if (Float.isNaN(localScore)) {
            return Float.NaN;
        }
        if (localScore == Float.NEGATIVE_INFINITY) {
            return Float.NEGATIVE_INFINITY;
        }
        if (localScore == Float.POSITIVE_INFINITY) {
            return Float.POSITIVE_INFINITY;
        }

        float requiredScore;
        if (localScore < SOFT_THRESHOLD_START) {
            requiredScore = localScore;
        }
        else if (localScore < SOFT_THRESHOLD_BONUS) {
            requiredScore = (localScore - SOFT_THRESHOLD_BONUS) / SOFT_THRESHOLD_MULTIPLIER;
        }
        else {
            requiredScore = localScore;
        }

        return makeBaseDensityThresholdSafe(localScore, requiredScore + DensityCarveRules.getBaseThreshold());
    }

    private static float makeBaseDensityThresholdSafe(float localScore, float requiredBaseDensity) {
        if (!Float.isFinite(localScore) || !Float.isFinite(requiredBaseDensity)) {
            return requiredBaseDensity;
        }

        float safeBaseDensity = requiredBaseDensity;
        for (int i = 0; i < 4 && toLocalScore(safeBaseDensity) < localScore; i++) {
            float next = Math.nextUp(safeBaseDensity);
            if (next == safeBaseDensity) {
                break;
            }
            safeBaseDensity = next;
        }
        return safeBaseDensity;
    }
}
