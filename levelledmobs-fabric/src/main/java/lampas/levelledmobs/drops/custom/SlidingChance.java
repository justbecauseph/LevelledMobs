package lampas.levelledmobs.drops.custom;

import lampas.levelledmobs.rules.strategy.math.MinAndMax;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Evaluates variable drop chances based on flat probability, level steps, or tier tables.
 */
public class SlidingChance {
    private double baseChance = 0.0;
    private double chancePerLevel = 0.0;
    private final Map<MinAndMax, Double> tierChances = new LinkedHashMap<>();

    public SlidingChance(double baseChance) {
        this.baseChance = baseChance;
    }

    public static SlidingChance flat(double chance) {
        return new SlidingChance(chance);
    }

    public static SlidingChance scaling(double baseChance, double perLevel) {
        SlidingChance sc = new SlidingChance(baseChance);
        sc.chancePerLevel = perLevel;
        return sc;
    }

    public void addTier(MinAndMax levelRange, double chance) {
        if (levelRange != null) {
            tierChances.put(levelRange, chance);
        }
    }

    public double getChance(int level) {
        if (!tierChances.isEmpty()) {
            for (Map.Entry<MinAndMax, Double> entry : tierChances.entrySet()) {
                if (entry.getKey().contains(level)) {
                    return entry.getValue();
                }
            }
        }

        double chance = baseChance + (Math.max(0, level - 1) * chancePerLevel);
        return Math.clamp(chance, 0.0, 1.0);
    }

    public boolean roll(int level) {
        double chance = getChance(level);
        if (chance <= 0.0) return false;
        if (chance >= 1.0) return true;
        return ThreadLocalRandom.current().nextDouble() < chance;
    }
}
