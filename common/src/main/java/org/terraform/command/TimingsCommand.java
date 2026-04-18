package org.terraform.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.terraform.command.contants.TerraCommand;
import org.terraform.cave.v3.CaveV3Profiler;
import org.terraform.main.TerraformGeneratorPlugin;
import org.terraform.utils.TickTimer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;

public class TimingsCommand extends TerraCommand {
    public TimingsCommand(TerraformGeneratorPlugin plugin, String... aliases) {
        super(plugin, aliases);
    }

    @Override
    public @NotNull String getDefaultDescription() {
        return "Shows timings of monitored functions";
    }

    @Override
    public boolean canConsoleExec() {
        return true;
    }

    @Override
    public boolean hasPermission(@NotNull CommandSender sender) {
        return sender.isOp();
    }

    @Override
    public boolean isInAcceptedParamRange(@NotNull Stack<String> args) {
        return args.size() <= 1;
    }

    @Override
    public void execute(@NotNull CommandSender sender, Stack<String> args) {
        String mode = args.isEmpty() ? "" : args.pop().toLowerCase(Locale.ROOT);
        if ("paste".equals(mode) || "raw".equals(mode) || "copy".equals(mode)) {
            emitPasteSummary(sender);
            return;
        }

        sender.sendMessage("=====Avg Timings=====");
        for (String line : buildAverageTimingLines(true)) {
            sender.sendMessage(line);
        }

        sender.sendMessage("=====Cave V3 Profiling=====");
        if (!CaveV3Profiler.isEnabled()) {
            sender.sendMessage(ChatColor.DARK_GRAY + "Disabled. Set dev-stuff.cave-v3-profile to true.");
            return;
        }

        if (!CaveV3Profiler.hasSamples()) {
            sender.sendMessage(ChatColor.DARK_GRAY + "No samples recorded yet.");
            return;
        }

        Map<String, CaveV3Profiler.SectionSnapshot> snapshotsByKey = new HashMap<>();
        for (CaveV3Profiler.SectionSnapshot snapshot : CaveV3Profiler.snapshot()) {
            snapshotsByKey.put(snapshot.key(), snapshot);
        }

        long voxelsConsidered = getCalls(snapshotsByKey, "cave-v3.sample.voxel-considered");
        if (voxelsConsidered > 0L) {
            sender.sendMessage(ChatColor.GRAY + "Field Eval Summary:");
            sender.sendMessage(formatRatioLine(
                    "cheese early-accept",
                    getCalls(snapshotsByKey, "cave-v3.sample.field.cheese.early-accept"),
                    voxelsConsidered
            ));
            sender.sendMessage(formatRatioLine(
                    "tunnel pruned",
                    getCalls(snapshotsByKey, "cave-v3.sample.field.tunnel.pruned"),
                    voxelsConsidered
            ));
            long tunnelPrunedByBest = getCalls(snapshotsByKey, "cave-v3.sample.field.tunnel.pruned-by-best");
            if (tunnelPrunedByBest > 0L) {
                sender.sendMessage(formatRatioLine(
                        "tunnel pruned by best",
                        tunnelPrunedByBest,
                        voxelsConsidered
                ));
            }
            long tunnelPrunedByPenalty = getCalls(snapshotsByKey, "cave-v3.sample.field.tunnel.pruned-by-penalty");
            if (tunnelPrunedByPenalty > 0L) {
                sender.sendMessage(formatRatioLine(
                        "tunnel pruned by penalty",
                        tunnelPrunedByPenalty,
                        voxelsConsidered
                ));
            }
            long tunnelPreSupportPruned = getCalls(
                    snapshotsByKey,
                    "cave-v3.sample.field.tunnel.probe.pre-support-pruned"
            );
            if (tunnelPreSupportPruned > 0L) {
                sender.sendMessage(formatRatioLine(
                        "tunnel pre-support pruned",
                        tunnelPreSupportPruned,
                        voxelsConsidered
                ));
            }
            long tunnelContinuityPruned = getCalls(
                    snapshotsByKey,
                    "cave-v3.sample.field.tunnel.probe.continuity-pruned"
            );
            if (tunnelContinuityPruned > 0L) {
                sender.sendMessage(formatRatioLine(
                        "tunnel continuity pruned",
                        tunnelContinuityPruned,
                        voxelsConsidered
                ));
            }
            sender.sendMessage(formatRatioLine(
                    "tunnel full sample",
                    getCalls(snapshotsByKey, "cave-v3.sample.field.tunnel.full"),
                    voxelsConsidered
            ));

            long tunnelBranchPruned = getCalls(snapshotsByKey, "cave-v3.sample.field.tunnel.branch-pruned");
            long tunnelBranchEntered = getCalls(snapshotsByKey, "cave-v3.sample.field.tunnel.branch-entered");
            long tunnelBranchTotal = tunnelBranchPruned + tunnelBranchEntered;
            if (tunnelBranchTotal > 0L) {
                sender.sendMessage(formatRatioLine(
                        "tunnel branch pruned",
                        tunnelBranchPruned,
                        tunnelBranchTotal
                ));
            }

            long tunnelClearancePruned = getCalls(snapshotsByKey, "cave-v3.sample.field.tunnel.clearance-pruned");
            long tunnelClearanceEntered = getCalls(snapshotsByKey, "cave-v3.sample.field.tunnel.clearance-entered");
            long tunnelClearanceTotal = tunnelClearancePruned + tunnelClearanceEntered;
            if (tunnelClearanceTotal > 0L) {
                sender.sendMessage(formatRatioLine(
                        "tunnel clearance pruned",
                        tunnelClearancePruned,
                        tunnelClearanceTotal
                ));
            }

            long tunnelFullSamples = getCalls(snapshotsByKey, "cave-v3.sample.field.tunnel.full");
            if (tunnelFullSamples > 0L) {
                sender.sendMessage(formatRatioLine(
                        "tunnel full 5-clearance stack",
                        getCalls(snapshotsByKey, "cave-v3.sample.field.tunnel.full-stack"),
                        tunnelFullSamples
                ));
                long tunnelFullThresholdHit = getCalls(snapshotsByKey, "cave-v3.sample.field.tunnel.full-threshold-hit");
                if (tunnelFullThresholdHit > 0L) {
                    sender.sendMessage(formatRatioLine(
                            "tunnel full threshold hit",
                            tunnelFullThresholdHit,
                            tunnelFullSamples
                    ));
                }
                long tunnelFullThresholdFalsePositive = getCalls(
                        snapshotsByKey,
                        "cave-v3.sample.field.tunnel.full-threshold-false-positive"
                );
                if (tunnelFullThresholdFalsePositive > 0L) {
                    sender.sendMessage(formatRatioLine(
                            "tunnel full threshold false-positive",
                            tunnelFullThresholdFalsePositive,
                            tunnelFullSamples
                    ));
                }
            }
        }

        if (hasPrefillSummary(snapshotsByKey)) {
            sender.sendMessage(ChatColor.GRAY + "Prefill Summary:");
            emitPrefillSummary(sender, snapshotsByKey);
        }

        for (CaveV3Profiler.SectionSnapshot snapshot : CaveV3Profiler.snapshot()) {
            String value = snapshot.isTimed()
                           ? String.format(
                                    Locale.ROOT,
                                   "calls=%d total=%.2fms avg=%.3fms max=%.3fms",
                                   snapshot.calls(),
                                   snapshot.totalMillis(),
                                   snapshot.averageMillis(),
                                   snapshot.maxMillis()
                           )
                           : String.format(Locale.ROOT, "calls=%d", snapshot.calls());
            sender.sendMessage(ChatColor.GRAY
                               + "- "
                               + ChatColor.GREEN
                               + snapshot.key()
                               + ChatColor.DARK_GRAY
                               + ": "
                + ChatColor.GOLD
                + value);
        }
    }

