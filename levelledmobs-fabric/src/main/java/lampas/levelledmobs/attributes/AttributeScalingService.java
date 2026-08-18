package lampas.levelledmobs.attributes;

import lampas.levelledmobs.rules.EffectiveRule;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;

/**
 * Service for managing deterministic, idempotent attribute scaling across all supported entity attributes.
 */
public class AttributeScalingService {
    private static final Logger LOGGER = LoggerFactory.getLogger("LevelledMobs");

    public static final net.minecraft.resources.Identifier HEALTH_MODIFIER_ID = AttributeDefinition.MAX_HEALTH.modifierId();
    public static final net.minecraft.resources.Identifier DAMAGE_MODIFIER_ID = AttributeDefinition.ATTACK_DAMAGE.modifierId();

    private final Map<String, AttributeFormula> defaultFormulas = new java.util.HashMap<>();

    public AttributeScalingService() {
        // Configure default scaling formulas (+2.0 health/lvl, +0.5 damage/lvl, +1% speed/lvl, +0.5 armor/lvl)
        defaultFormulas.put(AttributeDefinition.MAX_HEALTH.key(), AttributeFormula.simpleAddition(AttributeDefinition.MAX_HEALTH, 2.0));
        defaultFormulas.put(AttributeDefinition.ATTACK_DAMAGE.key(), AttributeFormula.simpleAddition(AttributeDefinition.ATTACK_DAMAGE, 0.5));
        defaultFormulas.put(AttributeDefinition.MOVEMENT_SPEED.key(), AttributeFormula.simpleMultiplier(AttributeDefinition.MOVEMENT_SPEED, 0.01));
        defaultFormulas.put(AttributeDefinition.ARMOR.key(), AttributeFormula.simpleAddition(AttributeDefinition.ARMOR, 0.5));
        defaultFormulas.put(AttributeDefinition.ARMOR_TOUGHNESS.key(), AttributeFormula.simpleAddition(AttributeDefinition.ARMOR_TOUGHNESS, 0.25));
        defaultFormulas.put(AttributeDefinition.KNOCKBACK_RESISTANCE.key(), AttributeFormula.simpleAddition(AttributeDefinition.KNOCKBACK_RESISTANCE, 0.01));
        defaultFormulas.put(AttributeDefinition.ATTACK_KNOCKBACK.key(), AttributeFormula.simpleAddition(AttributeDefinition.ATTACK_KNOCKBACK, 0.05));
        defaultFormulas.put(AttributeDefinition.FOLLOW_RANGE.key(), AttributeFormula.simpleAddition(AttributeDefinition.FOLLOW_RANGE, 0.5));
    }

    /**
     * Removes all LevelledMobs attribute modifiers across all supported attributes from an entity.
     */
    public void removeModifiers(LivingEntity entity) {
        if (entity == null) return;
        for (AttributeDefinition def : AttributeDefinition.ALL) {
            AttributeInstance instance = entity.getAttribute(def.attribute());
            if (instance != null) {
                instance.removeModifier(def.modifierId());
            }
        }
    }

    /**
     * Applies attribute modifiers for a given level using default formulas and PRESERVE_RATIO health policy.
     */
    public void applyModifiers(LivingEntity entity, int level) {
        applyModifiers(entity, level, null, HealthPolicy.PRESERVE_RATIO);
    }

    /**
     * Applies attribute modifiers for a given level with an explicit health policy.
     */
    public void applyModifiers(LivingEntity entity, int level, HealthPolicy healthPolicy) {
        applyModifiers(entity, level, null, healthPolicy);
    }

    /**
     * Applies attribute modifiers for a given level, extracting custom formulas from the effective rule if present.
     */
    public void applyModifiers(LivingEntity entity, int level, EffectiveRule rule, HealthPolicy healthPolicy) {
        if (entity == null) return;
        HealthPolicy policy = (healthPolicy != null) ? healthPolicy : HealthPolicy.PRESERVE_RATIO;

        float prevHealth = entity.getHealth();
        float prevMaxHealth = entity.getMaxHealth();

        // 1. Remove all previous LevelledMobs attribute modifiers across all 8 attributes (idempotency guarantee)
        for (AttributeDefinition def : AttributeDefinition.ALL) {
            AttributeInstance instance = entity.getAttribute(def.attribute());
            if (instance != null) {
                instance.removeModifier(def.modifierId());
            }
        }

        // 2. If level <= 1, restoring to baseline is complete
        if (level <= 1) {
            policy.apply(entity, prevHealth, prevMaxHealth);
            return;
        }

        // 3. Apply modifiers for each attribute
        Map<String, Object> ruleAttributes = (rule != null) ? rule.attributeSettings() : Map.of();

        for (AttributeDefinition def : AttributeDefinition.ALL) {
            AttributeInstance instance = entity.getAttribute(def.attribute());
            if (instance == null) continue;

            double perLevel = getPerLevelValue(def, ruleAttributes);
            if (perLevel == 0.0) continue;

            AttributeModifier.Operation operation = getOperation(def, ruleAttributes);
            double modifierValue = (level - 1) * perLevel;

            if (modifierValue != 0.0) {
                AttributeModifier modifier = new AttributeModifier(
                    def.modifierId(),
                    modifierValue,
                    operation
                );
                instance.addPermanentModifier(modifier);
            }
        }

        // 4. Update health according to health policy
        policy.apply(entity, prevHealth, prevMaxHealth);

        LOGGER.debug("Applied Lv. {} attribute modifiers to {} (UUID: {}) with HealthPolicy: {}",
            level, entity.getType().getDescription().getString(), entity.getUUID(), policy);
    }

    private double getPerLevelValue(AttributeDefinition def, Map<String, Object> ruleAttributes) {
        if (ruleAttributes.containsKey(def.key())) {
            Object val = ruleAttributes.get(def.key());
            if (val instanceof Number num) return num.doubleValue();
            try { return Double.parseDouble(String.valueOf(val)); } catch (NumberFormatException ignored) {}
        }
        AttributeFormula defaultFormula = defaultFormulas.get(def.key());
        return defaultFormula != null ? defaultFormula.perLevelValue() : 0.0;
    }

    private AttributeModifier.Operation getOperation(AttributeDefinition def, Map<String, Object> ruleAttributes) {
        String opKey = def.key() + "_operation";
        if (ruleAttributes.containsKey(opKey)) {
            String opStr = String.valueOf(ruleAttributes.get(opKey));
            try {
                return AttributeModifier.Operation.valueOf(opStr.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        AttributeFormula defaultFormula = defaultFormulas.get(def.key());
        return defaultFormula != null ? defaultFormula.operation() : def.defaultOperation();
    }

    public void setDefaultFormula(String attributeKey, AttributeFormula formula) {
        if (attributeKey != null && formula != null) {
            defaultFormulas.put(attributeKey, formula);
        }
    }
}
