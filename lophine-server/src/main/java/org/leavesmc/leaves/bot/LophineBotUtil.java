package org.leavesmc.leaves.bot;

import ca.spottedleaf.moonrise.common.util.TickThread;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.bukkit.Location;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.List;

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

    // Lophine - per-entity reschedule counter to prevent scheduler avalanche.
    // If the same entity is rescheduled more than MAX_RESCHEDULE_ATTEMPTS times
    // within RESCHEDULE_WINDOW_NANOS, we drop the work and log an error instead
    // of flooding the scheduler queue.
    private static final int MAX_RESCHEDULE_ATTEMPTS = 64;
    private static final long RESCHEDULE_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(10);
    private static final Map<String, RescheduleTracker> rescheduleTrackers = new ConcurrentHashMap<>();

    private LophineBotUtil() {
    }

    /**
     * Asserts that the current thread is the tick thread for the region that
     * contains the given entity. If it is not, the work is re-scheduled on
     * the owning region via the Bukkit entity scheduler (Folia-safe) and
     * {@code false} is returned. Returning {@code true} means the caller
     * may proceed with the work on the current thread.
     * <p>
     * Lophine enhancement: if the same entity has been rescheduled more than
     * {@link #MAX_RESCHEDULE_ATTEMPTS} times in the last 10 seconds, the
     * work is dropped to prevent scheduler queue avalanche.
     */
    public static boolean ensureTickThread(LivingEntity entity, Runnable work) {
        if (entity == null || work == null) {
            return false;
        }
        if (entity.level() == null) {
            return false;
        }
        if (TickThread.isTickThreadFor(entity.level(), entity.getX(), entity.getZ())) {
            // On correct thread - clear the reschedule tracker for this entity
            rescheduleTrackers.remove(entity.getUUID().toString());
            return true;
        }

        // Lophine - check reschedule rate limit before submitting to scheduler
        String entityId = entity.getUUID().toString();
        RescheduleTracker tracker = rescheduleTrackers.computeIfAbsent(entityId, k -> new RescheduleTracker());
        if (!tracker.tryAcquire()) {
            LOGGER.error("[ensureTickThread] Dropping work for bot {} - reschedule rate limit exceeded ({} attempts in {}s). " +
                    "This usually means a code path is repeatedly calling bot operations from the wrong thread.",
                    entity.getName().getString(), MAX_RESCHEDULE_ATTEMPTS,
                    TimeUnit.NANOSECONDS.toSeconds(RESCHEDULE_WINDOW_NANOS));
            return false;
        }

        // Misrouted: reschedule on the correct region via the Folia entity scheduler
        try {
            entity.getBukkitEntity().taskScheduler.schedule(
                    (LivingEntity unused) -> work.run(),
                    null, 1L
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

    /**
     * Lophine - check if the current thread is the owning tick thread for an entity,
     * without rescheduling. Useful for guard checks where the caller handles
     * rescheduling themselves.
     */
    public static boolean isOnTickThread(LivingEntity entity) {
        if (entity == null || entity.level() == null) {
            return false;
        }
        return TickThread.isTickThreadFor(entity.level(), entity.getX(), entity.getZ());
    }

    /**
     * Lophine - check if the current thread is the owning tick thread for a location.
     */
    public static boolean isOnTickThread(ServerLevel world, Location location) {
        if (world == null || location == null) {
            return false;
        }
        return TickThread.isTickThreadFor(world, location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    /**
     * Lophine - safely iterate over a bot list in a Folia multithreaded environment.
     * For each bot, checks if we are on the bot's region thread:
     * <ul>
     *   <li>If yes, executes the action immediately</li>
     *   <li>If no, schedules the action on the bot's region via the entity scheduler</li>
     * </ul>
     * This prevents cross-region data access violations that can corrupt chunk state
     * or crash the server.
     *
     * @param bots   the list of ServerBot instances to iterate
     * @param action the action to perform on each bot
     */
    @SuppressWarnings("unchecked")
    public static void forEachSafe(List<? extends LivingEntity> bots, Consumer<LivingEntity> action) {
        if (bots == null || bots.isEmpty()) {
            return;
        }
        for (LivingEntity bot : bots) {
            if (bot == null || bot.level() == null || bot.isRemoved()) {
                continue;
            }
            if (TickThread.isTickThreadFor(bot.level(), bot.getX(), bot.getZ())) {
                try {
                    action.accept(bot);
                } catch (Throwable t) {
                    LOGGER.warn("[forEachSafe] Error processing bot {}: {}", bot.getName().getString(), t.getMessage());
                }
            } else {
                // Schedule on the bot's owning region thread
                try {
                    bot.getBukkitEntity().taskScheduler.schedule(
                            (LivingEntity e) -> {
                                try {
                                    action.accept(e);
                                } catch (Throwable t) {
                                    LOGGER.warn("[forEachSafe] Error processing bot {} (scheduled): {}", e.getName().getString(), t.getMessage());
                                }
                            }, null, 1L
                    );
                } catch (Throwable t) {
                    LOGGER.debug("[forEachSafe] Failed to schedule bot {}: {}", bot.getName().getString(), t.getMessage());
                }
            }
        }
    }

    private static final Map<String, Long> lastWarnAt = new ConcurrentHashMap<>();
    private static final long WARN_THROTTLE_NANOS = TimeUnit.SECONDS.toNanos(5);

    /**
     * Lophine - Rate-limit tracker for reschedule attempts per entity.
     * Uses a sliding window approach: if the count exceeds MAX_RESCHEDULE_ATTEMPTS
     * within RESCHEDULE_WINDOW_NANOS, further reschedules are dropped.
     */
    private static final class RescheduleTracker {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStartNanos = System.nanoTime();

        boolean tryAcquire() {
            long now = System.nanoTime();
            // Reset window if expired
            if (now - windowStartNanos > RESCHEDULE_WINDOW_NANOS) {
                synchronized (this) {
                    if (now - windowStartNanos > RESCHEDULE_WINDOW_NANOS) {
                        windowStartNanos = now;
                        count.set(0);
                    }
                }
            }
            int current = count.incrementAndGet();
            return current <= MAX_RESCHEDULE_ATTEMPTS;
        }
    }
}
