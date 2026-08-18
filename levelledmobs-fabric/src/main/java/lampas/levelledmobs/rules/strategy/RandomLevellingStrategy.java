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

    @Override
    public String name() {
        return "RANDOM";
    }

    @Override
    public int calculateLevel(MobContext context, EffectiveRule rule) {
        int minLevel = rule.levelRange().min();
        int maxLevel = rule.levelRange().max();

        if (minLevel >= maxLevel) {
            return minLevel;
        }

        Map<String, Object> config = rule.strategyConfig();
        int level;

        Object weightedObj = config.get("weighted");
        if (weightedObj == null) weightedObj = config.get("weights");
        if (weightedObj == null) weightedObj = config.get("weighted_random");

        if (weightedObj instanceof Map<?, ?> weightMap && !weightMap.isEmpty()) {
            level = sampleWeighted(weightMap, minLevel, maxLevel);
        } else {
            level = ThreadLocalRandom.current().nextInt(minLevel, maxLevel + 1);
        }

        // Apply variance
        int variance = getInt(config.get("variance"), 0);
        if (variance > 0) {
            level += RandomVarianceGenerator.generateVariance(variance, true);
        }

        return rule.levelRange().clamp(level);
    }

    private int sampleWeighted(Map<?, ?> weightMap, int minLevel, int maxLevel) {
        List<WeightedEntry> entries = new ArrayList<>();
        int totalWeight = 0;

        for (Map.Entry<?, ?> entry : weightMap.entrySet()) {
            String key = String.valueOf(entry.getKey());
            int weight = getInt(entry.getValue(), 1);
            if (weight <= 0) continue;

            MinAndMax range = MinAndMax.parse(key);
            if (range != null) {
                entries.add(new WeightedEntry(range.minAsInt(), range.maxAsInt(), weight));
                totalWeight += weight;
            }
        }

        if (entries.isEmpty() || totalWeight <= 0) {
            return ThreadLocalRandom.current().nextInt(minLevel, maxLevel + 1);
        }

        int roll = ThreadLocalRandom.current().nextInt(0, totalWeight);
        int cursor = 0;

        for (WeightedEntry entry : entries) {
            cursor += entry.weight;
            if (roll < cursor) {
                int min = Math.max(minLevel, entry.min);
                int max = Math.min(maxLevel, entry.max);
                if (min >= max) return min;
                return ThreadLocalRandom.current().nextInt(min, max + 1);
            }
        }

        return minLevel;
    }

    private static int getInt(Object obj, int def) {
        if (obj instanceof Number num) return num.intValue();
        if (obj != null) {
            try { return Integer.parseInt(String.valueOf(obj)); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private record WeightedEntry(int min, int max, int weight) {}
}
