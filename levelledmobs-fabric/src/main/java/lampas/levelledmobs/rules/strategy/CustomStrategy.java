package lampas.levelledmobs.rules.strategy;

import lampas.levelledmobs.context.MobContext;
import lampas.levelledmobs.rules.EffectiveRule;
import lampas.levelledmobs.rules.strategy.math.LevelTierMatching;
import lampas.levelledmobs.rules.strategy.math.MinAndMax;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Custom formula and tier-based levelling strategy.
 */
public class CustomStrategy implements LevelStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger("LevelledMobs");
    public static final CustomStrategy INSTANCE = new CustomStrategy();

    @Override
    public String name() {
        return "CUSTOM";
    }

    @Override
    public int calculateLevel(MobContext context, EffectiveRule rule) {
        Map<String, Object> config = rule.strategyConfig();

        // 1. Check if tier matching list is supplied
        if (config.containsKey("tiers") && config.get("tiers") instanceof List<?> list) {
            double distance = calculateDistance(context);
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Float min = map.containsKey("min") ? (float) getDouble(map.get("min"), 0) : null;
                    Float max = map.containsKey("max") ? (float) getDouble(map.get("max"), Float.MAX_VALUE) : null;
                    String targetStr = map.containsKey("target") ? String.valueOf(map.get("target")) : "1-10";
                    MinAndMax target = MinAndMax.parse(targetStr);
                    if (target != null && new LevelTierMatching(null, min, max, target).matches((float) distance, null)) {
                        int minL = target.minAsInt();
                        int maxL = target.maxAsInt();
                        int level = (maxL > minL) ? ThreadLocalRandom.current().nextInt(minL, maxL + 1) : minL;
                        return rule.levelRange().clamp(level);
                    }
                }
            }
        }

        // 2. Evaluate formula string if present
        String formula = String.valueOf(config.getOrDefault("formula", ""));
        if (!formula.isBlank()) {
            double result = evaluateFormula(formula, context, rule);
            int level = (int) Math.round(result);
            return rule.levelRange().clamp(level);
        }

        return rule.levelRange().min();
    }

    public double evaluateFormula(String formula, MobContext context, EffectiveRule rule) {
        if (formula == null || formula.isBlank()) {
            return rule.levelRange().min();
        }

        double distance = calculateDistance(context);
        double y = context.blockPos() != null ? context.blockPos().getY() : (context.position() != null ? context.position().y : 64);
        double minLevel = rule.levelRange().min();
        double maxLevel = rule.levelRange().max();

        // Replace variable tokens
        String expr = formula
            .replace("%distance%", String.valueOf(distance))
            .replace("<distance>", String.valueOf(distance))
            .replace("%y%", String.valueOf(y))
            .replace("<y>", String.valueOf(y))
            .replace("%min_level%", String.valueOf(minLevel))
            .replace("<min_level>", String.valueOf(minLevel))
            .replace("%max_level%", String.valueOf(maxLevel))
            .replace("<max_level>", String.valueOf(maxLevel));

        try {
            return evalSimpleExpression(expr);
        } catch (Exception e) {
            LOGGER.warn("Failed to evaluate custom strategy formula '{}' (parsed as '{}')", formula, expr, e);
            return minLevel;
        }
    }

    private double calculateDistance(MobContext context) {
        if (context.blockPos() != null) {
            return Math.hypot(context.blockPos().getX(), context.blockPos().getZ());
        }
        if (context.position() != null) {
            return Math.hypot(context.position().x, context.position().z);
        }
        return 0.0;
    }

    /**
     * Simple recursive-descent parser for basic arithmetic (+, -, *, /, parens, numbers).
     */
    public static double evalSimpleExpression(final String str) {
        return new Object() {
            int pos = -1, ch;

            void nextChar() {
                ch = (++pos < str.length()) ? str.charAt(pos) : -1;
            }

            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) {
                    nextChar();
                    return true;
                }
                return false;
            }

            double parse() {
                nextChar();
                double x = parseExpression();
                if (pos < str.length()) throw new RuntimeException("Unexpected: " + (char) ch);
                return x;
            }

            double parseExpression() {
                double x = parseTerm();
                for (;;) {
                    if      (eat('+')) x += parseTerm();
                    else if (eat('-')) x -= parseTerm();
                    else return x;
                }
            }

            double parseTerm() {
                double x = parseFactor();
                for (;;) {
                    if      (eat('*')) x *= parseFactor();
                    else if (eat('/')) {
                        double divisor = parseFactor();
                        x = (divisor != 0.0) ? (x / divisor) : 0.0; // Graceful divide by zero handling
                    }
                    else return x;
                }
            }

            double parseFactor() {
                if (eat('+')) return +parseFactor();
                if (eat('-')) return -parseFactor();

                double x;
                int startPos = this.pos;
                if (eat('(')) {
                    x = parseExpression();
                    eat(')');
                } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(str.substring(startPos, this.pos));
                } else if (ch >= 'a' && ch <= 'z') {
                    while (ch >= 'a' && ch <= 'z') nextChar();
                    String func = str.substring(startPos, this.pos);
                    x = parseFactor();
                    if (func.equals("floor")) x = Math.floor(x);
                    else if (func.equals("ceil")) x = Math.ceil(x);
                    else if (func.equals("round")) x = Math.round(x);
                    else if (func.equals("abs")) x = Math.abs(x);
                    else if (func.equals("sqrt")) x = Math.sqrt(x);
                    else throw new RuntimeException("Unknown function: " + func);
                } else {
                    throw new RuntimeException("Unexpected char: " + (char) ch);
                }

                return x;
            }
        }.parse();
    }

    private static double getDouble(Object obj, double def) {
        if (obj instanceof Number num) return num.doubleValue();
        if (obj != null) {
            try { return Double.parseDouble(String.valueOf(obj)); } catch (NumberFormatException ignored) {}
        }
        return def;
    }
}
