package org.terraform.cave.v3.generation;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum CompositeCaveGeneratorMode {
    LEGACY,
    COMPOSITE_V3;

    public static @NotNull CompositeCaveGeneratorMode fromConfig(@NotNull String raw) {
        String normalized = raw.toUpperCase(Locale.ENGLISH);
        return switch (normalized) {
            case "COMPOSITE_V3", "V3", "DENSITY_V1" -> COMPOSITE_V3;
            default -> LEGACY;
        };
    }
}
