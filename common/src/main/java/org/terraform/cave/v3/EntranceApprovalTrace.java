package org.terraform.cave.v3;

import org.jetbrains.annotations.NotNull;

public interface EntranceApprovalTrace {
    void recordRawSeed();

    void recordLocalSearchCandidate();

    void recordSafeSurfacePass();

    void recordSlopePass();

    void recordTargetPass();

    void recordAccepted(@NotNull EntranceApproval approval);
}
