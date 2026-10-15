package lampas.levelledmobs.events;

import lampas.levelledmobs.LevelledMobsModule;
import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import lampas.levelledmobs.level.BossClassifier;
import lampas.levelledmobs.level.BossScaling;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * Handles combat damage modifications for non-melee and projectile attacks from levelled mobs.
 */
public class CombatHandler {
    private static double projectileDamageMultiplier = 0.05; // +5% per level above 1
    private static double explosionDamageMultiplier = 0.05; // +5% per level above 1

    /**
     * Calculates the adjusted damage amount based on the attacker's level and damage source type.
     * Note: Direct melee attack damage is NOT modified here to prevent double-scaling with Attributes.ATTACK_DAMAGE.
     *
     * <p>Damage caused by a levelled boss, or by a projectile or cloud it owns, is multiplied by
     * 1 + 0.025 * (level - 1) instead, unless the amount was read from the boss's attack_damage (see
     * {@link BossScaling#isAttackDamageHit}). It replaces the projectile and explosion multipliers for that damage.</p>
     */
    public static float modifyDamage(LivingEntity victim, DamageSource source, float originalDamage) {
        if (originalDamage <= 0 || source == null) {
            return originalDamage;
        }

        Entity attacker = source.getEntity();
        Entity directEntity = source.getDirectEntity();

        int bossLevel = bossDamageLevel(source);
        if (bossLevel > 1) {
            return originalDamage * (float) BossScaling.damageMultiplier(bossLevel);
        }

        // 1. Check for projectile attacks (e.g. arrow, fireball, thrown trident)
        if (directEntity instanceof Projectile && attacker instanceof LivingEntity livingAttacker) {
            if (livingAttacker instanceof LevelledMobHolder holder) {
                LevelledMobData data = holder.lampas$getLevelData();
                if (data != null && data.level() > 1) {
                    float multiplier = 1.0f + (float) ((data.level() - 1) * projectileDamageMultiplier);
                    return originalDamage * multiplier;
                }
            }
        }

        // 2. Check for a levelled mob's own blast (e.g. Creepers). The mob is both its direct and its causing entity,
        // which makes it player_explosion rather than explosion, so match the whole is_explosion tag. TNT or an End
        // Crystal the mob sets off is not multiplied; a ghast fireball's blast is a projectile's and is handled above.
        if (source.is(DamageTypeTags.IS_EXPLOSION) && directEntity == attacker
            && attacker instanceof LivingEntity livingAttacker) {
            if (livingAttacker instanceof LevelledMobHolder holder) {
                LevelledMobData data = holder.lampas$getLevelData();
                if (data != null && data.level() > 1) {
                    float multiplier = 1.0f + (float) ((data.level() - 1) * explosionDamageMultiplier);
                    return originalDamage * multiplier;
                }
            }
        }

        // Direct melee or other damages remain unmodified to prevent double-multiplying
        return originalDamage;
    }

    /** The level of the levelled boss that caused this fixed-amount damage, or 0 when the boss multiplier does not apply. */
    private static int bossDamageLevel(DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity boss) || !(boss instanceof LevelledMobHolder holder)) {
            return 0;
        }
        LevelledMobData data = holder.lampas$getLevelData();
        if (data == null || !data.levelled() || data.level() <= 1) {
            return 0;
        }
        BossClassifier classifier = LevelledMobsModule.getBossClassifier();
        if (classifier == null || !classifier.isBoss(boss) || BossScaling.isAttackDamageHit(boss, source)) {
            return 0;
        }
        return data.level();
    }

    public static void setProjectileDamageMultiplier(double multiplier) {
        projectileDamageMultiplier = multiplier;
    }

    public static void setExplosionDamageMultiplier(double multiplier) {
        explosionDamageMultiplier = multiplier;
    }
}
