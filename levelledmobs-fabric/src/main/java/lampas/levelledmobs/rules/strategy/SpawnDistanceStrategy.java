package lampas.levelledmobs.rules.strategy;

import lampas.levelledmobs.context.MobContext;
import lampas.levelledmobs.rules.EffectiveRule;
import lampas.levelledmobs.rules.strategy.math.RandomVarianceGenerator;
import net.minecraft.core.BlockPos;

import java.util.Map;

/**
 * Strategy calculating mob level as a linear function of distance from an origin point (e.g. world spawn).
 */
public class SpawnDistanceStrategy implements LevelStrategy {
    public static final SpawnDistanceStrategy INSTANCE = new SpawnDistanceStrategy();

    @Override
    public String name() {
        return "SPAWN_DISTANCE";
    }

    @Override
    public int calculateLevel(MobContext context, EffectiveRule rule) {
        Map<String, Object> config = rule.strategyConfig();

        double originX = getDouble(config.get("origin_x"), 0.0);
        double originZ = getDouble(config.get("origin_z"), 0.0);
        if (config.containsKey("origin") && config.get("origin") instanceof String str) {
            String[] parts = str.split(",");
            if (parts.length >= 2) {
                try {
                    originX = Double.parseDouble(parts[0].trim());
                    originZ = Double.parseDouble(parts[parts.length - 1].trim());
                } catch (NumberFormatException ignored) {}
            }
        }

        double mobX = 0.0;
        double mobZ = 0.0;
        if (context.blockPos() != null) {
            mobX = context.blockPos().getX();
            mobZ = context.blockPos().getZ();
        } else if (context.position() != null) {
            mobX = context.position().x;
            mobZ = context.position().z;
        }

        double distance = Math.hypot(mobX - originX, mobZ - originZ);
        double bufferDistance = getDouble(config.get("buffer_distance"), 0.0);
        double distancePerLevel = getDouble(config.get("distance_per_level"), 100.0);
        if (distancePerLevel <= 0.0) {
            distancePerLevel = getDouble(config.get("ringed_tiers"), 100.0);
        }
        if (distancePerLevel <= 0.0) {
            distancePerLevel = 100.0; // Prevent division by zero
        }

        int baseLevel = getInt(config.get("base_level"), rule.levelRange().min());

        double effectiveDistance = Math.max(0.0, distance - bufferDistance);
        int calculatedLevel = baseLevel + (int) Math.floor(effectiveDistance / distancePerLevel);

        // Apply variance
        int variance = getInt(config.get("variance"), 0);
        if (variance > 0) {
            calculatedLevel += RandomVarianceGenerator.generateVariance(variance, true);
        }

        return rule.levelRange().clamp(calculatedLevel);
    }

    public static int calculate(double distance, double distancePerLevel, int baseLevel, double bufferDistance) {
        if (distancePerLevel <= 0) distancePerLevel = 100.0;
        double effectiveDistance = Math.max(0.0, distance - bufferDistance);
        return baseLevel + (int) Math.floor(effectiveDistance / distancePerLevel);
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
