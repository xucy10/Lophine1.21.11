package fun.bm.lophine.config.modules.misc;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

/**
 * Lophine - Item entity performance/生电 tunables.
 *
 * <p>These settings expose levers that technical players care about for
 * item elevators, sorting systems and large-scale farms. Vanilla has a
 * conservative default that is correct but slow; technical players often
 * want a configurable merge radius and a faster pick-up so their farms
 * stay responsive at scale.
 */
@ConfigClassInfo(category = EnumConfigCategory.MISC, name = "item-entity-perf")
public class ItemEntityPerfConfig implements IConfigModule {
    @ConfigInfo(name = "optimistic-merge-range-bonus", comments = """
            Extra blocks added to the vanilla item-entity merge radius.
            0 = vanilla behaviour. Higher values let items find stacks to merge
            into across hopper / water-stream gaps, which is useful for
            compact item elevators and water-stream sorters. Recommended
            range: 0-2. Too high values will cause items in unrelated lanes
            to merge incorrectly.""")
    public static double mergeRangeBonus = 0.0;

    @ConfigInfo(name = "fast-pickup-cooldown-ticks", comments = """
            Override the server-side cooldown (in ticks) between item entity
            spawns at the same position. Vanilla is 10 ticks which is too
            slow for many technical builds. Set to 0 to use vanilla value.
            Recommended: 2-4 for item elevators, 0 for vanilla survival.""")
    public static int fastPickupCooldownTicks = 0;

    @ConfigInfo(name = "unrestricted-pickup", comments = """
            If true, the server ignores the per-player item pickup cooldown
            and allows instant pickup of any item the player is standing on.
            This is a major quality-of-life win for technical servers but
            may feel cheaty on survival, so it is disabled by default.""")
    public static boolean unrestrictedPickup = false;

    @ConfigInfo(name = "max-merge-attempts-per-tick", comments = """
            Maximum number of item merge attempts per item entity per tick.
            Vanilla has no limit which can cause severe lag with many items
            in a small area (e.g. super smelters, bulk item elevators).
            Set to 0 for unlimited (vanilla). Recommended: 8-16 for busy
            technical servers.""")
    public static int maxMergeAttemptsPerTick = 0;

    @ConfigInfo(name = "tps-aware-merge-throttle", comments = """
            If true, item merge operations are throttled when the server TPS
            drops below 'tps-aware-merge-threshold'. This prevents item
            processing from consuming excessive tick time during lag spikes,
            which is critical for stability on busy technical servers.""")
    public static boolean tpsAwareMergeThrottle = false;

    @ConfigInfo(name = "tps-aware-merge-threshold", comments = """
            TPS threshold below which item merge operations start being
            throttled. Only relevant when 'tps-aware-merge-throttle' is true.
            Default 16.0 means throttling begins when TPS drops below 80%.""")
    public static double tpsAwareMergeThreshold = 16.0;

    @ConfigInfo(name = "hopper-transfer-boost", comments = """
            Multiplier applied to the hopper transfer cooldown. Values < 1.0
            make hoppers faster (0.5 = twice as fast), values > 1.0 make
            them slower. This is useful for technical servers where hopper
            throughput is critical (e.g. super smelters, storage systems).
            WARNING: Values below 0.5 may cause unexpected behavior.
            1.0 = vanilla speed.""")
    public static double hopperTransferBoost = 1.0;
}
