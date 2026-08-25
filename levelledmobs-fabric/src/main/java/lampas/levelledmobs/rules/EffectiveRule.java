package lampas.levelledmobs.rules;

import lampas.levelledmobs.attributes.AttributeDefinition;
import lampas.levelledmobs.attributes.CompiledAttributeModifier;
import lampas.levelledmobs.rules.strategy.PlayerLevellingStrategy;
import lampas.levelledmobs.rules.strategy.RandomLevellingStrategy;
import lampas.levelledmobs.rules.strategy.SpawnDistanceStrategy;
import lampas.levelledmobs.rules.strategy.YDistanceStrategy;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    Map<String, Object> nametagSettings,
    List<CompiledAttributeModifier> compiledAttributes,
    Object compiledStrategyConfig
) {
    public EffectiveRule(
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
        this(
            primaryRuleId,
            matchedRuleIds,
            levelRange,
            strategyName,
            strategyConfig,
            attributeSettings,
            dropSettings,
            xpSettings,
            nametagSettings,
            compileAttributes(attributeSettings),
            compileStrategy(strategyName, strategyConfig)
        );
    }

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

    public static List<CompiledAttributeModifier> compileAttributes(Map<String, Object> attributeSettings) {
        List<CompiledAttributeModifier> list = new ArrayList<>(AttributeDefinition.ALL.size());
        for (AttributeDefinition def : AttributeDefinition.ALL) {
            Double explicitValue = null;
            if (attributeSettings != null && attributeSettings.containsKey(def.key())) {
                Object val = attributeSettings.get(def.key());
                if (val instanceof Number num) {
                    explicitValue = num.doubleValue();
                } else if (val != null) {
                    try {
                        explicitValue = Double.parseDouble(String.valueOf(val).trim());
                    } catch (NumberFormatException ignored) {
                        explicitValue = null; // Malformed -> inherit service default
                    }
                }
            }

            AttributeModifier.Operation explicitOp = null;
            String opKey = def.key() + "_operation";
            if (attributeSettings != null && attributeSettings.containsKey(opKey)) {
                Object opObj = attributeSettings.get(opKey);
                if (opObj instanceof AttributeModifier.Operation opEnum) {
                    explicitOp = opEnum;
                } else if (opObj != null) {
                    try {
                        explicitOp = AttributeModifier.Operation.valueOf(String.valueOf(opObj).trim().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ignored) {
                        explicitOp = null; // Malformed -> inherit service default
                    }
                }
            }

            list.add(CompiledAttributeModifier.of(def, explicitValue, explicitOp));
        }
        return List.copyOf(list);
    }

    private static Object compileStrategy(String strategyName, Map<String, Object> config) {
        if (strategyName == null) return null;
        return switch (strategyName.toUpperCase(Locale.ROOT)) {
            case "RANDOM" -> RandomLevellingStrategy.compileConfig(config);
            case "PLAYER" -> PlayerLevellingStrategy.compileConfig(config);
            case "SPAWN_DISTANCE" -> SpawnDistanceStrategy.compileConfig(config);
            case "Y_DISTANCE" -> YDistanceStrategy.compileConfig(config);
            default -> null;
        };
    }
}
