package fun.bm.lophine.feature;

import ca.spottedleaf.moonrise.common.time.TickData;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.TickRegions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.command.CommandContext;
import org.leavesmc.leaves.command.RootNode;

import java.text.DecimalFormat;

/**
 * Lophine - /tpsall command.
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
 */
public class LophineTpsAllCommand extends RootNode {
    private static final DecimalFormat ONE_DECIMAL = new DecimalFormat("#,##0.0");
    private static final DecimalFormat TWO_DECIMALS = new DecimalFormat("#,##0.00");

    public LophineTpsAllCommand() {
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
        try {
            final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region =
                    TickRegionScheduler.getCurrentRegion();
            final TickData.TickReportData reportData =
                    region.getData().getRegionSchedulingHandle().getTickReport5s(System.nanoTime());
            final TickRegions.RegionStats regionStats = region.getData().getRegionStats();

            sender.sendMessage(Component.text("===== Lophine 区域 TPS/MSPT 状态 =====")
                    .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

            if (reportData != null) {
                final TickData.SegmentData tpsData = reportData.tpsData().segmentAll();
                final double mspt = reportData.timePerTickData().segmentAll().average() / 1.0E6;
                final double maxMspt = reportData.timePerTickData().segmentAll().greatest() / 1.0E6;
                final double utilisation = reportData.utilisation() * 100.0;

                sender.sendMessage(Component.text("TPS: " + TWO_DECIMALS.format(tpsData.average()) + " / 20.00")
                        .color(tpsColor(tpsData.average())));
                sender.sendMessage(Component.text("MSPT: " + TWO_DECIMALS.format(mspt) + " ms (峰值 " + TWO_DECIMALS.format(maxMspt) + " ms)")
                        .color(msptColor(mspt)));
                sender.sendMessage(Component.text("区域使用率: " + ONE_DECIMAL.format(utilisation) + "%")
                        .color(utilisation >= 80 ? NamedTextColor.RED : (utilisation >= 50 ? NamedTextColor.YELLOW : NamedTextColor.GREEN)));
            } else {
                sender.sendMessage(Component.text("区域数据尚未收集 (服务器启动时间过短?)").color(NamedTextColor.GRAY));
            }

            sender.sendMessage(Component.text("--- 区域统计 ---").color(NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("区块数: " + regionStats.getChunkCount()).color(NamedTextColor.WHITE));
            sender.sendMessage(Component.text("玩家数: " + regionStats.getPlayerCount()).color(NamedTextColor.WHITE));
            sender.sendMessage(Component.text("实体数: " + regionStats.getEntityCount()).color(NamedTextColor.WHITE));

        } catch (Exception e) {
            sender.sendMessage(Component.text("获取 TPS 数据失败: " + e.getMessage()).color(NamedTextColor.RED));
        }
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
