package org.terraform.cave.v3.generation;

import org.jetbrains.annotations.NotNull;
import org.terraform.data.TerraformWorld;

public interface CaveFieldProvider {
    @NotNull CaveFieldSampler createSampler(@NotNull TerraformWorld tw);
}
