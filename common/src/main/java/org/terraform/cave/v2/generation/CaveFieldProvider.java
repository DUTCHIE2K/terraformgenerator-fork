package org.terraform.cave.v2.generation;

import org.jetbrains.annotations.NotNull;
import org.terraform.data.TerraformWorld;

public interface CaveFieldProvider {
    @NotNull CaveDensitySampler createSampler(@NotNull TerraformWorld tw);
}
