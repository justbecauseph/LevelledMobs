package lampas.levelledmobs.attributes;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Precomputed typed attribute scaling data for an attribute definition with independent
 * value and operation override tracking.
 */
public record CompiledAttributeModifier(
    AttributeDefinition definition,
    double perLevel,
    boolean hasExplicitValue,
    AttributeModifier.Operation operation,
    boolean hasExplicitOperation
) {
    public CompiledAttributeModifier(
        AttributeDefinition definition,
        double perLevel,
        AttributeModifier.Operation operation
    ) {
        this(definition, perLevel, true, operation, true);
    }

    public boolean isExplicit() {
        return hasExplicitValue || hasExplicitOperation;
    }

    public static CompiledAttributeModifier explicit(
        AttributeDefinition definition,
        double perLevel,
        AttributeModifier.Operation operation
    ) {
        return new CompiledAttributeModifier(definition, perLevel, true, operation, true);
    }

    public static CompiledAttributeModifier of(
        AttributeDefinition definition,
        Double explicitValue,
        AttributeModifier.Operation explicitOperation
    ) {
        double val = (explicitValue != null) ? explicitValue : 0.0;
        boolean hasVal = (explicitValue != null);
        AttributeModifier.Operation op = (explicitOperation != null) ? explicitOperation : definition.defaultOperation();
        boolean hasOp = (explicitOperation != null);
        return new CompiledAttributeModifier(definition, val, hasVal, op, hasOp);
    }

    public static CompiledAttributeModifier inherited(AttributeDefinition definition) {
        return new CompiledAttributeModifier(definition, 0.0, false, definition.defaultOperation(), false);
    }
}
