package org.terraform.cave.v3;

public record EntranceApproval(EntranceAnchor anchor,
                               int ownerChunkX,
                               int ownerChunkZ,
                               int mouthRawX,
                               int mouthY,
                               int mouthRawZ,
                               int targetRawX,
                               int targetY,
                               int targetRawZ,
                               float mouthRadius,
                               float targetRadius,
                               int effectPadding)
{
}
