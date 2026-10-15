package lampas.levelledmobs.level;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Multi-party boss tuning: the group factor on a boss's max health and the multiplier on boss damage that does not
 * come from {@code attack_damage}.
 */
public final class BossScaling {
    /** Players a boss is tuned for: two full parties of five. */
    public static final int DESIGN_GROUP_SIZE = 10;
    /** Players within this distance of a boss when it is levelled count towards its group factor. */
    public static final double GROUP_RADIUS = 128.0;
    /** Damage multiplier added per level above 1 for boss damage that does not come from attack_damage. */
    public static final double DAMAGE_PER_LEVEL = 0.025;

    private BossScaling() {}

    /** Max health factor for a boss levelled with {@code nearbyPlayers} players around it: max(10, n) / 10. */
    public static double groupFactor(int nearbyPlayers) {
        return Math.max(DESIGN_GROUP_SIZE, nearbyPlayers) / (double) DESIGN_GROUP_SIZE;
    }

    /** Outgoing damage multiplier for a boss at {@code level}: 1 + 0.025 * (level - 1). */
    public static double damageMultiplier(int level) {
        return (level > 1) ? 1.0 + DAMAGE_PER_LEVEL * (level - 1) : 1.0;
    }

    /** Counts the non-spectator players in the boss's level within {@link #GROUP_RADIUS} blocks of it. */
    public static int countNearbyPlayers(LivingEntity boss) {
        if (boss == null || !(boss.level() instanceof ServerLevel level)) {
            return 0;
        }
        double radiusSqr = GROUP_RADIUS * GROUP_RADIUS;
        int count = 0;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.distanceToSqr(boss) <= radiusSqr) {
                count++;
            }
        }
        return count;
    }

    /**
     * True when the hit's amount was read from the boss's {@code attack_damage}: a melee hit ({@code Mob.doHurtTarget},
     * a direct {@code mob_attack}) by a boss that has the attribute, such as the Warden's or Elder Guardian's. Those
     * already grow through LevelledMobs' attack_damage modifier. Everything else a boss deals uses fixed amounts:
     * wither skulls, explosions, the Warden's sonic boom, the Elder Guardian's beam and spikes, and the Ender Dragon's
     * head, wings and breath (the dragon has no attack_damage attribute).
     */
    public static boolean isAttackDamageHit(LivingEntity boss, DamageSource source) {
        return source.getDirectEntity() == boss
            && (source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO))
            && boss.getAttribute(Attributes.ATTACK_DAMAGE) != null;
    }
}
