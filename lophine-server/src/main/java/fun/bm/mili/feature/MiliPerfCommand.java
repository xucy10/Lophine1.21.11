package fun.bm.mili.feature;

import fun.bm.mili.perf.MiliAffinityAutoTuner;
import fun.bm.mili.perf.MiliRegionLoadMonitor;
import fun.bm.mili.perf.MiliTickProfiler;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.openhft.affinity.Affinity;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.command.CommandContext;
import org.leavesmc.leaves.command.RootNode;

import java.util.BitSet;

/**
 * mili - /lophine-perf command.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>{@code /lophine-perf status} - shows current settings for all
 *       Lophine performance modules.</li>
 *   <li>{@code /lophine-perf affinity} - shows the resolved CPU
 *       affinity bitset that tick threads are pinned to, plus the
 *       bitmask of the current thread (so you can confirm pinning is
 *       active).</li>
 *   <li>{@code /lophine-perf regions} - dumps the latest region load
 *       monitor summary (top 20 regions by EMA load score).</li>
 *   <li>{@code /lophine-perf profiler} - dumps the current tick
 *       profiler statistics, sorted by total elapsed time.</li>
 *   <li>{@code /lophine-perf deadlock-stats} - shows in-flight region
 *       ticks and total deadlock warnings emitted by the safety
 *       guard.</li>
 * </ul>
 *
 * <p>Aliases: {@code /lperf}, {@code /lophineperf}.
 */
public class MiliPerfCommand extends RootNode {
    public MiliPerfCommand() {
        super("lophine-perf", "lophine.commands.perf");
    }

    @Override
    protected boolean execute(@NotNull CommandContext context) {
        io.papermc.paper.command.brigadier.CommandSourceStack stack = context.getSource();
        String[] args = context.getInput().split("\\s+");
        String sub = args.length > 1 ? args[1] : "status";
        switch (sub.toLowerCase(java.util.Locale.ROOT)) {
            case "affinity" -> sendAffinity(stack);
            case "regions" -> sendRegions(stack);
            case "profiler" -> sendProfiler(stack);
            case "deadlock-stats" -> sendDeadlockStats(stack);
            default -> sendStatus(stack);
        }
        return true;
    }

    @Override
    public boolean requires(@NotNull CommandSourceStack source) {
        return source.getSender().hasPermission("lophine.commands.perf");
    }

    private void sendStatus(CommandSourceStack stack) {
        stack.getSender().sendMessage(header("mili 性能模块状态"));
        stack.getSender().sendMessage(key("自动亲和性", MiliAffinityAutoTuner.autoTuneEnabled
                ? "启用" : "关闭", MiliAffinityAutoTuner.autoTuneEnabled
                ? NamedTextColor.GREEN : NamedTextColor.RED));
        stack.getSender().sendMessage(key("亲和性策略", MiliAffinityAutoTuner.strategy, NamedTextColor.GOLD));
        stack.getSender().sendMessage(key("区域负载监控", MiliRegionLoadMonitor.enabled
                ? "启用 (" + MiliRegionLoadMonitor.getTrackedRegionCount() + " regions)"
                : "关闭", MiliRegionLoadMonitor.enabled
                ? NamedTextColor.GREEN : NamedTextColor.RED));
        stack.getSender().sendMessage(key("Tick 探查器", MiliTickProfiler.enabled
                ? "启用 (" + MiliTickProfiler.getTrackedLabelCount() + " labels)"
                : "关闭", MiliTickProfiler.enabled
                ? NamedTextColor.GREEN : NamedTextColor.RED));
        stack.getSender().sendMessage(Component.text("  用法: /lophine-perf {status|affinity|regions|profiler|deadlock-stats}")
                .color(NamedTextColor.GRAY));
    }

    private void sendAffinity(CommandSourceStack stack) {
        stack.getSender().sendMessage(header("mili 亲和性状态"));
        BitSet set = MiliAffinityAutoTuner.getResolvedBitSet();
        if (set == null || set.isEmpty()) {
            stack.getSender().sendMessage(Component.text("  未配置亲和性 (关闭或未检测)")
                    .color(NamedTextColor.YELLOW));
            return;
        }
        stack.getSender().sendMessage(key("策略", MiliAffinityAutoTuner.strategy, NamedTextColor.GOLD));
        stack.getSender().sendMessage(key("核心数", String.valueOf(set.cardinality()), NamedTextColor.WHITE));
        stack.getSender().sendMessage(key("Bitmask", describeBitSet(set), NamedTextColor.AQUA));
        try {
            BitSet current = Affinity.getAffinity();
            stack.getSender().sendMessage(key("当前线程", describeBitSet(current), NamedTextColor.AQUA));
            if (current.equals(set)) {
                stack.getSender().sendMessage(Component.text("  当前线程已绑定到目标核心")
                        .color(NamedTextColor.GREEN));
            } else {
                stack.getSender().sendMessage(Component.text("  当前线程未绑定到目标核心 (command 是从主线程执行的 - 这是正常的)")
                        .color(NamedTextColor.GRAY));
            }
        } catch (Throwable t) {
            stack.getSender().sendMessage(Component.text("  当前线程亲和性: 不可用 (" + t.getMessage() + ")")
                    .color(NamedTextColor.GRAY));
        }
    }

    private void sendRegions(CommandSourceStack stack) {
        stack.getSender().sendMessage(header("mili 区域负载摘要"));
        String summary = MiliRegionLoadMonitor.getLatestSummary();
        if (summary == null || summary.isEmpty()) {
            stack.getSender().sendMessage(Component.text("  还没有收集到样本 (需要等待 " + MiliRegionLoadMonitor.sampleIntervalTicks + " ticks)")
                    .color(NamedTextColor.YELLOW));
            return;
        }
        for (String line : summary.split("\n")) {
            stack.getSender().sendMessage(Component.text(line).color(NamedTextColor.WHITE));
        }
    }

    private void sendProfiler(CommandSourceStack stack) {
        stack.getSender().sendMessage(header("mili Tick 探查器"));
        if (!MiliTickProfiler.enabled) {
            stack.getSender().sendMessage(Component.text("  探查器未启用 (在 lophine-perf.tick-profiler = true 启用)")
                    .color(NamedTextColor.YELLOW));
            return;
        }
        String stats = MiliTickProfiler.dumpStats();
        for (String line : stats.split("\n")) {
            stack.getSender().sendMessage(Component.text(line).color(NamedTextColor.WHITE));
        }
    }

    private void sendDeadlockStats(CommandSourceStack stack) {
        stack.getSender().sendMessage(header("mili 安全监控"));
        stack.getSender().sendMessage(key("进行中的 region tick", String.valueOf(org.leavesmc.leaves.util.MiliRegionSafetyGuard.getInFlightTickCount()), NamedTextColor.WHITE));
        stack.getSender().sendMessage(key("累计死锁/慢 tick 警告", String.valueOf(org.leavesmc.leaves.util.MiliRegionSafetyGuard.getTotalDeadlockWarnings()), NamedTextColor.WHITE));
    }

    private static Component header(String text) {
        return Component.text("===== " + text + " =====")
                .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
    }

    private static Component key(String name, String value, NamedTextColor color) {
        return Component.text("  " + name + ": ").color(NamedTextColor.WHITE)
                .append(Component.text(value).color(color));
    }

    private static String describeBitSet(BitSet bs) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
            if (!first) sb.append(",");
            sb.append(i);
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
