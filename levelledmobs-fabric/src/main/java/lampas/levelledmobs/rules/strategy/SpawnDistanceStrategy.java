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

    public record CompiledSpawnDistanceConfig(
        double originX,
        double originZ,
        double bufferDistance,
        double distancePerLevel,
        Integer baseLevel,
        int variance
    ) {}

    public static CompiledSpawnDistanceConfig compileConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return new CompiledSpawnDistanceConfig(0.0, 0.0, 0.0, 100.0, null, 0);
        }
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

        double bufferDistance = getDouble(config.get("buffer_distance"), 0.0);
        double distancePerLevel = getDouble(config.get("distance_per_level"), 100.0);
        if (distancePerLevel <= 0.0) {
            distancePerLevel = getDouble(config.get("ringed_tiers"), 100.0);
        }
        if (distancePerLevel <= 0.0) {
            distancePerLevel = 100.0;
        }

        Integer baseLevel = parseOptionalInt(config.get("base_level"));
        int variance = getInt(config.get("variance"), 0);

        return new CompiledSpawnDistanceConfig(originX, originZ, bufferDistance, distancePerLevel, baseLevel, variance);
    }

    @Override
    public String name() {
        return "SPAWN_DISTANCE";
    }

    @Override
    public int calculateLevel(MobContext context, EffectiveRule rule) {
        CompiledSpawnDistanceConfig compiled = (rule != null && rule.compiledStrategyConfig() instanceof CompiledSpawnDistanceConfig c)
            ? c
            : compileConfig(rule != null ? rule.strategyConfig() : Map.of());

        double mobX = 0.0;
        double mobZ = 0.0;
        if (context != null) {
            if (context.blockPos() != null) {
                mobX = context.blockPos().getX();
                mobZ = context.blockPos().getZ();
            } else if (context.position() != null) {
                mobX = context.position().x;
                mobZ = context.position().z;
            }
        }

        double distance = Math.hypot(mobX - compiled.originX(), mobZ - compiled.originZ());
        int base = (compiled.baseLevel() != null) ? compiled.baseLevel() : ((rule != null) ? rule.levelRange().min() : 1);
        int calculatedLevel = calculate(distance, compiled.distancePerLevel(), base, compiled.bufferDistance());

        // Apply variance
        if (compiled.variance() > 0) {
            calculatedLevel += RandomVarianceGenerator.generateVariance(compiled.variance(), true);
        }

        return (rule != null) ? rule.levelRange().clamp(calculatedLevel) : calculatedLevel;
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

    private static Integer parseOptionalInt(Object obj) {
        if (obj instanceof Number num) return num.intValue();
        if (obj != null) {
            try { return Integer.parseInt(String.valueOf(obj).trim()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static int getInt(Object obj, int def) {
        if (obj instanceof Number num) return num.intValue();
        if (obj != null) {
            try { return Integer.parseInt(String.valueOf(obj)); } catch (NumberFormatException ignored) {}
        }
        return def;
    }
}
