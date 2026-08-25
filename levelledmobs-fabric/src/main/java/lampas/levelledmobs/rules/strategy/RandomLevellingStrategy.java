package lampas.levelledmobs.rules.strategy;

import lampas.levelledmobs.context.MobContext;
import lampas.levelledmobs.rules.EffectiveRule;
import lampas.levelledmobs.rules.strategy.math.MinAndMax;
import lampas.levelledmobs.rules.strategy.math.RandomVarianceGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Random levelling strategy with support for uniform and weighted distributions.
 */
public class RandomLevellingStrategy implements LevelStrategy {
    public static final RandomLevellingStrategy INSTANCE = new RandomLevellingStrategy();

    public record CompiledRandomConfig(
        List<WeightedEntry> entries,
        int totalWeight,
        int variance
    ) {
        public record WeightedEntry(int min, int max, int weight) {}
    }

    public static CompiledRandomConfig compileConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return new CompiledRandomConfig(List.of(), 0, 0);
        }

        Object weightedObj = config.get("weighted");
        if (weightedObj == null) weightedObj = config.get("weights");
        if (weightedObj == null) weightedObj = config.get("weighted_random");

        List<CompiledRandomConfig.WeightedEntry> entries = new ArrayList<>();
        int totalWeight = 0;

        if (weightedObj instanceof Map<?, ?> weightMap && !weightMap.isEmpty()) {
            for (Map.Entry<?, ?> entry : weightMap.entrySet()) {
                String key = String.valueOf(entry.getKey());
                int weight = getInt(entry.getValue(), 1);
                if (weight <= 0) continue;

                MinAndMax range = MinAndMax.parse(key);
                if (range != null) {
                    entries.add(new CompiledRandomConfig.WeightedEntry(range.minAsInt(), range.maxAsInt(), weight));
                    totalWeight += weight;
                }
            }
        }

        int variance = getInt(config.get("variance"), 0);
        return new CompiledRandomConfig(List.copyOf(entries), totalWeight, variance);
    }

    @Override
    public String name() {
        return "RANDOM";
    }

    @Override
    public int calculateLevel(MobContext context, EffectiveRule rule) {
        int minLevel = rule != null ? rule.levelRange().min() : 1;
        int maxLevel = rule != null ? rule.levelRange().max() : 1;

        if (minLevel >= maxLevel) {
            return minLevel;
        }

        CompiledRandomConfig compiled = (rule != null && rule.compiledStrategyConfig() instanceof CompiledRandomConfig c)
            ? c
            : compileConfig(rule != null ? rule.strategyConfig() : Map.of());

        int level;
        if (!compiled.entries().isEmpty() && compiled.totalWeight() > 0) {
            int roll = ThreadLocalRandom.current().nextInt(0, compiled.totalWeight());
            int cursor = 0;
            int sampled = minLevel;
            for (CompiledRandomConfig.WeightedEntry entry : compiled.entries()) {
                cursor += entry.weight();
                if (roll < cursor) {
                    int min = Math.max(minLevel, entry.min());
                    int max = Math.min(maxLevel, entry.max());
                    sampled = (min >= max) ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
                    break;
                }
            }
            level = sampled;
        } else {
            level = ThreadLocalRandom.current().nextInt(minLevel, maxLevel + 1);
        }

        // Apply variance
        if (compiled.variance() > 0) {
            level += RandomVarianceGenerator.generateVariance(compiled.variance(), true);
        }

        return rule != null ? rule.levelRange().clamp(level) : level;
    }

    private static int getInt(Object obj, int def) {
        if (obj instanceof Number num) return num.intValue();
        if (obj != null) {
            try { return Integer.parseInt(String.valueOf(obj)); } catch (NumberFormatException ignored) {}
        }
        return def;
    }
}
