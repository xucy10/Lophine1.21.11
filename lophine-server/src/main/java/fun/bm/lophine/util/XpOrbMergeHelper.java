package fun.bm.lophine.util;

import fun.bm.lophine.config.modules.misc.ItemEntityPerfConfig;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.AABB;

/**
 * Lophine - Experience orb merge optimization helper.
 *
 * <p>Provides enhanced cross-value merging: orbs with different XP values
 * can merge up to a configurable maximum, significantly reducing entity
 * count during mob farming.
 */
public final class XpOrbMergeHelper {
    private XpOrbMergeHelper() {}

    /**
     * Attempts to merge this orb with nearby orbs of different values.
     * Called after the vanilla scanForMerges pass.
     *
     * @param orb the experience orb to merge
     */
    public static void enhancedMerge(ExperienceOrb orb) {
        int maxValue = ItemEntityPerfConfig.xpOrbMaxValue;
        if (maxValue <= 0 || orb.isRemoved() || orb.getValue() >= maxValue) {
            return;
        }

        double mergeRadius = ItemEntityPerfConfig.xpOrbMergeRadius > 0
            ? ItemEntityPerfConfig.xpOrbMergeRadius : 0.5;

        AABB searchBox = orb.getBoundingBox().inflate(mergeRadius);

        for (ExperienceOrb other : orb.level().getEntitiesOfClass(
            ExperienceOrb.class, searchBox,
            e -> e != orb && !e.isRemoved() && e.getValue() < maxValue
        )) {
            int combinedValue = orb.getValue() + other.getValue();
            if (combinedValue <= maxValue) {
                orb.setValue(combinedValue);
                orb.count += other.count;
                other.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.MERGE);
                break;
            }
        }
    }
}
