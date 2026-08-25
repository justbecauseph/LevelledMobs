package lampas.levelledmobs.nametag;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nametag template parser with dynamic entity placeholder replacements.
 */
public class NametagTemplate {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("<(/?[#a-zA-Z0-9_]+)>|(%lampas:[a-zA-Z0-9_]+%)");

    private final String template;
    private final List<Token> tokens;
    private final boolean hasLevel;
    private final boolean hasMobName;
    private final boolean hasHealth;
    private final boolean hasMaxHealth;
    private final boolean hasHealthPercent;
    private final boolean hasRule;
    private final boolean hasEntityId;

    public enum PlaceholderType {
        LEVEL,
        MOB_NAME,
        HEALTH,
        MAX_HEALTH,
        HEALTH_PERCENT,
        RULE,
        ENTITY_ID
    }

    public sealed interface Token permits StaticToken, PlaceholderToken {}
    public record StaticToken(String text, Style style, Component component) implements Token {}
    public record PlaceholderToken(PlaceholderType type, Style style) implements Token {}

    public NametagTemplate(String template) {
        this.template = (template != null && !template.isBlank()) ? template : "<gray>Lv. <yellow><level></yellow> <white><mob_name></white>";
        this.tokens = compileTokens(this.template);

        boolean level = false;
        boolean mobName = false;
        boolean health = false;
        boolean maxHealth = false;
        boolean healthPercent = false;
        boolean rule = false;
        boolean entityId = false;

        for (Token t : tokens) {
            if (t instanceof PlaceholderToken pt) {
                switch (pt.type()) {
                    case LEVEL -> level = true;
                    case MOB_NAME -> mobName = true;
                    case HEALTH -> health = true;
                    case MAX_HEALTH -> maxHealth = true;
                    case HEALTH_PERCENT -> healthPercent = true;
                    case RULE -> rule = true;
                    case ENTITY_ID -> entityId = true;
                }
            }
        }

        this.hasLevel = level;
        this.hasMobName = mobName;
        this.hasHealth = health;
        this.hasMaxHealth = maxHealth;
        this.hasHealthPercent = healthPercent;
        this.hasRule = rule;
        this.hasEntityId = entityId;
    }

    private static List<Token> compileTokens(String rawTemplate) {
        String translated = TextFormatter.translateLegacy(rawTemplate);
        List<Token> list = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(translated);

        int lastIndex = 0;
        TextColor currentColor = null;
        Style currentStyle = Style.EMPTY;

        while (matcher.find()) {
            int start = matcher.start();
            if (start > lastIndex) {
                String textSegment = translated.substring(lastIndex, start);
                list.add(new StaticToken(textSegment, currentStyle, Component.literal(textSegment).setStyle(currentStyle)));
            }

            String tag = matcher.group(1);
            String placeholder = matcher.group(2);

            if (tag != null) {
                String lowerTag = tag.toLowerCase(Locale.ROOT);
                PlaceholderType type = getPlaceholderType(lowerTag);
                if (type != null) {
                    list.add(new PlaceholderToken(type, currentStyle));
                } else if (lowerTag.startsWith("/")) {
                    currentColor = null;
                    currentStyle = Style.EMPTY;
                } else {
                    currentColor = TextFormatter.resolveColor(lowerTag);
                    currentStyle = (currentColor != null) ? Style.EMPTY.withColor(currentColor) : Style.EMPTY;
                }
            } else if (placeholder != null) {
                String lowerPlaceholder = placeholder.toLowerCase(Locale.ROOT);
                PlaceholderType type = getPercentPlaceholderType(lowerPlaceholder);
                if (type != null) {
                    list.add(new PlaceholderToken(type, currentStyle));
                } else {
                    list.add(new StaticToken(placeholder, currentStyle, Component.literal(placeholder).setStyle(currentStyle)));
                }
            }

            lastIndex = matcher.end();
        }

        if (lastIndex < translated.length()) {
            String trailing = translated.substring(lastIndex);
            list.add(new StaticToken(trailing, currentStyle, Component.literal(trailing).setStyle(currentStyle)));
        }

        return List.copyOf(list);
    }

    private static PlaceholderType getPlaceholderType(String tag) {
        return switch (tag) {
            case "level" -> PlaceholderType.LEVEL;
            case "mob_name" -> PlaceholderType.MOB_NAME;
            case "health" -> PlaceholderType.HEALTH;
            case "max_health" -> PlaceholderType.MAX_HEALTH;
            case "health_percent" -> PlaceholderType.HEALTH_PERCENT;
            case "rule" -> PlaceholderType.RULE;
            case "entity_id" -> PlaceholderType.ENTITY_ID;
            default -> null;
        };
    }

    private static PlaceholderType getPercentPlaceholderType(String placeholder) {
        return switch (placeholder) {
            case "%lampas:mob_level%" -> PlaceholderType.LEVEL;
            case "%lampas:mob_name%" -> PlaceholderType.MOB_NAME;
            case "%lampas:mob_health%" -> PlaceholderType.HEALTH;
            case "%lampas:mob_max_health%" -> PlaceholderType.MAX_HEALTH;
            case "%lampas:mob_health_percent%" -> PlaceholderType.HEALTH_PERCENT;
            case "%lampas:mob_rule%" -> PlaceholderType.RULE;
            case "%lampas:mob_entity_id%" -> PlaceholderType.ENTITY_ID;
            default -> null;
        };
    }

    public Component render(LivingEntity entity, int level, String ruleSet) {
        if (entity == null) {
            return Component.empty();
        }

        String mobName = hasMobName ? entity.getType().getDescription().getString() : null;
        float health = (hasHealth || hasHealthPercent) ? entity.getHealth() : 0.0f;
        float maxHealth = (hasMaxHealth || hasHealthPercent) ? entity.getMaxHealth() : 0.0f;
        int healthPercent = hasHealthPercent ? ((maxHealth > 0) ? Math.round((health / maxHealth) * 100f) : 100) : 0;
        String entityId = hasEntityId ? BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString() : null;
        String rule = hasRule ? (ruleSet != null ? ruleSet : "default") : null;

        String levelStr = hasLevel ? Integer.toString(level) : null;
        String healthStr = hasHealth ? Integer.toString(Math.round(health)) : null;
        String maxHealthStr = hasMaxHealth ? Integer.toString(Math.round(maxHealth)) : null;
        String healthPercentStr = hasHealthPercent ? Integer.toString(healthPercent) : null;

        MutableComponent root = Component.empty();
        for (Token token : tokens) {
            if (token instanceof StaticToken st) {
                root.append(st.component());
            } else if (token instanceof PlaceholderToken pt) {
                String val = switch (pt.type()) {
                    case LEVEL -> levelStr;
                    case MOB_NAME -> mobName;
                    case HEALTH -> healthStr;
                    case MAX_HEALTH -> maxHealthStr;
                    case HEALTH_PERCENT -> healthPercentStr;
                    case RULE -> rule;
                    case ENTITY_ID -> entityId;
                };
                if (val != null) {
                    root.append(Component.literal(val).setStyle(pt.style()));
                }
            }
        }

        return root;
    }

    public String template() {
        return template;
    }
}
