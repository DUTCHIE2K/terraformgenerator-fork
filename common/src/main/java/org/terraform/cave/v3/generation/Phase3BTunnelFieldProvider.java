package org.terraform.cave.v3.generation;

import org.jetbrains.annotations.NotNull;
import org.terraform.data.TerraformWorld;

public final class Phase3BTunnelFieldProvider implements CaveFieldProvider {
    private static final @NotNull TunnelFieldModel DEFAULT_MODEL = TunnelFieldModel.createDefault();

    private final @NotNull TunnelFieldModel model;

    public Phase3BTunnelFieldProvider() {
        this(DEFAULT_MODEL);
    }

    Phase3BTunnelFieldProvider(@NotNull TunnelFieldModel model) {
        this.model = model;
    }

    @Override
    public @NotNull CaveFieldSampler createSampler(@NotNull TerraformWorld tw) {
        return model.createSampler(tw);
    }
}
