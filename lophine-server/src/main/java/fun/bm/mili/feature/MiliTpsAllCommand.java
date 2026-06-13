package fun.bm.mili.feature;

import ca.spottedleaf.moonrise.common.time.TickData;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.TickRegions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.command.CommandContext;
import org.leavesmc.leaves.command.RootNode;
import org.leavesmc.leaves.plugin.MinecraftInternalPlugin;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * mili - /tpsall command.
 *
 * <p>Prints a comprehensive overview of the current region:
 * <ul>
 *   <li>Current region TPS (5-second window)</li>
 *   <li>Current region MSPT and peak MSPT</li>
 *   <li>Region statistics: chunks, players, entities</li>
 *   <li>Region utilisation percentage</li>
 * </ul>
 *
 * <p>Output is in Chinese for technical Chinese players.
 * Aliases: /mspt, /msptall
 *
 * <p>Implementation note: {@link TickRegionScheduler#getCurrentRegion()} only
 * returns a non-null value when called from a region tick thread (or the
 * global region thread). Command execution in Folia does <b>not</b> run on
 * a region thread, so this command must hop onto a region thread before
 * reading the tick report. For player senders we use the player's entity
 * scheduler; for non-player senders (console, RCON) we use the global
 * region scheduler. The reply is then sent back to the sender from the
 * main thread via {@link Bukkit#isPrimaryThread()} reschedule.
 */
public class MiliTpsAllCommand extends RootNode {
    private static final DecimalFormat ONE_DECIMAL = new DecimalFormat("#,##0.0");
    private static final DecimalFormat TWO_DECIMALS = new DecimalFormat("#,##0.00");

    public MiliTpsAllCommand() {
        super("tpsall", "lophine.commands.tpsall");
    }

    @Override
    protected boolean execute(@NotNull CommandContext context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        sendTpsAll(context.getSender());
        return true;
    }

    @Override
    public boolean requires(@NotNull CommandSourceStack source) {
        return source.getSender().hasPermission("lophine.commands.tpsall");
    }

    public void sendTpsAll(@NotNull CommandSender sender) {
        final Consumer<List<Component>> reply = lines -> {
            final Runnable send = () -> {
                for (Component line : lines) {
                    sender.sendMessage(line);
                }
            };
            if (Bukkit.isPrimaryThread()) {
                send.run();
            } else {
                Bukkit.getScheduler().runTask(MinecraftInternalPlugin.INSTANCE, send);
            }
        };

        if (sender instanceof Player player) {
            try {
                player.getScheduler().run(MinecraftInternalPlugin.INSTANCE, (task) -> {
                    List<Component> result = buildTpsReport();
                    reply.accept(result);
                }, null);
            } catch (Throwable t) {
                sender.sendMessage(Component.text("获取 TPS 数据失败: " + t.getMessage()).color(NamedTextColor.RED));
            }
        } else {
            try {
                Bukkit.getGlobalRegionScheduler().run(MinecraftInternalPlugin.INSTANCE, (task) -> {
                    List<Component> result = buildTpsReport();
                    reply.accept(result);
                });
            } catch (Throwable t) {
                sender.sendMessage(Component.text("获取 TPS 数据失败: " + t.getMessage()).color(NamedTextColor.RED));
            }
        }
    }

    private static List<Component> buildTpsReport() {
        final List<Component> lines = new ArrayList<>();
        try {
            final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region =
                    TickRegionScheduler.getCurrentRegion();
            if (region == null || region.getData() == null) {
                lines.add(Component.text("\u65e0\u6cd5\u83b7\u53d6\u5f53\u524d\u533a\u57df (region \u5c1a\u672a\u521d\u59cb\u5316?)").color(NamedTextColor.RED));
                return lines;
            }
            final TickData.TickReportData reportData =
                    region.getData().getRegionSchedulingHandle().getTickReport5s(System.nanoTime());
            final TickRegions.RegionStats regionStats = region.getData().getRegionStats();
            final long regionId = region.getData().id;
    
            // Header with region info
            lines.add(Component.text("===== mili \u533a\u57df TPS/MSPT \u72b6\u6001 =====")
                    .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            lines.add(Component.text("  \u533a\u57df #" + regionId)
                    .color(NamedTextColor.DARK_GRAY)
                    .append(Component.text("  5s \u7a97\u53e3\u5e73\u5747\u503c").color(NamedTextColor.DARK_GRAY)));
    
            if (reportData != null) {
                final TickData.SegmentData tpsData = reportData.tpsData().segmentAll();
                final double tps = tpsData.average();
                final double mspt = reportData.timePerTickData().segmentAll().average() / 1.0E6;
                final double maxMspt = reportData.timePerTickData().segmentAll().greatest() / 1.0E6;
                final double utilisation = reportData.utilisation() * 100.0;
    
                // TPS with bar
                lines.add(Component.text("  TPS: ").color(NamedTextColor.GRAY)
                        .append(Component.text(TWO_DECIMALS.format(tps) + " / 20.00").color(tpsColor(tps)))
                        .append(Component.text("  " + tpsBar(tps)).color(NamedTextColor.DARK_GRAY)));
    
                // MSPT with hover for peak
                lines.add(Component.text("  MSPT: ").color(NamedTextColor.GRAY)
                        .append(Component.text(TWO_DECIMALS.format(mspt) + " ms").color(msptColor(mspt)))
                        .append(Component.text("  (\u5cf0\u503c " + TWO_DECIMALS.format(maxMspt) + " ms)")
                                .color(NamedTextColor.DARK_GRAY)));
    
                // Utilisation bar
                NamedTextColor utilColor = utilisation >= 80 ? NamedTextColor.RED
                        : (utilisation >= 50 ? NamedTextColor.YELLOW : NamedTextColor.GREEN);
                lines.add(Component.text("  \u4f7f\u7528\u7387: ").color(NamedTextColor.GRAY)
                        .append(Component.text(ONE_DECIMAL.format(utilisation) + "%").color(utilColor))
                        .append(Component.text("  " + utilBar(utilisation)).color(utilColor)));
    
                // Separator
                lines.add(Component.text("  \u2501".repeat(20)).color(NamedTextColor.DARK_GRAY));
            } else {
                lines.add(Component.text("  \u533a\u57df\u6570\u636e\u5c1a\u672a\u6536\u96c6 (\u670d\u52a1\u5668\u542f\u52a8\u65f6\u95f4\u8fc7\u77ed?)").color(NamedTextColor.GRAY));
            }
    
            // Region stats with better formatting
            lines.add(Component.text("  \u533a\u57df\u7edf\u8ba1").color(NamedTextColor.YELLOW));
            lines.add(Component.text("    \u533a\u5757: ").color(NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(regionStats.getChunkCount())).color(NamedTextColor.WHITE)));
            lines.add(Component.text("    \u5b9e\u4f53: ").color(NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(regionStats.getEntityCount())).color(NamedTextColor.WHITE)));
            lines.add(Component.text("    \u73a9\u5bb6: ").color(NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(regionStats.getPlayerCount())).color(NamedTextColor.WHITE)));
        } catch (Throwable t) {
            lines.add(Component.text("\u83b7\u53d6 TPS \u6570\u636e\u5931\u8d25: " + t.getMessage()).color(NamedTextColor.RED));
        }
        return lines;
    }
    
    /**
     * Build a simple TPS bar: \u2588\u2588\u2588\u2588\u2591\u2591\u2591\u2591\u2591\u2591
     */
    private static String tpsBar(double tps) {
        int filled = (int) Math.round(tps / 20.0 * 10);
        filled = Math.max(0, Math.min(10, filled));
        return "\u2588".repeat(filled) + "\u2591".repeat(10 - filled);
    }
    
    /**
     * Build a utilisation bar.
     */
    private static String utilBar(double pct) {
        int filled = (int) Math.round(pct / 100.0 * 10);
        filled = Math.max(0, Math.min(10, filled));
        return "\u2588".repeat(filled) + "\u2591".repeat(10 - filled);
    }

    private static NamedTextColor tpsColor(double tps) {
        if (tps >= 19.5) return NamedTextColor.GREEN;
        if (tps >= 18.0) return NamedTextColor.YELLOW;
        if (tps >= 15.0) return NamedTextColor.GOLD;
        return NamedTextColor.RED;
    }

    private static NamedTextColor msptColor(double mspt) {
        if (mspt <= 25.0) return NamedTextColor.GREEN;
        if (mspt <= 40.0) return NamedTextColor.YELLOW;
        if (mspt <= 50.0) return NamedTextColor.GOLD;
        return NamedTextColor.RED;
    }
}
