package lampas.levelledmobs.level;

import lampas.levelledmobs.data.SpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.raid.Raider;

/**
 * Resolves or infers SpawnReason for newly loaded/spawned entities from internal state and context.
 */
public class SpawnReasonResolver {

    /**
     * Attempts to infer a specialized SpawnReason from entity characteristics if no explicit reason was supplied.
     */
    public static SpawnReason infer(LivingEntity entity, SpawnReason explicitReason) {
        if (explicitReason != null && explicitReason != SpawnReason.UNKNOWN && explicitReason != SpawnReason.NATURAL) {
            return explicitReason;
        }

        if (entity instanceof Raider raider && raider.hasActiveRaid()) {
            return SpawnReason.RAID;
        }

        if (entity instanceof PatrollingMonster patrol && patrol.isPatrolLeader()) {
            return SpawnReason.PATROL;
        }

        return (explicitReason != null) ? explicitReason : SpawnReason.NATURAL;
    }
}
