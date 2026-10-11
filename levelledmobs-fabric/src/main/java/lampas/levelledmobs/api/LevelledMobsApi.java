package lampas.levelledmobs.api;

import lampas.levelledmobs.LevelledMobsModule;
import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import lampas.levelledmobs.data.SpawnReason;
import lampas.levelledmobs.level.RelevelQueue;
import net.minecraft.world.entity.LivingEntity;

/**
 * Stable public API for external modders to query and manipulate mob levels.
 */
public class LevelledMobsApi {

    /**
     * Gets the level of an entity, or 0 if unlevelled.
     */
    public static int getLevel(LivingEntity entity) {
        if (entity instanceof LevelledMobHolder holder) {
            LevelledMobData data = holder.lampas$getLevelData();
            return (data != null) ? data.level() : 0;
        }
        return 0;
    }

    /**
     * Returns true if the entity has been levelled.
     */
    public static boolean isLevelled(LivingEntity entity) {
        if (entity instanceof LevelledMobHolder holder) {
            LevelledMobData data = holder.lampas$getLevelData();
            return data != null && data.levelled();
        }
        return false;
    }

    /**
     * Sets the mob's level and recalculates scaled attributes and nametag.
     */
    public static void setLevel(LivingEntity entity, int level) {
        if (LevelledMobsModule.getMobLevelingService() != null) {
            LevelledMobsModule.getMobLevelingService().setLevel(entity, level, "custom");
        }
    }

    /**
     * Re-runs the lifecycle check for the entity. A levelled entity only has its saved modifiers restored; its level
     * is not recalculated. Use {@link #relevelManaged(LivingEntity)} to re-tier a managed mob. An unlevelled entity is
     * levelled from the active rule engine.
     */
    public static void relevel(LivingEntity entity) {
        if (LevelledMobsModule.getMobLevelingService() != null) {
            LevelledMobsModule.getMobLevelingService().level(entity, SpawnReason.COMMAND);
        }
    }

    /**
     * Queues a re-evaluation of a levelled managed mob against its current rule and strategy, for example after its
     * creation band changed. The work runs on a later server tick, at most 32 mobs per tick. Unlevelled, quarantined,
     * admin-set and mobs whose level is still valid are left unchanged.
     */
    public static void relevelManaged(LivingEntity entity) {
        RelevelQueue queue = LevelledMobsModule.getRelevelQueue();
        if (queue != null) {
            queue.enqueue(entity);
        }
    }

    /**
     * Retrieves the persistent LevelledMobData attached to the entity.
     */
    public static LevelledMobData getLevelData(LivingEntity entity) {
        if (entity instanceof LevelledMobHolder holder) {
            return holder.lampas$getLevelData();
        }
        return LevelledMobData.EMPTY;
    }
}
