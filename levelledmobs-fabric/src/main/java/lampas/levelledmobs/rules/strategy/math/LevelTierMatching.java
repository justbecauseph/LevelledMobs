package lampas.levelledmobs.rules.strategy.math;

import java.util.Objects;

/**
 * Matches source tiers/levels to target level ranges.
 */
public class LevelTierMatching {
    private String sourceTierName;
    private Float minLevel;
    private Float maxLevel;
    private MinAndMax targetRange;

    public LevelTierMatching(String sourceTierName, Float minLevel, Float maxLevel, MinAndMax targetRange) {
        this.sourceTierName = sourceTierName;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.targetRange = targetRange != null ? targetRange : new MinAndMax(1, 1);
    }

    public static LevelTierMatching ofRange(float min, float max, MinAndMax target) {
        return new LevelTierMatching(null, min, max, target);
    }

    public static LevelTierMatching ofNamedTier(String name, MinAndMax target) {
        return new LevelTierMatching(name, null, null, target);
    }

    public boolean matches(float sourceLevel, String sourceName) {
        if (sourceTierName != null && sourceName != null) {
            return sourceTierName.equalsIgnoreCase(sourceName);
        }

        boolean meetsMin = (minLevel == null || sourceLevel >= minLevel);
        boolean meetsMax = (maxLevel == null || sourceLevel <= maxLevel);
        return meetsMin && meetsMax;
    }

    public String sourceTierName() {
        return sourceTierName;
    }

    public Float minLevel() {
        return minLevel;
    }

    public Float maxLevel() {
        return maxLevel;
    }

    public MinAndMax targetRange() {
        return targetRange;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LevelTierMatching that = (LevelTierMatching) o;
        return Objects.equals(sourceTierName, that.sourceTierName) &&
            Objects.equals(minLevel, that.minLevel) &&
            Objects.equals(maxLevel, that.maxLevel) &&
            Objects.equals(targetRange, that.targetRange);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceTierName, minLevel, maxLevel, targetRange);
    }

    @Override
    public String toString() {
        if (sourceTierName != null) {
            return sourceTierName + " -> " + targetRange;
        }
        return minLevel + ".." + maxLevel + " -> " + targetRange;
    }
}
