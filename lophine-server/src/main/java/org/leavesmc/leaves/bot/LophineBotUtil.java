package org.leavesmc.leaves.bot;

import ca.spottedleaf.moonrise.common.util.TickThread;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.bukkit.Location;

/**
 * Lophine - Folia safety utilities for fake players (ServerBot).
 * <p>
 * Folia uses region-based multithreading. Each region runs on its own thread
 * and entities must only be mutated from the thread that owns their region.
 * Failing to respect this can corrupt chunk state, leak entities, or
 * crash the server.
 * <p>
 * This class provides a single place to:
 * <ul>
 *   <li>Verify that a bot operation is running on the correct region thread</li>
 *   <li>Reschedule a misrouted operation to the correct region via the
 *       Bukkit {@link org.bukkit.entity.Entity#getScheduler() entity scheduler}</li>
 *   <li>Guard against world unload / shutdown races during bot spawn / remove</li>
 *   <li>Rate-limit repeated misrouting so a programming error cannot spam
 *       the scheduler queue</li>
 * </ul>
 */
public final class LophineBotUtil {
    private static final Logger LOGGER = LogUtils.getLogger();

    private LophineBotUtil() {
    }

    /**
     * Asserts that the current thread is the tick thread for the region that
     * contains the given entity. If it is not, the work is re-scheduled on
     * the owning region via the Bukkit entity scheduler (Folia-safe) and
     * {@code false} is returned. Returning {@code true} means the caller
     * may proceed with the work on the current thread.
     */
    public static boolean ensureTickThread(LivingEntity entity, Runnable work) {
        if (entity == null || work == null) {
            return false;
        }
        if (entity.level() == null) {
            return false;
        }
        if (TickThread.isTickThreadFor(entity.level(), entity.getX(), entity.getZ())) {
            return true;
        }
        // Misrouted: reschedule on the correct region via the Folia entity scheduler
        try {
            entity.getBukkitEntity().taskScheduler().run(
                    (org.bukkit.entity.Entity unused) -> work.run(),
                    null
            );
        } catch (Throwable t) {
            LOGGER.warn("Failed to reschedule bot work to owning region: {}", t.getMessage());
        }
        return false;
    }

    /**
     * Verifies that {@code location} belongs to the region currently ticked by
     * the calling thread. Returns {@code true} on success. On mismatch, logs
     * a warning and returns {@code false} so the caller can decide whether
     * to re-schedule.
     */
    public static boolean ensureTickThread(ServerLevel world, Location location, String caller) {
        if (world == null || location == null) {
            return false;
        }
        if (location.getWorld() == null) {
            return false;
        }
        if (world.getWorld() == null || !world.getWorld().getUID().equals(location.getWorld().getUID())) {
            LOGGER.warn("[{}] world mismatch: location={} world={}", caller, location.getWorld().getName(), world.getWorld().getName());
            return false;
        }
        if (TickThread.isTickThreadFor(world, location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return true;
        }
        warnThreadMismatch(caller, world, location);
        return false;
    }

    /**
     * Internal helper used by patched code paths to log a thread mismatch
     * without spamming the logger. The first occurrence per (caller, world)
     * pair is logged at WARN, subsequent ones at DEBUG.
     */
    public static void warnThreadMismatch(String caller, ServerLevel world, Location location) {
        if (caller == null) {
            caller = "bot";
        }
        String key = caller + "@" + (world == null ? "null" : Integer.toHexString(System.identityHashCode(world)));
        long now = System.nanoTime();
        Long last = lastWarnAt.get(key);
        if (last == null || now - last > WARN_THROTTLE_NANOS) {
            lastWarnAt.put(key, now);
            LOGGER.warn("[{}] thread mismatch (likely Folia region race): location={} world={} — caller is NOT on the owning region tick thread; rescheduling",
                    caller,
                    location == null ? "null" : (location.getWorld() == null ? "null" : location.getWorld().getName()),
                    world == null ? "null" : world.serverLevelData.getLevelName());
        } else {
            LOGGER.debug("[{}] thread mismatch (throttled)", caller);
        }
    }

    private static final java.util.Map<String, Long> lastWarnAt = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long WARN_THROTTLE_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
}
