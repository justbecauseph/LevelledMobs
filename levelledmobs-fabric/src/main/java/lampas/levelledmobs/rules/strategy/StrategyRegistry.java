package lampas.levelledmobs.rules.strategy;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Registry maintaining available LevelStrategy implementations.
 */
public class StrategyRegistry {
    public static final StrategyRegistry INSTANCE = new StrategyRegistry();

    private final Map<String, LevelStrategy> strategies = new HashMap<>();

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
}
