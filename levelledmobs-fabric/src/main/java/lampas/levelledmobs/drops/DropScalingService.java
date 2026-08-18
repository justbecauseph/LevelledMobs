package lampas.levelledmobs.drops;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Service calculating item drop quantity scaling for vanilla mob drops.
 */
public class DropScalingService {
    private static double itemDropMultiplierPerLevel = 0.05; // +5% extra items per level above 1

    /**
     * Calculates the scaled item drop quantity given the original drop count and mob level.
     */
    public static int calculateDropAmount(int baseAmount, int level) {
        if (baseAmount <= 0) return 0;
        if (level <= 1) return baseAmount;

        double multiplier = 1.0 + (level - 1) * itemDropMultiplierPerLevel;
        double expected = baseAmount * multiplier;

        int fullAmount = (int) Math.floor(expected);
        double remainder = expected - fullAmount;

        if (remainder > 0 && ThreadLocalRandom.current().nextDouble() < remainder) {
            fullAmount++;
        }

        return Math.max(1, fullAmount);
    }

    public static void setItemDropMultiplierPerLevel(double multiplier) {
        itemDropMultiplierPerLevel = multiplier;
    }
}
