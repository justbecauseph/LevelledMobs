package lampas.levelledmobs.rules.strategy.math;

import java.util.Objects;

/**
 * Utility holding a min and max float value, supporting string range parsing (e.g. "10-25" or "5").
 */
public class MinAndMax implements Comparable<MinAndMax> {
    private float min;
    private float max;

    public MinAndMax() {
        this(0f, 0f);
    }

    public MinAndMax(float min, float max) {
        if (min > max) {
            this.min = max;
            this.max = min;
        } else {
            this.min = min;
            this.max = max;
        }
    }

    public static MinAndMax of(float min, float max) {
        return new MinAndMax(min, max);
    }

    public static MinAndMax parse(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String trimmed = input.trim();
        if (!trimmed.contains("-")) {
            try {
                float val = Float.parseFloat(trimmed);
                return new MinAndMax(val, val);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        String[] parts = trimmed.split("-", 2);
        if (parts.length != 2) {
            return null;
        }

        try {
            float minVal = Float.parseFloat(parts[0].trim());
            float maxVal = Float.parseFloat(parts[1].trim());
            return new MinAndMax(minVal, maxVal);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public float min() {
        return min;
    }

    public float max() {
        return max;
    }

    public int minAsInt() {
        return (int) min;
    }

    public int maxAsInt() {
        return (int) max;
    }

    public boolean contains(float value) {
        return value >= min && value <= max;
    }

    public float clamp(float value) {
        return Math.clamp(value, min, max);
    }

    @Override
    public int compareTo(MinAndMax other) {
        if (other == null) return 1;
        int cmp = Float.compare(this.min, other.min);
        if (cmp != 0) return cmp;
        return Float.compare(this.max, other.max);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MinAndMax minAndMax = (MinAndMax) o;
        return Float.compare(minAndMax.min, min) == 0 && Float.compare(minAndMax.max, max) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(min, max);
    }

    @Override
    public String toString() {
        return (min == max) ? String.valueOf(min) : (min + "-" + max);
    }
}
