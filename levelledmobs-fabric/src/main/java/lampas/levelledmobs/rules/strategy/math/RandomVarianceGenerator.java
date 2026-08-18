package lampas.levelledmobs.rules.strategy.math;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates bounded random level variance.
 */
public class RandomVarianceGenerator {
    /**
     * Generates a random variance in the range [-maxVariance, +maxVariance] or [0, +maxVariance].
     *
     * @param maxVariance   Maximum variance integer (>= 0)
     * @param allowNegative Whether negative variance is permitted
     * @return Integer variance
     */
    public static int generateVariance(int maxVariance, boolean allowNegative) {
        if (maxVariance <= 0) {
            return 0;
        }

        int delta = ThreadLocalRandom.current().nextInt(0, maxVariance + 1);
        if (allowNegative && ThreadLocalRandom.current().nextBoolean()) {
            return -delta;
        }
        return delta;
    }
}
