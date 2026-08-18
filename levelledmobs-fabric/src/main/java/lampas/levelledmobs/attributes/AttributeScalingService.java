package lampas.levelledmobs.attributes;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Service responsible for applying deterministic, idempotent attribute modifiers based on mob level.
 */
public class AttributeScalingService {
    public static final Identifier HEALTH_MODIFIER_ID = Identifier.fromNamespaceAndPath("lampas", "levelled/max_health");
    public static final Identifier DAMAGE_MODIFIER_ID = Identifier.fromNamespaceAndPath("lampas", "levelled/attack_damage");

    /**
     * Applies level scaling attributes to the target entity.
     * Guaranteed idempotent: cleanly removes any existing modifiers before applying new ones.
     *
     * @param entity The living entity
     * @param level  The mob level (e.g. 1-10+)
     */
    public void applyModifiers(LivingEntity entity, int level) {
        if (level <= 1) {
            removeModifiers(entity);
            return;
        }

        // 1. Max Health scaling: e.g. +2.0 HP (+1 heart) per level above 1
        AttributeInstance maxHealthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            float previousMaxHealth = entity.getMaxHealth();
            float previousHealth = entity.getHealth();
            float healthRatio = previousMaxHealth > 0 ? (previousHealth / previousMaxHealth) : 1.0f;

            maxHealthAttr.removeModifier(HEALTH_MODIFIER_ID);
            double bonusHealth = (level - 1) * 2.0; // 2 HP per level above 1
            AttributeModifier healthModifier = new AttributeModifier(
                HEALTH_MODIFIER_ID,
                bonusHealth,
                AttributeModifier.Operation.ADD_VALUE
            );
            maxHealthAttr.addPermanentModifier(healthModifier);

            // Health policy: if freshly leveled or full HP, set to full new max health; otherwise preserve ratio
            if (previousHealth >= previousMaxHealth - 0.001f) {
                entity.setHealth(entity.getMaxHealth());
            } else {
                entity.setHealth(Math.max(1.0f, entity.getMaxHealth() * healthRatio));
            }
        }

        // 2. Attack Damage scaling: e.g. +0.5 damage per level above 1
        AttributeInstance damageAttr = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr.removeModifier(DAMAGE_MODIFIER_ID);
            double bonusDamage = (level - 1) * 0.5;
            AttributeModifier damageModifier = new AttributeModifier(
                DAMAGE_MODIFIER_ID,
                bonusDamage,
                AttributeModifier.Operation.ADD_VALUE
            );
            damageAttr.addPermanentModifier(damageModifier);
        }
    }

    /**
     * Removes all Lampas attribute modifiers from the entity.
     */
    public void removeModifiers(LivingEntity entity) {
        AttributeInstance maxHealthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.removeModifier(HEALTH_MODIFIER_ID);
        }
        AttributeInstance damageAttr = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr.removeModifier(DAMAGE_MODIFIER_ID);
        }
    }
}
