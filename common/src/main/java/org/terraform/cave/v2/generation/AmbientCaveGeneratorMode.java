package org.terraform.cave.v2.generation;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum AmbientCaveGeneratorMode {
    LEGACY,
    DENSITY_V1;

    public static @NotNull AmbientCaveGeneratorMode fromConfig(@NotNull String raw) {
        try {
            return valueOf(raw.toUpperCase(Locale.ENGLISH));
        }
        catch (IllegalArgumentException ex) {
            return LEGACY;
        }
    }
}
