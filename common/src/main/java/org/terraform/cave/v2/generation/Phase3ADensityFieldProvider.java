package org.terraform.cave.v2.generation;

import org.jetbrains.annotations.NotNull;
import org.terraform.data.TerraformWorld;
import org.terraform.main.config.TConfig;
import org.terraform.utils.noise.FastNoise;
import org.terraform.utils.noise.NoiseCacheHandler;

public final class Phase3ADensityFieldProvider implements CaveFieldProvider {
    @Override
    public @NotNull CaveDensitySampler createSampler(@NotNull TerraformWorld tw) {
        FastNoise noise = NoiseCacheHandler.getNoise(
                tw,
                NoiseCacheHandler.NoiseCacheEntry.CAVE_V2_PHASE3A_DENSITY,
                world -> {
                    FastNoise n = new FastNoise((int) (world.getSeed() * 31L + 17L));
                    n.SetNoiseType(FastNoise.NoiseType.Simplex);
                    n.SetFrequency(TConfig.c.CAVES_DENSITY_V1_FREQUENCY);
                    return n;
                }
        );
        return (rawX, y, rawZ, surfaceHeight) -> noise.GetNoise(rawX, y, rawZ);
    }
}
