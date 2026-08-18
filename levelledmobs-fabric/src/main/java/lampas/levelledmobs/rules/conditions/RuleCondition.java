package lampas.levelledmobs.rules.conditions;

import lampas.levelledmobs.context.MobContext;

/**
 * Evaluates whether a specific rule condition matches the provided MobContext.
 */
@FunctionalInterface
public interface RuleCondition {
    boolean matches(MobContext context);
}