    private void emitPasteSummary(@NotNull CommandSender sender) {
        List<String> lines = buildPlainTextSummary();
        sender.sendMessage("Plain timings summary written below and mirrored to console.");
        sender.sendMessage("Copy the lines between BEGIN/END TERRAFORM TIMINGS.");
        for (String line : lines) {
            sender.sendMessage(line);
        }

        Bukkit.getConsoleSender().sendMessage("===== BEGIN TERRAFORM TIMINGS =====");
        for (String line : lines) {
            Bukkit.getConsoleSender().sendMessage(line);
        }
        Bukkit.getConsoleSender().sendMessage("===== END TERRAFORM TIMINGS =====");
    }

    private @NotNull List<String> buildPlainTextSummary() {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("BEGIN TERRAFORM TIMINGS");
        lines.add("[avg-timings]");
        lines.addAll(buildAverageTimingLines(false));

        lines.add("[cave-v3]");
        if (!CaveV3Profiler.isEnabled()) {
            lines.add("disabled=true");
            lines.add("hint=set dev-stuff.cave-v3-profile to true");
            lines.add("END TERRAFORM TIMINGS");
            return lines;
        }

        if (!CaveV3Profiler.hasSamples()) {
            lines.add("samples=0");
            lines.add("END TERRAFORM TIMINGS");
            return lines;
        }

        Map<String, CaveV3Profiler.SectionSnapshot> snapshotsByKey = new HashMap<>();
        for (CaveV3Profiler.SectionSnapshot snapshot : CaveV3Profiler.snapshot()) {
            snapshotsByKey.put(snapshot.key(), snapshot);
        }

        long voxelsConsidered = getCalls(snapshotsByKey, "cave-v3.sample.voxel-considered");
        if (voxelsConsidered > 0L) {
            lines.add("[field-eval-summary]");
            lines.add(formatPlainRatioLine(
                    "cheese_early_accept",
                    getCalls(snapshotsByKey, "cave-v3.sample.field.cheese.early-accept"),
                    voxelsConsidered
            ));
            lines.add(formatPlainRatioLine(
                    "tunnel_pruned",
                    getCalls(snapshotsByKey, "cave-v3.sample.field.tunnel.pruned"),
                    voxelsConsidered
            ));
            long tunnelPrunedByBest = getCalls(snapshotsByKey, "cave-v3.sample.field.tunnel.pruned-by-best");
            if (tunnelPrunedByBest > 0L) {
                lines.add(formatPlainRatioLine(
                        "tunnel_pruned_by_best",
                        tunnelPrunedByBest,
                        voxelsConsidered
                ));
            }
            long tunnelPrunedByPenalty = getCalls(snapshotsByKey, "cave-v3.sample.field.tunnel.pruned-by-penalty");
            if (tunnelPrunedByPenalty > 0L) {
                lines.add(formatPlainRatioLine(
                        "tunnel_pruned_by_penalty",
                        tunnelPrunedByPenalty,
                        voxelsConsidered
                ));
            }
            long tunnelPreSupportPruned = getCalls(
                    snapshotsByKey,
                    "cave-v3.sample.field.tunnel.probe.pre-support-pruned"
            );
            if (tunnelPreSupportPruned > 0L) {
                lines.add(formatPlainRatioLine(
                        "tunnel_pre_support_pruned",
                        tunnelPreSupportPruned,
                        voxelsConsidered
                ));
            }
            long tunnelContinuityPruned = getCalls(
                    snapshotsByKey,
                    "cave-v3.sample.field.tunnel.probe.continuity-pruned"
            );
            if (tunnelContinuityPruned > 0L) {
                lines.add(formatPlainRatioLine(
                        "tunnel_continuity_pruned",
                        tunnelContinuityPruned,
                        voxelsConsidered
                ));
            }
            lines.add(formatPlainRatioLine(
                    "tunnel_full_sample",
                    getCalls(snapshotsByKey, "cave-v3.sample.field.tunnel.full"),
                    voxelsConsidered
            ));

            long tunnelBranchPruned = getCalls(snapshotsByKey, "cave-v3.sample.field.tunnel.branch-pruned");
            long tunnelBranchEntered = getCalls(snapshotsByKey, "cave-v3.sample.field.tunnel.branch-entered");
            long tunnelBranchTotal = tunnelBranchPruned + tunnelBranchEntered;
            if (tunnelBranchTotal > 0L) {
                lines.add(formatPlainRatioLine(
                        "tunnel_branch_pruned",
                        tunnelBranchPruned,
                        tunnelBranchTotal
                ));
            }

            long tunnelClearancePruned = getCalls(snapshotsByKey, "cave-v3.sample.field.tunnel.clearance-pruned");
            long tunnelClearanceEntered = getCalls(snapshotsByKey, "cave-v3.sample.field.tunnel.clearance-entered");
            long tunnelClearanceTotal = tunnelClearancePruned + tunnelClearanceEntered;
            if (tunnelClearanceTotal > 0L) {
                lines.add(formatPlainRatioLine(
                        "tunnel_clearance_pruned",
                        tunnelClearancePruned,
                        tunnelClearanceTotal
                ));
            }

            long tunnelFullSamples = getCalls(snapshotsByKey, "cave-v3.sample.field.tunnel.full");
            if (tunnelFullSamples > 0L) {
                lines.add(formatPlainRatioLine(
                        "tunnel_full_5_clearance_stack",
                        getCalls(snapshotsByKey, "cave-v3.sample.field.tunnel.full-stack"),
                        tunnelFullSamples
                ));
                long tunnelFullThresholdHit = getCalls(snapshotsByKey, "cave-v3.sample.field.tunnel.full-threshold-hit");
                if (tunnelFullThresholdHit > 0L) {
                    lines.add(formatPlainRatioLine(
                            "tunnel_full_threshold_hit",
                            tunnelFullThresholdHit,
                            tunnelFullSamples
                    ));
                }
                long tunnelFullThresholdFalsePositive = getCalls(
                        snapshotsByKey,
                        "cave-v3.sample.field.tunnel.full-threshold-false-positive"
                );
                if (tunnelFullThresholdFalsePositive > 0L) {
                    lines.add(formatPlainRatioLine(
                            "tunnel_full_threshold_false_positive",
                            tunnelFullThresholdFalsePositive,
                            tunnelFullSamples
                    ));
                }
            }
        }

        if (hasPrefillSummary(snapshotsByKey)) {
            lines.add("[prefill-summary]");
            appendPlainPrefillSummary(lines, snapshotsByKey);
        }

        lines.add("[cave-v3-detail]");
        for (CaveV3Profiler.SectionSnapshot snapshot : CaveV3Profiler.snapshot()) {
            lines.add(formatPlainSnapshotLine(snapshot));
        }
        lines.add("END TERRAFORM TIMINGS");
        return lines;
    }

