package lampas.levelledmobs.attributes;

import net.minecraft.world.entity.LivingEntity;

/**
 * Health adjustment policy applied when mob max health changes due to leveling or chunk reloading.
 */
public enum HealthPolicy {
    FRESH_SPAWN,
    PRESERVE_RATIO,
    KEEP_CURRENT;

    /**
     * Applies the health policy to a living entity given its previous health and previous max health.
     */
    public void apply(LivingEntity entity, float previousHealth, float previousMaxHealth) {
        if (entity == null) return;
        float newMaxHealth = entity.getMaxHealth();

        switch (this) {
            case FRESH_SPAWN -> entity.setHealth(newMaxHealth);
            case PRESERVE_RATIO -> {
                float ratio = (previousMaxHealth > 0) ? (previousHealth / previousMaxHealth) : 1.0f;
                // If mob was at full health, preserve full health
                if (previousHealth >= previousMaxHealth - 0.001f) {
                    entity.setHealth(newMaxHealth);
                } else {
                    entity.setHealth(Math.max(1.0f, Math.min(newMaxHealth, newMaxHealth * ratio)));
                }
            }
            case KEEP_CURRENT -> entity.setHealth(Math.min(newMaxHealth, previousHealth));
        }
    }
}
