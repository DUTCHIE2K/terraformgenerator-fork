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
