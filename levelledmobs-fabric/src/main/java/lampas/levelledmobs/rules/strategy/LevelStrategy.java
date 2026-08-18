package lampas.levelledmobs.rules.strategy;

import lampas.levelledmobs.context.MobContext;
import lampas.levelledmobs.rules.EffectiveRule;

/**
 * Strategy contract for calculating a mob's integer level from context and rule settings.
 */
public interface LevelStrategy {
    String name();

    int calculateLevel(MobContext context, EffectiveRule rule);
}
