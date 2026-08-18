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

    public PlayerLevellingStrategy(PlayerLevelProvider provider) {
        this.provider = (provider != null) ? provider : PlayerLevelProvider.VANILLA;
    }

    @Override
    public String name() {
        return "PLAYER";
    }

    @Override
    public int calculateLevel(MobContext context, EffectiveRule rule) {
        Map<String, Object> config = rule.strategyConfig();

        ServerPlayer player = context.nearestPlayer().orElse(null);
        if (player == null) {
            return rule.levelRange().min();
        }

        String variable = getString(config.get("variable"), "%level%");
        float scale = (float) getDouble(config.get("player_variable_scale"), 1.0);
        float rawPlayerLevel = provider.getPlayerLevel(player, variable);
        float scaledPlayerLevel = Math.max(0f, rawPlayerLevel * scale);

        boolean matchVariable = getBoolean(config.get("match_variable"), false);
        boolean variableAsMax = getBoolean(config.get("variable_as_max"), false);

        int level;
        if (matchVariable) {
            level = Math.round(scaledPlayerLevel);
        } else if (variableAsMax) {
            int max = Math.max(rule.levelRange().min(), Math.round(scaledPlayerLevel));
            level = ThreadLocalRandom.current().nextInt(rule.levelRange().min(), max + 1);
        } else {
            List<LevelTierMatching> tiers = parseTiers(config.get("level_tiers"));
            LevelTierMatching matchedTier = null;
            for (LevelTierMatching tier : tiers) {
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
        if (config.containsKey("output_cap")) {
            int cap = getInt(config.get("output_cap"), Integer.MAX_VALUE);
            level = Math.min(level, cap);
        }

        // Apply variance
        int variance = getInt(config.get("variance"), 0);
        if (variance > 0) {
            level += RandomVarianceGenerator.generateVariance(variance, true);
        }

        return rule.levelRange().clamp(level);
    }

    @SuppressWarnings("unchecked")
    private List<LevelTierMatching> parseTiers(Object obj) {
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
