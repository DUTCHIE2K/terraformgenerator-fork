package org.terraform.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.terraform.command.contants.TerraCommand;
import org.terraform.cave.v3.CaveV3Profiler;
import org.terraform.main.TerraformGeneratorPlugin;
import org.terraform.utils.TickTimer;

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
    public void execute(@NotNull CommandSender sender, Stack<String> args) {
        sender.sendMessage("=====Avg Timings=====");
        for (Map.Entry<String, Long> entry : TickTimer.TIMINGS.entrySet()) {
            sender.sendMessage(ChatColor.GRAY
                               + "- "
                               + ChatColor.GREEN
                               + entry.getKey()
                               + ChatColor.DARK_GRAY
                               + ": "
                               + ChatColor.GOLD
                               + entry.getValue());
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
}
