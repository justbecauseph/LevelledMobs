package lampas.levelledmobs.rules.strategy;

import lampas.levelledmobs.context.MobContext;
import lampas.levelledmobs.rules.EffectiveRule;

/**
 * Strategy contract for calculating a mob's integer level from context and rule settings.
 */
public interface LevelStrategy {
    String name();

    int calculateLevel(MobContext context, EffectiveRule rule);

    /**
     * Applies an integration-owned bound to a positive calculated level.
     * Ordinary strategies keep their existing result; managed strategies can
     * return zero to reject a result that is outside trusted creation data.
     */
    default int constrainLevel(MobContext context, EffectiveRule rule, int level) {
        return level;
    }

    /**
     * Clears state derived from the current LevelledMobs configuration.
     * Stateless strategies keep the default no-op implementation.
     */
    default void clearConfigurationCache() {}

    /**
     * Reports whether a non-positive level is a temporary external-context
     * deferral. Strategies that return zero for a permanent unknown or invalid
     * context leave this false so the owning service can quarantine the mob
     * instead of retrying it indefinitely.
     */
    default boolean shouldRetryDeferredLevel(MobContext context, EffectiveRule rule) {
        return false;
    }
}
