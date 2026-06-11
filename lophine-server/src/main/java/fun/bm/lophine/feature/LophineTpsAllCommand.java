package fun.bm.lophine.feature;

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
                player.taskScheduler.schedule((org.bukkit.entity.LivingEntity nmsEntity) -> {
                    List<Component> result = buildTpsReport();
                    reply.accept(result);
                }, null, 1L);
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
                lines.add(Component.text("无法获取当前区域 (region 尚未初始化?)").color(NamedTextColor.RED));
                return lines;
            }
            final TickData.TickReportData reportData =
                    region.getData().getRegionSchedulingHandle().getTickReport5s(System.nanoTime());
            final TickRegions.RegionStats regionStats = region.getData().getRegionStats();

            lines.add(Component.text("===== Lophine 区域 TPS/MSPT 状态 =====")
                    .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

            if (reportData != null) {
                final TickData.SegmentData tpsData = reportData.tpsData().segmentAll();
                final double tps = tpsData.average();
                final double mspt = reportData.timePerTickData().segmentAll().average() / 1.0E6;
                final double maxMspt = reportData.timePerTickData().segmentAll().greatest() / 1.0E6;
                final double utilisation = reportData.utilisation() * 100.0;

                lines.add(Component.text("TPS: " + TWO_DECIMALS.format(tps) + " / 20.00").color(tpsColor(tps)));
                lines.add(Component.text("MSPT: " + TWO_DECIMALS.format(mspt) + " ms (峰值 " + TWO_DECIMALS.format(maxMspt) + " ms)")
                        .color(msptColor(mspt)));
                lines.add(Component.text("区域使用率: " + ONE_DECIMAL.format(utilisation) + "%")
                        .color(utilisation >= 80 ? NamedTextColor.RED : (utilisation >= 50 ? NamedTextColor.YELLOW : NamedTextColor.GREEN)));
            } else {
                lines.add(Component.text("区域数据尚未收集 (服务器启动时间过短?)").color(NamedTextColor.GRAY));
            }

            lines.add(Component.text("--- 区域统计 ---").color(NamedTextColor.YELLOW));
            lines.add(Component.text("区块数: " + regionStats.getChunkCount()).color(NamedTextColor.WHITE));
            lines.add(Component.text("玩家数: " + regionStats.getPlayerCount()).color(NamedTextColor.WHITE));
            lines.add(Component.text("实体数: " + regionStats.getEntityCount()).color(NamedTextColor.WHITE));
        } catch (Throwable t) {
            lines.add(Component.text("获取 TPS 数据失败: " + t.getMessage()).color(NamedTextColor.RED));
        }
        return lines;
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
