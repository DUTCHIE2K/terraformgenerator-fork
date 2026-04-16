package org.terraform.cave.v3.generation;

import org.jetbrains.annotations.NotNull;
import org.terraform.data.TerraformWorld;
import org.terraform.utils.noise.FastNoise;

public final class Phase3ACheeseFieldProvider implements CaveFieldProvider {
    private static final @NotNull CheeseFieldModel DEFAULT_MODEL = CheeseFieldModel.createDefault();

    private final @NotNull CheeseFieldModel model;

    public Phase3ACheeseFieldProvider() {
        this(DEFAULT_MODEL);
    }

    Phase3ACheeseFieldProvider(@NotNull CheeseFieldModel model) {
        this.model = model;
    }

    @Override
    public @NotNull CaveFieldSampler createSampler(@NotNull TerraformWorld tw) {
        return model.createSampler();
    }

    @NotNull FastNoise createWarpNoise(@NotNull TerraformWorld tw, float baseFrequency) {
        return model.createWarpNoise(tw, baseFrequency);
    }

    @NotNull FastNoise createChamberNoise(@NotNull TerraformWorld tw, float baseFrequency) {
        return model.createChamberNoise(tw, baseFrequency);
    }

    @NotNull CheeseFieldModel.DomainWarp getDomainWarp() {
        return model.getDomainWarp();
    }
}
