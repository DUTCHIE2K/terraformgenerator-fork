package org.terraform.cave.v3;

public record CaveIntervalMetadata(SurfaceConnectivity openToSurface,
                                   CaveBiomeType caveBiome,
                                   CaveFluidState fluidState,
                                   CaveFamilyType primaryFamily,
                                   boolean largeCavity) {
    public CaveIntervalMetadata(SurfaceConnectivity openToSurface) {
        this(
                openToSurface,
                CaveBiomeType.NEUTRAL,
                CaveFluidState.DRY,
                CaveFamilyType.UNKNOWN,
                false
        );
    }

    public boolean isFlooded() {
        return fluidState.isWet();
    }

    public boolean supportsDecoration() {
        return openToSurface != SurfaceConnectivity.YES && !isFlooded();
    }

    public boolean supportsClusterDecoration() {
        return supportsDecoration() && largeCavity;
    }
}
