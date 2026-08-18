package lampas.levelledmobs.attributes;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import java.util.HashMap;
import java.util.Map;

/**
 * Snapshot of entity attribute base values and calculated values.
 */
public record AttributeSnapshot(
    float currentHealth,
    float maxHealth,
    Map<String, Double> baseValues,
    Map<String, Double> totalValues
) {
    public static AttributeSnapshot capture(LivingEntity entity) {
        if (entity == null) {
            return new AttributeSnapshot(0f, 0f, Map.of(), Map.of());
        }

        Map<String, Double> bases = new HashMap<>();
        Map<String, Double> totals = new HashMap<>();

        for (AttributeDefinition def : AttributeDefinition.ALL) {
            AttributeInstance instance = entity.getAttribute(def.attribute());
            if (instance != null) {
                bases.put(def.key(), instance.getBaseValue());
                totals.put(def.key(), instance.getValue());
            }
        }

        return new AttributeSnapshot(
            entity.getHealth(),
            entity.getMaxHealth(),
            Map.copyOf(bases),
            Map.copyOf(totals)
        );
    }
}
