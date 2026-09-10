package lampas.levelledmobs.attributes;

import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobModifier;
import lampas.levelledmobs.rules.EffectiveRule;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing deterministic, idempotent attribute scaling across all supported entity attributes.
 */
public class AttributeScalingService {
    private static final Logger LOGGER = LoggerFactory.getLogger("LevelledMobs");

    public static final net.minecraft.resources.Identifier HEALTH_MODIFIER_ID = AttributeDefinition.MAX_HEALTH.modifierId();
    public static final net.minecraft.resources.Identifier DAMAGE_MODIFIER_ID = AttributeDefinition.ATTACK_DAMAGE.modifierId();

    private final Map<String, AttributeFormula> defaultFormulas = new ConcurrentHashMap<>();
    private volatile List<CompiledAttributeModifier> cachedDefaultPlan;

    public AttributeScalingService() {
        // Configure standard default scaling formulas (+2.0 health/lvl, +0.5 damage/lvl, +1% speed/lvl, +0.5 armor/lvl, etc.)
        defaultFormulas.put(AttributeDefinition.MAX_HEALTH.key(), AttributeFormula.simpleAddition(AttributeDefinition.MAX_HEALTH, 2.0));
        defaultFormulas.put(AttributeDefinition.ATTACK_DAMAGE.key(), AttributeFormula.simpleAddition(AttributeDefinition.ATTACK_DAMAGE, 0.5));
        defaultFormulas.put(AttributeDefinition.MOVEMENT_SPEED.key(), AttributeFormula.simpleMultiplier(AttributeDefinition.MOVEMENT_SPEED, 0.01));
        defaultFormulas.put(AttributeDefinition.ARMOR.key(), AttributeFormula.simpleAddition(AttributeDefinition.ARMOR, 0.5));
        defaultFormulas.put(AttributeDefinition.ARMOR_TOUGHNESS.key(), AttributeFormula.simpleAddition(AttributeDefinition.ARMOR_TOUGHNESS, 0.25));
        defaultFormulas.put(AttributeDefinition.KNOCKBACK_RESISTANCE.key(), AttributeFormula.simpleAddition(AttributeDefinition.KNOCKBACK_RESISTANCE, 0.01));
        defaultFormulas.put(AttributeDefinition.ATTACK_KNOCKBACK.key(), AttributeFormula.simpleAddition(AttributeDefinition.ATTACK_KNOCKBACK, 0.05));
        // follow_range has NO default scaling formula (implicit growth removed; 0.0 unless explicitly configured in rules or on this service)
        rebuildDefaultPlan();
    }

    private synchronized void rebuildDefaultPlan() {
        this.cachedDefaultPlan = createPlan(this.defaultFormulas);
    }

