package lampas.levelledmobs.drops;

import java.util.Map;

/**
 * Service calculating scaled experience point rewards upon mob death.
 */
public class XpScalingService {
    private static double multiplierPerLevel = 0.10; // +10% XP per level above 1
    private static int minXp = 1;
    private static int maxXp = 5000;

    /**
     * Calculates the scaled XP reward for a given base XP and mob level.
     */
    public static int calculateXp(int baseXp, int level) {
        if (baseXp <= 0) return 0;
        if (level <= 1) return baseXp;

        double scaled = baseXp * (1.0 + (level - 1) * multiplierPerLevel);
        return Math.clamp((int) Math.round(scaled), minXp, maxXp);
    }

    public static int calculateXp(int baseXp, int level, Map<String, Object> config) {
        if (baseXp <= 0) return 0;
        if (level <= 1) return baseXp;

        if (config == null || config.isEmpty()) {
            return calculateXp(baseXp, level);
        }

        double multiplier = getDouble(config.get("xp_multiplier"), multiplierPerLevel);
        double additive = getDouble(config.get("xp_additive"), 0.0);
        int min = getInt(config.get("min_xp"), minXp);
        int max = getInt(config.get("max_xp"), maxXp);

        double scaled = (baseXp * (1.0 + (level - 1) * multiplier)) + ((level - 1) * additive);
        return Math.clamp((int) Math.round(scaled), min, max);
    }

    public static void setMultiplierPerLevel(double multiplier) {
        multiplierPerLevel = multiplier;
    }

    private static double getDouble(Object obj, double def) {
        if (obj instanceof Number num) return num.doubleValue();
        if (obj != null) {
            try { return Double.parseDouble(String.valueOf(obj)); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private static int getInt(Object obj, int def) {
        if (obj instanceof Number num) return num.intValue();
        if (obj != null) {
            try { return Integer.parseInt(String.valueOf(obj)); } catch (NumberFormatException ignored) {}
        }
        return def;
    }
}
