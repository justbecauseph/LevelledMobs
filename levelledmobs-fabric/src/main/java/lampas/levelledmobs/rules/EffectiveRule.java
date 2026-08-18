package lampas.levelledmobs.rules;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable compiled view of all properties resulting from matching and merging applicable LevelRules.
 */
public record EffectiveRule(
    String primaryRuleId,
    List<String> matchedRuleIds,
    IntRange levelRange,
    String strategyName,
    Map<String, Object> strategyConfig,
    Map<String, Object> attributeSettings,
    Map<String, Object> dropSettings,
    Map<String, Object> xpSettings,
    Map<String, Object> nametagSettings
) {
    public static final EffectiveRule DEFAULT = new EffectiveRule(
        "default",
        List.of("default"),
        new IntRange(1, 10),
        "RANDOM",
        Collections.emptyMap(),
        Collections.emptyMap(),
        Collections.emptyMap(),
        Collections.emptyMap(),
        Collections.emptyMap()
    );

    /**
     * Merges a list of sorted matching rules (highest priority first) into a single EffectiveRule.
     */
    public static EffectiveRule merge(List<LevelRule> matchingRules) {
        if (matchingRules == null || matchingRules.isEmpty()) {
            return DEFAULT;
        }

        LevelRule primary = matchingRules.get(0);
        List<String> ruleIds = matchingRules.stream().map(LevelRule::id).toList();

        IntRange levelRange = primary.levelRange();
        String strategyName = primary.strategyName();

        Map<String, Object> mergedStrategy = new HashMap<>();
        Map<String, Object> mergedAttrs = new HashMap<>();
        Map<String, Object> mergedDrops = new HashMap<>();
        Map<String, Object> mergedXp = new HashMap<>();
        Map<String, Object> mergedNametag = new HashMap<>();

        // Merge from lowest priority to highest so higher priority overrides
        for (int i = matchingRules.size() - 1; i >= 0; i--) {
            LevelRule r = matchingRules.get(i);
            mergedStrategy.putAll(r.strategyConfig());
            mergedAttrs.putAll(r.attributeSettings());
            mergedDrops.putAll(r.dropSettings());
            mergedXp.putAll(r.xpSettings());
            mergedNametag.putAll(r.nametagSettings());
        }

        return new EffectiveRule(
            primary.id(),
            ruleIds,
            levelRange,
            strategyName,
            Collections.unmodifiableMap(mergedStrategy),
            Collections.unmodifiableMap(mergedAttrs),
            Collections.unmodifiableMap(mergedDrops),
            Collections.unmodifiableMap(mergedXp),
            Collections.unmodifiableMap(mergedNametag)
        );
    }
}
