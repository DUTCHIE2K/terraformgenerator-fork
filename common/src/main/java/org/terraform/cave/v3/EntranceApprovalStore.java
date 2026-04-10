package org.terraform.cave.v3;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.terraform.data.TerraformWorld;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;

public final class EntranceApprovalStore {
    private static final ConcurrentHashMap<WorldEntranceAnchorKey, EntranceApproval> APPROVALS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<WorldEntranceAnchorKey, Boolean> REJECTED = new ConcurrentHashMap<>();

    private EntranceApprovalStore() {
    }

    public static void publish(@NotNull TerraformWorld tw, @NotNull EntranceApproval approval) {
        cacheApproval(tw, approval);
    }

    public static @Nullable EntranceApproval getApprovedEntrance(@NotNull TerraformWorld tw, @NotNull EntranceAnchor anchor) {
        WorldEntranceAnchorKey key = new WorldEntranceAnchorKey(tw, anchor);
        EntranceApproval cached = APPROVALS.get(key);
        if (cached != null) {
            return cached;
        }
        if (REJECTED.containsKey(key)) {
            return null;
        }

        EntranceApproval resolved = EntranceApprovalResolverV3.resolve(tw, anchor);
        if (resolved == null) {
            REJECTED.put(key, Boolean.TRUE);
            return null;
        }

        cacheApproval(tw, resolved);
        return resolved;
    }

    public static @NotNull Collection<EntranceApproval> getApprovedEntrancesTouchingChunk(@NotNull TerraformWorld tw,
                                                                                           int chunkX,
                                                                                           int chunkZ)
    {
        LinkedHashMap<EntranceAnchor, EntranceApproval> approvals = new LinkedHashMap<>();
        for (EntranceAnchor anchor : EntranceApprovalResolverV3.getCandidateAnchorsTouchingChunk(tw, chunkX, chunkZ)) {
            EntranceApproval approval = getApprovedEntrance(tw, anchor);
            if (approval != null && EntranceApprovalResolverV3.touchesChunk(approval, chunkX, chunkZ)) {
                approvals.put(approval.anchor(), approval);
            }
        }
        if (approvals.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(approvals.values()));
    }

    public static @NotNull Collection<EntranceApproval> traceApprovedEntrancesTouchingChunk(@NotNull TerraformWorld tw,
                                                                                             int chunkX,
                                                                                             int chunkZ,
                                                                                             @NotNull EntranceApprovalTrace trace)
    {
        LinkedHashMap<EntranceAnchor, EntranceApproval> approvals = new LinkedHashMap<>();
        for (EntranceAnchor anchor : EntranceApprovalResolverV3.getCandidateAnchorsTouchingChunk(tw, chunkX, chunkZ)) {
            trace.recordRawSeed();
            EntranceApproval approval = EntranceApprovalResolverV3.resolve(tw, anchor, trace);
            if (approval != null) {
                cacheApproval(tw, approval);
                if (EntranceApprovalResolverV3.touchesChunk(approval, chunkX, chunkZ)) {
                    approvals.put(approval.anchor(), approval);
                    trace.recordAccepted(approval);
                }
            }
        }
        if (approvals.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(approvals.values()));
    }

    public static void clearWorld(@NotNull TerraformWorld tw) {
        APPROVALS.keySet().removeIf(key -> key.tw().getName().equals(tw.getName()));
        REJECTED.keySet().removeIf(key -> key.tw().getName().equals(tw.getName()));
    }

    private static void cacheApproval(@NotNull TerraformWorld tw, @NotNull EntranceApproval approval) {
        WorldEntranceAnchorKey key = new WorldEntranceAnchorKey(tw, approval.anchor());
        APPROVALS.put(key, approval);
        REJECTED.remove(key);
    }

    private record WorldEntranceAnchorKey(@NotNull TerraformWorld tw, @NotNull EntranceAnchor anchor) {
    }
}
