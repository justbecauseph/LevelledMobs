package lampas.levelledmobs.rules.conditions;

import lampas.levelledmobs.context.MobContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.Set;

/**
 * Evaluates world dimension key conditions.
 */
public class DimensionCondition implements RuleCondition {
    private final Set<ResourceKey<Level>> includeDimensions;
    private final Set<ResourceKey<Level>> excludeDimensions;

    public DimensionCondition(
        Set<ResourceKey<Level>> includeDimensions,
        Set<ResourceKey<Level>> excludeDimensions
    ) {
        this.includeDimensions = includeDimensions != null ? includeDimensions : Collections.emptySet();
        this.excludeDimensions = excludeDimensions != null ? excludeDimensions : Collections.emptySet();
    }

    @Override
    public boolean matches(MobContext context) {
        ResourceKey<Level> dimKey = context.dimensionKey();
        if (dimKey == null) {
            return includeDimensions.isEmpty();
        }

        // Check exclusions
        if (excludeDimensions.contains(dimKey)) {
            return false;
        }

        // If no inclusions specified, matches everything not excluded
        if (includeDimensions.isEmpty()) {
            return true;
        }

        return includeDimensions.contains(dimKey);
    }

    public Set<ResourceKey<Level>> includeDimensions() {
        return includeDimensions;
    }

    public Set<ResourceKey<Level>> excludeDimensions() {
        return excludeDimensions;
    }
}
