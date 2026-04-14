package org.terraform.cave.v3.generation;

import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface CaveFieldSampler {
    float sampleLocalScore(@NotNull DensitySampleContext context);

    default float sampleLocalScore(@NotNull DensitySampleContext context, float minRelevantLocalScore) {
        return sampleLocalScore(context);
    }

    default float sampleLocalScoreIfRelevant(@NotNull DensitySampleContext context, float minRelevantLocalScore) {
        if (!canBeatLocalScore(context, minRelevantLocalScore)) {
            return Float.NEGATIVE_INFINITY;
        }
        return sampleLocalScore(context, minRelevantLocalScore);
    }

    default @NotNull String getProfilerKey() {
        return "unknown";
    }

    default boolean canBeatLocalScore(@NotNull DensitySampleContext context, float minRelevantLocalScore) {
        return getUpperBoundLocalScore(context) >= minRelevantLocalScore;
    }

    default float getUpperBoundLocalScore(@NotNull DensitySampleContext context) {
        return Float.POSITIVE_INFINITY;
    }
}
