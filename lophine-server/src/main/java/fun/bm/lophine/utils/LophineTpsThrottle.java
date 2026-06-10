package fun.bm.lophine.utils;

import net.minecraft.server.MinecraftServer;

/**
 * Lophine - TPS-aware throttling for生电 features.
 *
 * <p>Several生电 features (wool hopper counter, item elevator, etc.) can
 * consume a lot of tick time when running at maximum speed. When the server
 * TPS drops, running these features at full speed only makes the lag worse.
 * <p>
 * This utility checks the current TPS and returns a throttle factor (0.0 to
 * 1.0) that callers can use to slow down or skip work.
 */
public final class LophineTpsThrottle {
    private LophineTpsThrottle() {
    }

    /**
     * Returns a throttle factor in [0.0, 1.0] based on the current TPS.
     * If TPS is at or above {@code goodTps}, the factor is 1.0 (no throttle).
     * If TPS is at or below {@code badTps}, the factor is 0.0 (skip all work).
     * In between, the factor is interpolated linearly.
     */
    public static double throttleFactor(double goodTps, double badTps) {
        if (goodTps <= badTps) {
            return badTps < 1.0 ? 0.0 : 1.0;
        }
        double[] tps = recentTps();
        double current = tps[0];
        if (current >= goodTps) {
            return 1.0;
        }
        if (current <= badTps) {
            return 0.0;
        }
        return (current - badTps) / (goodTps - badTps);
    }

    /**
     * Returns the recent TPS values from the server. Index 0 is the 5-second
     * average, index 1 is 10s, etc.
     */
    public static double[] recentTps() {
        long[] times = MinecraftServer.getServer().getTickTimes5s();
        if (times == null || times.length == 0) {
            return new double[]{20.0, 20.0, 20.0};
        }
        double[] result = new double[times.length];
        for (int i = 0; i < times.length; i++) {
            // times[i] is in nanoseconds; convert to TPS
            result[i] = Math.min(20.0, 1_000_000_000.0 / Math.max(times[i], 1));
        }
        return result;
    }
}
