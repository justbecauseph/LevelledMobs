package lampas.levelledmobs.api;

import lampas.levelledmobs.LevelledMobsModule;
import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import lampas.levelledmobs.data.SpawnReason;
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
     * Forces the entity to recalculate its level using the active rule engine.
     */
    public static void relevel(LivingEntity entity) {
        if (LevelledMobsModule.getMobLevelingService() != null) {
            LevelledMobsModule.getMobLevelingService().level(entity, SpawnReason.COMMAND);
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
