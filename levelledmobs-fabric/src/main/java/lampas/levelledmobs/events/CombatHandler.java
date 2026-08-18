package lampas.levelledmobs.events;

import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
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
     */
    public static float modifyDamage(LivingEntity victim, DamageSource source, float originalDamage) {
        if (originalDamage <= 0 || source == null) {
            return originalDamage;
        }

        Entity attacker = source.getEntity();
        Entity directEntity = source.getDirectEntity();

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

        // 2. Check for explosions caused by levelled mobs (e.g. Creepers)
        if (source.is(DamageTypes.EXPLOSION) && attacker instanceof LivingEntity livingAttacker) {
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

    public static void setProjectileDamageMultiplier(double multiplier) {
        projectileDamageMultiplier = multiplier;
    }

    public static void setExplosionDamageMultiplier(double multiplier) {
        explosionDamageMultiplier = multiplier;
    }
}
