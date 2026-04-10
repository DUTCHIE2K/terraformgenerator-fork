package org.terraform.cave.v3;

public record CompositeVoxelSample(CaveIntentType intentType,
                                   CaveResolvedType resolvedType,
                                   float finalScore,
                                   float confidence)
{
}
