package lampas.levelledmobs.rules.conditions;

import lampas.levelledmobs.context.MobContext;

/**
 * Evaluates Y-altitude bounds for mob spawning (e.g. subterranean or high-altitude leveling).
 */
public class AltitudeCondition implements RuleCondition {
    private final int minY;
    private final int maxY;

    public AltitudeCondition(int minY, int maxY) {
        this.minY = minY;
        this.maxY = maxY;
    }

    public static AltitudeCondition all() {
        return new AltitudeCondition(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public static AltitudeCondition above(int minY) {
        return new AltitudeCondition(minY, Integer.MAX_VALUE);
    }

    public static AltitudeCondition below(int maxY) {
        return new AltitudeCondition(Integer.MIN_VALUE, maxY);
    }

    public static AltitudeCondition between(int minY, int maxY) {
        return new AltitudeCondition(minY, maxY);
    }

    @Override
    public boolean matches(MobContext context) {
        if (context.blockPos() == null) {
            return true;
        }
        int y = context.blockPos().getY();
        return y >= minY && y <= maxY;
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }
}
