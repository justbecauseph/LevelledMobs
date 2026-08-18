package lampas.levelledmobs.rules;

import lampas.levelledmobs.context.MobContext;
import lampas.levelledmobs.util.RuleCacheKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Immutable compiled rule set with internal thread-safe caching.
 */
public class CompiledRules {
    public static final CompiledRules EMPTY = new CompiledRules(Collections.emptyList());

    private final List<LevelRule> rules;
    private final Map<RuleCacheKey, RuleResult> cache = new ConcurrentHashMap<>();

    public CompiledRules(List<LevelRule> rules) {
        if (rules != null) {
            List<LevelRule> sorted = new ArrayList<>(rules);
            Collections.sort(sorted);
            this.rules = List.copyOf(sorted);
        } else {
            this.rules = Collections.emptyList();
        }
    }

    public RuleResult evaluate(MobContext context) {
        if (context == null) {
            return RuleResult.UNMATCHED;
        }

        RuleCacheKey cacheKey = RuleCacheKey.from(context);
        return cache.computeIfAbsent(cacheKey, k -> computeRule(context));
    }

    private RuleResult computeRule(MobContext context) {
        List<LevelRule> matching = new ArrayList<>();
        for (LevelRule rule : rules) {
            if (rule.matches(context)) {
                matching.add(rule);
            }
        }

        if (matching.isEmpty()) {
            return RuleResult.UNMATCHED;
        }

        EffectiveRule effective = EffectiveRule.merge(matching);
        return RuleResult.matched(effective);
    }

    public List<LevelRule> rules() {
        return rules;
    }

    public int ruleCount() {
        return rules.size();
    }
}