    private static @NotNull List<String> buildAverageTimingLines(boolean colorized) {
        ArrayList<String> lines = new ArrayList<>(TickTimer.TIMINGS.size());
        for (Map.Entry<String, Long> entry : TickTimer.TIMINGS.entrySet()) {
            if (colorized) {
                lines.add(ChatColor.GRAY
                          + "- "
                          + ChatColor.GREEN
                          + entry.getKey()
                          + ChatColor.DARK_GRAY
                          + ": "
                          + ChatColor.GOLD
                          + entry.getValue());
            }
            else {
                lines.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        return lines;
    }

    private static long getCalls(@NotNull Map<String, CaveV3Profiler.SectionSnapshot> snapshotsByKey, @NotNull String key) {
        CaveV3Profiler.SectionSnapshot snapshot = snapshotsByKey.get(key);
        return snapshot == null ? 0L : snapshot.calls();
    }

    private static boolean hasPrefillSummary(@NotNull Map<String, CaveV3Profiler.SectionSnapshot> snapshotsByKey) {
        return getCalls(snapshotsByKey, "cave-v3.generate-noise.prefilled-full-column-hit") > 0L
               || getCalls(snapshotsByKey, "cave-v3.generate-noise.prefilled-full-column-miss") > 0L
               || getCalls(snapshotsByKey, "cave-v3.generate-noise.prefilled-full-column-sync-built") > 0L
               || getCalls(snapshotsByKey, "cave-v3.generate-noise.prefilled-snapshot-hit") > 0L
               || getCalls(snapshotsByKey, "cave-v3.generate-noise.prefill-air-voxels-reused") > 0L
               || getCalls(snapshotsByKey, "cave-v3.prefill-neighbor.full-column-scheduled") > 0L
               || getCalls(snapshotsByKey, "cave-v3.prefill-neighbor.surface-scheduled") > 0L
               || getCalls(snapshotsByKey, "cave-v3.build-filled-cache.voxels-skipped-after-cutoff") > 0L;
    }

    private static void emitPrefillSummary(@NotNull CommandSender sender,
                                           @NotNull Map<String, CaveV3Profiler.SectionSnapshot> snapshotsByKey)
    {
        long fullColumnHits = getCalls(snapshotsByKey, "cave-v3.generate-noise.prefilled-full-column-hit");
        long fullColumnMisses = getCalls(snapshotsByKey, "cave-v3.generate-noise.prefilled-full-column-miss");
        long fullColumnRequests = fullColumnHits + fullColumnMisses;
        if (fullColumnRequests > 0L) {
            sender.sendMessage(formatRatioLine(
                    "full-column request hit",
                    fullColumnHits,
                    fullColumnRequests
            ));
            sender.sendMessage(formatRatioLine(
                    "full-column request miss",
                    fullColumnMisses,
                    fullColumnRequests
            ));
        }

        emitValueLineIfPositive(
                sender,
                "full-column sync-built",
                getCalls(snapshotsByKey, "cave-v3.generate-noise.prefilled-full-column-sync-built")
        );
        emitValueLineIfPositive(
                sender,
                "prefilled snapshot hit",
                getCalls(snapshotsByKey, "cave-v3.generate-noise.prefilled-snapshot-hit")
        );
        emitValueLineIfPositive(
                sender,
                "prefill air voxels reused",
                getCalls(snapshotsByKey, "cave-v3.generate-noise.prefill-air-voxels-reused")
        );
        emitValueLineIfPositive(
                sender,
                "buildFilledCache voxels skipped after cutoff",
                getCalls(snapshotsByKey, "cave-v3.build-filled-cache.voxels-skipped-after-cutoff")
        );

        emitValueLineIfPositive(
                sender,
                "neighbor full-column scheduled",
                getCalls(snapshotsByKey, "cave-v3.prefill-neighbor.full-column-scheduled")
        );
        emitValueLineIfPositive(
                sender,
                "neighbor full-column completed",
                getCalls(snapshotsByKey, "cave-v3.prefill-neighbor.full-column-completed")
        );
        emitValueLineIfPositive(
                sender,
                "neighbor full-column upgraded surface",
                getCalls(snapshotsByKey, "cave-v3.prefill-neighbor.full-column-upgraded-surface")
        );
        emitValueLineIfPositive(
                sender,
                "neighbor full-column upgrade executed",
                getCalls(snapshotsByKey, "cave-v3.prefill-neighbor.full-column-upgrade-executed")
        );
        emitValueLineIfPositive(
                sender,
                "neighbor full-column upgrade cap missed",
                getCalls(snapshotsByKey, "cave-v3.prefill-neighbor.full-column-upgrade-cap-missed")
        );
        emitValueLineIfPositive(
                sender,
                "neighbor full-column downgraded cap",
                getCalls(snapshotsByKey, "cave-v3.prefill-neighbor.full-column-downgraded-cap")
        );
        emitValueLineIfPositive(
                sender,
                "neighbor full-column downgraded stale",
                getCalls(snapshotsByKey, "cave-v3.prefill-neighbor.full-column-downgraded-stale")
        );
        emitValueLineIfPositive(
                sender,
                "neighbor full-column accepted history",
                getCalls(snapshotsByKey, "cave-v3.prefill-neighbor.full-column-accepted-history")
        );
        emitValueLineIfPositive(
                sender,
                "neighbor surface scheduled",
                getCalls(snapshotsByKey, "cave-v3.prefill-neighbor.surface-scheduled")
        );
        emitValueLineIfPositive(
                sender,
                "neighbor surface completed",
                getCalls(snapshotsByKey, "cave-v3.prefill-neighbor.surface-completed")
        );
        emitValueLineIfPositive(
                sender,
                "neighbor surface cached",
                getCalls(snapshotsByKey, "cave-v3.prefill-neighbor.skip-surface-cached")
        );
    }

    private static void appendPlainPrefillSummary(@NotNull List<String> lines,
                                                  @NotNull Map<String, CaveV3Profiler.SectionSnapshot> snapshotsByKey)
    {
        long fullColumnHits = getCalls(snapshotsByKey, "cave-v3.generate-noise.prefilled-full-column-hit");
        long fullColumnMisses = getCalls(snapshotsByKey, "cave-v3.generate-noise.prefilled-full-column-miss");
        long fullColumnRequests = fullColumnHits + fullColumnMisses;
        if (fullColumnRequests > 0L) {
            lines.add(formatPlainRatioLine(
                    "full_column_request_hit",
                    fullColumnHits,
                    fullColumnRequests
            ));
            lines.add(formatPlainRatioLine(
                    "full_column_request_miss",
                    fullColumnMisses,
                    fullColumnRequests
            ));
        }

        appendPlainValueLineIfPositive(
                lines,
                "full_column_sync_built",
                getCalls(snapshotsByKey, "cave-v3.generate-noise.prefilled-full-column-sync-built")
        );
        appendPlainValueLineIfPositive(
                lines,
                "prefilled_snapshot_hit",
                getCalls(snapshotsByKey, "cave-v3.generate-noise.prefilled-snapshot-hit")
        );
        appendPlainValueLineIfPositive(
                lines,
                "prefill_air_voxels_reused",
                getCalls(snapshotsByKey, "cave-v3.generate-noise.prefill-air-voxels-reused")
        );
        appendPlainValueLineIfPositive(
                lines,
                "build_filled_cache_voxels_skipped_after_cutoff",
                getCalls(snapshotsByKey, "cave-v3.build-filled-cache.voxels-skipped-after-cutoff")
        );

        appendPlainValueLineIfPositive(
                lines,
                "neighbor_full_column_scheduled",
                getCalls(snapshotsByKey, "cave-v3.prefill-neighbor.full-column-scheduled")
        );
        appendPlainValueLineIfPositive(
                lines,
                "neighbor_full_column_completed",
                getCalls(snapshotsByKey, "cave-v3.prefill-neighbor.full-column-completed")
        );
        appendPlainValueLineIfPositive(
                lines,
                "neighbor_full_column_upgraded_surface",
                getCalls(snapshotsByKey, "cave-v3.prefill-neighbor.full-column-upgraded-surface")
        );
        appendPlainValueLineIfPositive(
                lines,
                "neighbor_full_column_upgrade_executed",
                getCalls(snapshotsByKey, "cave-v3.prefill-neighbor.full-column-upgrade-executed")
        );
        appendPlainValueLineIfPositive(
                lines,
                "neighbor_full_column_upgrade_cap_missed",
                getCalls(snapshotsByKey, "cave-v3.prefill-neighbor.full-column-upgrade-cap-missed")
        );
        appendPlainValueLineIfPositive(
                lines,
                "neighbor_full_column_downgraded_cap",
                getCalls(snapshotsByKey, "cave-v3.prefill-neighbor.full-column-downgraded-cap")
        );
        appendPlainValueLineIfPositive(
                lines,
                "neighbor_full_column_downgraded_stale",
                getCalls(snapshotsByKey, "cave-v3.prefill-neighbor.full-column-downgraded-stale")
        );
        appendPlainValueLineIfPositive(
                lines,
                "neighbor_full_column_accepted_history",
                getCalls(snapshotsByKey, "cave-v3.prefill-neighbor.full-column-accepted-history")
        );
        appendPlainValueLineIfPositive(
                lines,
                "neighbor_surface_scheduled",
                getCalls(snapshotsByKey, "cave-v3.prefill-neighbor.surface-scheduled")
        );
        appendPlainValueLineIfPositive(
                lines,
                "neighbor_surface_completed",
                getCalls(snapshotsByKey, "cave-v3.prefill-neighbor.surface-completed")
        );
        appendPlainValueLineIfPositive(
                lines,
                "neighbor_surface_cached",
                getCalls(snapshotsByKey, "cave-v3.prefill-neighbor.skip-surface-cached")
        );
    }

    private static void emitValueLineIfPositive(@NotNull CommandSender sender, @NotNull String label, long value) {
        if (value > 0L) {
            sender.sendMessage(formatValueLine(label, value));
        }
    }

    private static void appendPlainValueLineIfPositive(@NotNull List<String> lines,
                                                       @NotNull String label,
                                                       long value)
    {
        if (value > 0L) {
            lines.add(formatPlainValueLine(label, value));
        }
    }

    private static @NotNull String formatRatioLine(@NotNull String label, long count, long total) {
        double percentage = total <= 0L ? 0d : (100d * count) / total;
        return ChatColor.DARK_GRAY
               + "- "
               + ChatColor.GREEN
               + label
               + ChatColor.DARK_GRAY
               + ": "
               + ChatColor.GOLD
               + String.format(Locale.ROOT, "%d / %d (%.1f%%)", count, total, percentage);
    }

    private static @NotNull String formatPlainRatioLine(@NotNull String label, long count, long total) {
        double percentage = total <= 0L ? 0d : (100d * count) / total;
        return String.format(Locale.ROOT, "%s=%d/%d (%.1f%%)", label, count, total, percentage);
    }

    private static @NotNull String formatValueLine(@NotNull String label, long value) {
        return ChatColor.DARK_GRAY
               + "- "
               + ChatColor.GREEN
               + label
               + ChatColor.DARK_GRAY
               + ": "
               + ChatColor.GOLD
               + value;
    }

    private static @NotNull String formatPlainValueLine(@NotNull String label, long value) {
        return label + "=" + value;
    }

    private static @NotNull String formatPlainSnapshotLine(@NotNull CaveV3Profiler.SectionSnapshot snapshot) {
        if (snapshot.isTimed()) {
            return String.format(
                    Locale.ROOT,
                    "%s: calls=%d total=%.2fms avg=%.3fms max=%.3fms",
                    snapshot.key(),
                    snapshot.calls(),
                    snapshot.totalMillis(),
                    snapshot.averageMillis(),
                    snapshot.maxMillis()
            );
        }
        return String.format(Locale.ROOT, "%s: calls=%d", snapshot.key(), snapshot.calls());
    }
}
