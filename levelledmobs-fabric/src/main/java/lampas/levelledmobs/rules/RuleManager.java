package lampas.levelledmobs.rules;

import lampas.levelledmobs.context.MobContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service managing compiled rules and providing thread-safe, atomic rule resolution and reloading.
 */
public class RuleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("LevelledMobs");

    private final AtomicReference<CompiledRules> compiledRules = new AtomicReference<>(CompiledRules.EMPTY);

    public RuleManager() {
        // Initialize with default fallback rule
        LevelRule defaultRule = LevelRule.builder("default")
            .priority(-1000)
            .levelRange(IntRange.of(1, 10))
            .strategy("RANDOM", null)
            .build();
        setRules(List.of(defaultRule));
    }

    /**
     * Resolves the matching effective rule for a given MobContext.
     */
    public RuleResult resolve(MobContext context) {
        if (context == null) {
            return RuleResult.UNMATCHED;
        }
        return compiledRules.get().evaluate(context);
    }

    /**
     * Atomically replaces the active ruleset.
     */
    public void setRules(List<LevelRule> rules) {
        CompiledRules compiled = new CompiledRules(rules);
        compiledRules.set(compiled);
        LOGGER.info("Loaded {} active LevelledMobs rules.", compiled.ruleCount());
    }

    /**
     * Gets the currently active compiled rules.
     */
    public CompiledRules getCompiledRules() {
        return compiledRules.get();
    }

    public int size() {
        return compiledRules.get().ruleCount();
    }

    public boolean isEmpty() {
        return compiledRules.get().ruleCount() == 0;
    }

    public List<LevelRule> getRules() {
        return compiledRules.get().rules();
    }

    public void reload() {
        LOGGER.info("Reloading LevelledMobs rule engine...");
    }
}
