package lampas.levelledmobs.rules.strategy;

import lampas.levelledmobs.context.MobContext;
import lampas.levelledmobs.rules.EffectiveRule;
import lampas.levelledmobs.rules.strategy.math.RandomVarianceGenerator;

import java.util.Map;

/**
 * Strategy scaling mob level by Y altitude (depth scaling subterranean or height scaling alpine).
 */
public class YDistanceStrategy implements LevelStrategy {
    public static final YDistanceStrategy INSTANCE = new YDistanceStrategy();

    public record CompiledYDistanceConfig(
        int startY,
        int endY,
        boolean isDescending,
        double increasePerLevel,
        double yPeriod,
        int variance
    ) {}

    public static CompiledYDistanceConfig compileConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return new CompiledYDistanceConfig(64, -64, true, 0.0, 0.0, 0);
        }
        int startY = getInt(config.get("starting_y"), getInt(config.get("start_y"), 64));
        int endY = getInt(config.get("ending_y"), getInt(config.get("end_y"), -64));
        boolean isDescending = config.containsKey("scale_downward") ? getBoolean(config.get("scale_downward"), true) : (startY > endY);
        double increasePerLevel = getDouble(config.get("increase_per_level"), getDouble(config.get("blocks_per_level"), 0.0));
        double yPeriod = getDouble(config.get("y_period"), 0.0);
        int variance = getInt(config.get("variance"), 0);

        return new CompiledYDistanceConfig(startY, endY, isDescending, increasePerLevel, yPeriod, variance);
    }

    @Override
    public String name() {
        return "Y_DISTANCE";
    }

    @Override
    public int calculateLevel(MobContext context, EffectiveRule rule) {
        CompiledYDistanceConfig compiled = (rule != null && rule.compiledStrategyConfig() instanceof CompiledYDistanceConfig c)
            ? c
            : compileConfig(rule != null ? rule.strategyConfig() : Map.of());

        int mobY = (context != null && context.blockPos() != null)
            ? context.blockPos().getY()
            : (context != null && context.position() != null ? (int) context.position().y : 64);

        int minLevel = (rule != null) ? rule.levelRange().min() : 1;
        int maxLevel = (rule != null) ? rule.levelRange().max() : 1;
        int calculatedLevel;

        double deltaY = compiled.isDescending() ? (compiled.startY() - mobY) : (mobY - compiled.startY());
        deltaY = Math.max(0.0, deltaY);

        if (compiled.increasePerLevel() > 0.0) {
            calculatedLevel = minLevel + (int) Math.floor(deltaY / compiled.increasePerLevel());
        } else if (compiled.yPeriod() > 0.0) {
            double lvlPerPeriod = (double) (maxLevel - minLevel) / compiled.yPeriod();
            calculatedLevel = minLevel + (int) Math.floor(lvlPerPeriod * (deltaY / compiled.yPeriod()));
        } else {
            double diff = Math.max(1.0, Math.abs(compiled.startY() - compiled.endY()));
            double percent = Math.min(1.0, deltaY / diff);
            calculatedLevel = minLevel + (int) Math.floor((maxLevel - minLevel) * percent);
        }

        // Apply variance
        if (compiled.variance() > 0) {
            calculatedLevel += RandomVarianceGenerator.generateVariance(compiled.variance(), true);
        }

        return (rule != null) ? rule.levelRange().clamp(calculatedLevel) : calculatedLevel;
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

    private static boolean getBoolean(Object obj, boolean def) {
        if (obj instanceof Boolean b) return b;
        if (obj != null) return Boolean.parseBoolean(String.valueOf(obj));
        return def;
    }
}