    /**
     * Removes all LevelledMobs attribute modifiers across all supported attributes from an entity.
     */
    public void removeModifiers(LivingEntity entity) {
        if (entity == null) return;
        for (AttributeDefinition def : AttributeDefinition.ALL) {
            AttributeInstance instance = entity.getAttribute(def.attribute());
            if (instance != null && instance.hasModifier(def.modifierId())) {
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
     * Applies attribute modifiers for a given level, resolving explicit rule overrides or fallback default formulas.
     */
    public void applyModifiers(LivingEntity entity, int level, EffectiveRule rule, HealthPolicy healthPolicy) {
        if (entity == null) return;
        applyModifiers(entity, resolveEffectiveModifierPlan(level, rule), healthPolicy);
    }

    /**
     * Resolves the exact modifier outcome for a newly-created mob.
     *
     * <p>All supported definitions are included, including zero amounts, so a
     * persisted plan can also remove a stale LevelledMobs modifier without
     * changing unrelated attribute modifiers.</p>
     */
    public List<LevelledMobModifier> resolveEffectiveModifierPlan(int level, EffectiveRule rule) {
        List<CompiledAttributeModifier> rulePlan = (rule != null) ? rule.compiledAttributes() : null;
        List<CompiledAttributeModifier> fallbackPlan = this.cachedDefaultPlan;
        List<LevelledMobModifier> result = new ArrayList<>(AttributeDefinition.ALL.size());

        for (int i = 0; i < AttributeDefinition.ALL.size(); i++) {
            CompiledAttributeModifier ruleMod = (rulePlan != null && i < rulePlan.size()) ? rulePlan.get(i) : null;
            CompiledAttributeModifier fallbackMod = fallbackPlan.get(i);
            AttributeDefinition def = fallbackMod.definition();

            double perLevel = (ruleMod != null && ruleMod.hasExplicitValue())
                ? ruleMod.perLevel()
                : fallbackMod.perLevel();
            AttributeModifier.Operation operation = (ruleMod != null && ruleMod.hasExplicitOperation())
                ? ruleMod.operation()
                : fallbackMod.operation();
            double amount = (level > 1) ? ((level - 1) * perLevel) : 0.0;

            result.add(new LevelledMobModifier(
                def.key(),
                def.modifierId().toString(),
                amount,
                operation.getSerializedName()
            ));
        }
        return List.copyOf(result);
    }

    /** Applies a resolved plan while retaining the existing public scaling API. */
    public void applyModifiers(LivingEntity entity, List<LevelledMobModifier> plan, HealthPolicy healthPolicy) {
        if (entity == null || plan == null) return;
        HealthPolicy policy = (healthPolicy != null) ? healthPolicy : HealthPolicy.PRESERVE_RATIO;

        Map<String, LevelledMobModifier> byKey = new HashMap<>();
        for (LevelledMobModifier modifier : plan) {
            if (modifier != null) {
                byKey.putIfAbsent(modifier.attributeKey(), modifier);
            }
        }
        applyModifierMap(entity, byKey, policy);
    }

    /**
     * Restores a current-format plan exactly. Legacy or malformed plans return
     * false and leave all existing attributes untouched.
     */
    public boolean restorePersistedModifiers(LivingEntity entity, LevelledMobData data) {
        if (entity == null || data == null || !data.hasPersistedModifiers()) {
            return false;
        }
        if (data.effectiveModifiers().size() != AttributeDefinition.ALL.size()) {
            return false;
        }

        Map<String, LevelledMobModifier> byKey = new HashMap<>();
        for (LevelledMobModifier modifier : data.effectiveModifiers()) {
            if (modifier == null || !Double.isFinite(modifier.amount()) ||
                byKey.putIfAbsent(modifier.attributeKey(), modifier) != null) {
                return false;
            }
        }

        for (AttributeDefinition def : AttributeDefinition.ALL) {
            LevelledMobModifier modifier = byKey.get(def.key());
            if (modifier == null || !def.modifierId().toString().equals(modifier.modifierId()) ||
                parseOperation(modifier.operation()) == null) {
                return false;
            }
        }

        applyModifierMap(entity, byKey, HealthPolicy.PRESERVE_RATIO);
        return true;
    }

    private void applyModifierMap(
        LivingEntity entity,
        Map<String, LevelledMobModifier> modifiers,
        HealthPolicy healthPolicy
    ) {
        float prevHealth = entity.getHealth();
        float prevMaxHealth = entity.getMaxHealth();

        for (AttributeDefinition def : AttributeDefinition.ALL) {
            LevelledMobModifier persisted = modifiers.get(def.key());
            if (persisted == null) continue;

            AttributeInstance instance = entity.getAttribute(def.attribute());
            if (instance == null) continue;

            AttributeModifier.Operation desiredOp = parseOperation(persisted.operation());
            if (desiredOp == null) continue;
            applyModifier(instance, def, persisted.amount(), desiredOp);
        }

        healthPolicy.apply(entity, prevHealth, prevMaxHealth);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Applied persisted LevelledMobs attribute modifiers to {} (UUID: {}) with HealthPolicy: {}",
                entity.getType().getDescription().getString(), entity.getUUID(), healthPolicy);
        }
    }

    private static void applyModifier(
        AttributeInstance instance,
        AttributeDefinition def,
        double desiredValue,
        AttributeModifier.Operation desiredOp
    ) {
        AttributeModifier existing = instance.getModifier(def.modifierId());
        if (desiredValue != 0.0) {
            if (existing != null) {
                if (Double.compare(existing.amount(), desiredValue) == 0 && existing.operation() == desiredOp) {
                    return; // Matching modifier already present; avoid stacking or churn.
                }
                instance.removeModifier(def.modifierId());
            }
            instance.addPermanentModifier(new AttributeModifier(def.modifierId(), desiredValue, desiredOp));
        } else if (existing != null) {
            instance.removeModifier(def.modifierId());
        }
    }

    private static AttributeModifier.Operation parseOperation(String serialized) {
        if (serialized == null) return null;
        for (AttributeModifier.Operation operation : AttributeModifier.Operation.values()) {
            if (operation.name().equalsIgnoreCase(serialized) ||
                operation.getSerializedName().equalsIgnoreCase(serialized)) {
                return operation;
            }
        }
        return null;
    }

    public synchronized void setDefaultFormula(String attributeKey, AttributeFormula formula) {
        if (attributeKey != null && formula != null) {
            defaultFormulas.put(attributeKey, formula);
            rebuildDefaultPlan();
        }
    }

    public List<CompiledAttributeModifier> getDefaultPlan() {
        return cachedDefaultPlan;
    }

    public static List<CompiledAttributeModifier> createPlan(Map<String, AttributeFormula> formulas) {
        List<CompiledAttributeModifier> list = new ArrayList<>(AttributeDefinition.ALL.size());
        for (AttributeDefinition def : AttributeDefinition.ALL) {
            AttributeFormula formula = (formulas != null) ? formulas.get(def.key()) : null;
            double perLevel = (formula != null) ? formula.perLevelValue() : 0.0;
            AttributeModifier.Operation op = (formula != null) ? formula.operation() : def.defaultOperation();
            list.add(CompiledAttributeModifier.explicit(def, perLevel, op));
        }
        return List.copyOf(list);
    }
}
