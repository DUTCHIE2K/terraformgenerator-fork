package org.terraform.cave.v3;

public record CaveIntervalMetadata(CaveResolvedType resolvedType, float confidence, SurfaceConnectivity openToSurface) {
    public CaveIntervalMetadata {
        confidence = Math.max(0f, Math.min(1f, confidence));
    }
}
