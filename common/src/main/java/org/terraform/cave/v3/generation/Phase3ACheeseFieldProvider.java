package org.terraform.cave.v3.generation;

import org.jetbrains.annotations.NotNull;
import org.terraform.cave.v3.CaveV3Profiler;
import org.terraform.data.TerraformWorld;

public final class Phase3ACheeseFieldProvider implements CaveFieldProvider {
    private static final float MIN_FULL_CARVE_DEPTH = 2f;
    private static final float SURFACE_FADE_DEPTH = 8f;
    private static final float DEEP_BOOST_START = 18f;
    private static final float DEEP_BOOST_RANGE = 28f;
    private static final float BASE_DENSITY_OFFSET = -0.05f;
    private static final float CHAMBER_WEIGHT = 0.5f;

    @Override
    public @NotNull CaveFieldSampler createSampler(@NotNull TerraformWorld tw) {
        return new CaveFieldSampler() {
            @Override
            public float sampleLocalScore(@NotNull DensitySampleContext context) {
                try (CaveV3Profiler.Scope ignored = CaveV3Profiler.start("cave-v3.sample.field.cheese")) {
                    return getCheeseLocalScore(context);
                }
            }

            @Override
            public @NotNull String getProfilerKey() {
                return "cheese";
            }
        };
    }

    private static float getCheeseLocalScore(@NotNull DensitySampleContext context)
    {
        float nearSurfaceFade = DensityCarveRules.clamp01(
                (context.depthBelowSurface - MIN_FULL_CARVE_DEPTH) / SURFACE_FADE_DEPTH
        );
        float deepBoost = DensityCarveRules.clamp01((context.depthBelowSurface - DEEP_BOOST_START) / DEEP_BOOST_RANGE);

        float density = BASE_DENSITY_OFFSET + (context.chamber * CHAMBER_WEIGHT);
        density *= 0.75f + (nearSurfaceFade * 0.25f);
        density += deepBoost * 0.08f;
        return DensityFieldScore.toLocalScore(density);
    }
}
