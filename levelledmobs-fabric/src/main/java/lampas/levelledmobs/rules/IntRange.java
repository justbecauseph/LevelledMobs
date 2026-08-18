package lampas.levelledmobs.rules;

/**
 * Immutable representation of an inclusive integer range [min, max].
 */
public record IntRange(int min, int max) {
    public static final IntRange DEFAULT = new IntRange(1, 25);

    public IntRange {
        if (min > max) {
            int temp = min;
            min = max;
            max = temp;
        }
    }

    public static IntRange of(int min, int max) {
        return new IntRange(min, max);
    }

    public static IntRange single(int value) {
        return new IntRange(value, value);
    }

    public int clamp(int value) {
        return Math.clamp(value, min, max);
    }

    public boolean contains(int value) {
        return value >= min && value <= max;
    }
}
