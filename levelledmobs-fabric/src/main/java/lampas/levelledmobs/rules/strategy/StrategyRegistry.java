package lampas.levelledmobs.rules.strategy;

import lampas.levelledmobs.context.MobContext;
import lampas.levelledmobs.rules.EffectiveRule;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;

/**
 * Registry maintaining available LevelStrategy implementations.
 */
public class StrategyRegistry {
    public static final StrategyRegistry INSTANCE = new StrategyRegistry();

    private final Map<String, LevelStrategy> strategies = new HashMap<>();
    private final CopyOnWriteArrayList<StrategyOverride> overrides = new CopyOnWriteArrayList<>();

    public StrategyRegistry() {
        register(RandomLevellingStrategy.INSTANCE);
        register(SpawnDistanceStrategy.INSTANCE);
        register(YDistanceStrategy.INSTANCE);
        register(PlayerLevellingStrategy.INSTANCE);
        register(CustomStrategy.INSTANCE);
    }

    public void register(LevelStrategy strategy) {
        if (strategy != null) {
            strategies.put(strategy.name().toUpperCase(Locale.ROOT), strategy);
        }
    }

    public LevelStrategy getStrategy(String name) {
        if (name == null) {
            return RandomLevellingStrategy.INSTANCE;
        }
        return strategies.getOrDefault(name.toUpperCase(Locale.ROOT), RandomLevellingStrategy.INSTANCE);
    }

    /**
     * Registers an optional context-aware selector. The most recently
     * registered matching selector wins, which lets an integration take
     * ownership of its managed entities while leaving ordinary rules alone.
     */
    public void registerOverride(BiPredicate<MobContext, EffectiveRule> selector, LevelStrategy strategy) {
        if (selector != null && strategy != null) {
            overrides.add(new StrategyOverride(selector, strategy));
        }
    }

    /**
     * Resolves a configured strategy and applies any registered integration
     * override before level calculation. Eligibility and rule merging remain
     * the caller's responsibility.
     */
    public LevelStrategy resolveStrategy(String configuredName, MobContext context, EffectiveRule rule) {
        for (int index = overrides.size() - 1; index >= 0; index--) {
            StrategyOverride override = overrides.get(index);
            if (override.selector().test(context, rule)) {
                return override.strategy();
            }
        }
        return getStrategy(configuredName);
    }

    /** Clears configuration-derived state held by registered strategies. */
    public void clearConfigurationCaches() {
        Set<LevelStrategy> unique = Collections.newSetFromMap(new IdentityHashMap<>());
        unique.addAll(strategies.values());
        for (StrategyOverride override : overrides) {
            unique.add(override.strategy());
        }
        for (LevelStrategy strategy : unique) {
            strategy.clearConfigurationCache();
        }
    }

    private record StrategyOverride(
        BiPredicate<MobContext, EffectiveRule> selector,
        LevelStrategy strategy
    ) {}
}
