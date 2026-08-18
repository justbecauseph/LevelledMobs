package lampas.levelledmobs.attributes;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Formula defining how an attribute scales per level.
 */
public record AttributeFormula(
    AttributeDefinition definition,
    double perLevelValue,
    AttributeModifier.Operation operation,
    double minValue,
    double maxValue
) {
    public static AttributeFormula simpleAddition(AttributeDefinition def, double perLevel) {
        return new AttributeFormula(def, perLevel, AttributeModifier.Operation.ADD_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE);
    }

    public static AttributeFormula simpleMultiplier(AttributeDefinition def, double perLevel) {
        return new AttributeFormula(def, perLevel, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, -Double.MAX_VALUE, Double.MAX_VALUE);
    }

    public double calculateModifierValue(int level) {
        if (level <= 1) {
            return 0.0;
        }
        double rawBonus = (level - 1) * perLevelValue;
        return Math.clamp(rawBonus, minValue, maxValue);
    }
}
