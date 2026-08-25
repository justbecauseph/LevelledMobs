package lampas.levelledmobs.rules.strategy;

import lampas.levelledmobs.context.MobContext;
import lampas.levelledmobs.rules.EffectiveRule;
import lampas.levelledmobs.rules.strategy.math.LevelTierMatching;
import lampas.levelledmobs.rules.strategy.math.MinAndMax;
import lampas.levelledmobs.rules.strategy.math.RandomVarianceGenerator;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Strategy scaling mob levels dynamically relative to nearby player progress / stats.
 */
public class PlayerLevellingStrategy implements LevelStrategy {
    public static final PlayerLevellingStrategy INSTANCE = new PlayerLevellingStrategy(PlayerLevelProvider.VANILLA);

    private final PlayerLevelProvider provider;

    public record CompiledPlayerConfig(
        String variable,
        float scale,
        boolean matchVariable,
        boolean variableAsMax,
        List<LevelTierMatching> tiers,
        Integer outputCap,
        int variance
    ) {}

    public PlayerLevellingStrategy(PlayerLevelProvider provider) {
        this.provider = (provider != null) ? provider : PlayerLevelProvider.VANILLA;
    }

    public static CompiledPlayerConfig compileConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return new CompiledPlayerConfig("%level%", 1.0f, false, false, List.of(), null, 0);
        }
        String variable = getString(config.get("variable"), "%level%");
        float scale = (float) getDouble(config.get("player_variable_scale"), 1.0);
        boolean matchVariable = getBoolean(config.get("match_variable"), false);
        boolean variableAsMax = getBoolean(config.get("variable_as_max"), false);
        List<LevelTierMatching> tiers = parseTiers(config.get("level_tiers"));
        Integer outputCap = config.containsKey("output_cap") ? getInt(config.get("output_cap"), Integer.MAX_VALUE) : null;
        int variance = getInt(config.get("variance"), 0);

        return new CompiledPlayerConfig(variable, scale, matchVariable, variableAsMax, List.copyOf(tiers), outputCap, variance);
    }

    @Override
    public String name() {
        return "PLAYER";
    }

    @Override
    public int calculateLevel(MobContext context, EffectiveRule rule) {
        ServerPlayer player = (context != null) ? context.nearestPlayer().orElse(null) : null;
        if (player == null) {
            return (rule != null) ? rule.levelRange().min() : 1;
        }

        CompiledPlayerConfig compiled = (rule != null && rule.compiledStrategyConfig() instanceof CompiledPlayerConfig c)
            ? c
            : compileConfig(rule != null ? rule.strategyConfig() : Map.of());

        float rawPlayerLevel = provider.getPlayerLevel(player, compiled.variable());
        float scaledPlayerLevel = Math.max(0f, rawPlayerLevel * compiled.scale());

        int level;
        if (compiled.matchVariable()) {
            level = Math.round(scaledPlayerLevel);
        } else if (compiled.variableAsMax()) {
            int minL = (rule != null) ? rule.levelRange().min() : 1;
            int max = Math.max(minL, Math.round(scaledPlayerLevel));
            level = ThreadLocalRandom.current().nextInt(minL, max + 1);
        } else {
            LevelTierMatching matchedTier = null;
            for (LevelTierMatching tier : compiled.tiers()) {
                if (tier.matches(scaledPlayerLevel, null)) {
                    matchedTier = tier;
                    break;
                }
            }

            if (matchedTier != null) {
                int min = matchedTier.targetRange().minAsInt();
                int max = matchedTier.targetRange().maxAsInt();
                level = (max > min) ? ThreadLocalRandom.current().nextInt(min, max + 1) : min;
            } else {
                level = Math.round(scaledPlayerLevel);
            }
        }

        // Apply output cap if set
        if (compiled.outputCap() != null) {
            level = Math.min(level, compiled.outputCap());
        }

        // Apply variance
        if (compiled.variance() > 0) {
            level += RandomVarianceGenerator.generateVariance(compiled.variance(), true);
        }

        return (rule != null) ? rule.levelRange().clamp(level) : level;
    }

    private static List<LevelTierMatching> parseTiers(Object obj) {
        if (!(obj instanceof List<?> list)) {
            return List.of();
        }
        List<LevelTierMatching> tiers = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Float min = map.containsKey("min") ? (float) getDouble(map.get("min"), 0) : null;
                Float max = map.containsKey("max") ? (float) getDouble(map.get("max"), Float.MAX_VALUE) : null;
                String targetStr = map.containsKey("target") ? String.valueOf(map.get("target")) : "1-10";
                MinAndMax target = MinAndMax.parse(targetStr);
                tiers.add(new LevelTierMatching(null, min, max, target));
            }
        }
        return tiers;
    }

    private static String getString(Object obj, String def) {
        return obj != null ? String.valueOf(obj) : def;
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
