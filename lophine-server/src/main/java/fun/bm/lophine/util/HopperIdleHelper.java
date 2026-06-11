package fun.bm.lophine.util;

import fun.bm.lophine.config.modules.misc.ItemEntityPerfConfig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Lophine - Hopper idle detection helper.
 *
 * <p>Tracks consecutive idle ticks for each hopper and skips tick processing
 * when a hopper has been empty for longer than the configured threshold.
 * This reduces CPU usage in builds with many idle hoppers (sorting systems,
 * storage tech, etc.) without affecting hoppers that are actively moving items.
 */
public final class HopperIdleHelper {
    private static final Map<HopperBlockEntity, Integer> idleTickMap = new WeakHashMap<>();

    private HopperIdleHelper() {}

    /**
     * Returns true if this hopper's tick should be skipped because it is idle.
     *
     * <p>A hopper is considered idle when all its inventory slots are empty.
     * After being idle for more than {@code hopperIdleThreshold} consecutive ticks,
     * the hopper only processes every N ticks (where N = {@code hopperIdleSkipTicks}).
     *
     * @param blockEntity the hopper block entity
     * @param level the level (used for game time modulo check)
     * @return true if the tick should be skipped
     */
    public static boolean shouldSkipTick(HopperBlockEntity blockEntity, Level level) {
        int skipTicks = ItemEntityPerfConfig.hopperIdleSkipTicks;
        int threshold = ItemEntityPerfConfig.hopperIdleThreshold;

        // Feature disabled
        if (skipTicks <= 1 || threshold <= 0) {
            return false;
        }

        boolean isEmpty = isHopperEmpty(blockEntity);
        int idleTicks = idleTickMap.getOrDefault(blockEntity, 0);

        if (!isEmpty) {
            // Hopper has items - reset idle counter
            if (idleTicks > 0) {
                idleTickMap.put(blockEntity, 0);
            }
            return false;
        }

        // Hopper is empty - increment idle counter
        idleTicks++;
        idleTickMap.put(blockEntity, idleTicks);

        // Only skip after threshold is exceeded
        if (idleTicks <= threshold) {
            return false;
        }

        // Skip ticks based on game time modulo
        return level.getGameTime() % skipTicks != 0;
    }

    private static boolean isHopperEmpty(HopperBlockEntity hopper) {
        List<ItemStack> items = hopper.getContents();
        for (int i = 0, size = items.size(); i < size; i++) {
            if (!items.get(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
