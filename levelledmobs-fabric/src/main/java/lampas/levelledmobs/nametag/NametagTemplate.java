package lampas.levelledmobs.nametag;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/**
 * Nametag template parser with dynamic entity placeholder replacements.
 */
public class NametagTemplate {
    private final String template;

    public NametagTemplate(String template) {
        this.template = (template != null && !template.isBlank()) ? template : "<gray>Lv. <yellow><level></yellow> <white><mob_name></white>";
    }

    public Component render(LivingEntity entity, int level, String ruleSet) {
        if (entity == null) {
            return Component.empty();
        }

        String mobName = entity.getType().getDescription().getString();
        float health = entity.getHealth();
        float maxHealth = entity.getMaxHealth();
        int healthPercent = (maxHealth > 0) ? Math.round((health / maxHealth) * 100f) : 100;
        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();

        String formatted = template
            .replace("<level>", String.valueOf(level))
            .replace("%lampas:mob_level%", String.valueOf(level))
            .replace("<mob_name>", mobName)
            .replace("%lampas:mob_name%", mobName)
            .replace("<health>", String.format("%.0f", health))
            .replace("%lampas:mob_health%", String.format("%.0f", health))
            .replace("<max_health>", String.format("%.0f", maxHealth))
            .replace("%lampas:mob_max_health%", String.format("%.0f", maxHealth))
            .replace("<health_percent>", String.valueOf(healthPercent))
            .replace("%lampas:mob_health_percent%", String.valueOf(healthPercent))
            .replace("<rule>", ruleSet != null ? ruleSet : "default")
            .replace("%lampas:mob_rule%", ruleSet != null ? ruleSet : "default")
            .replace("<entity_id>", entityId)
            .replace("%lampas:mob_entity_id%", entityId);

        return TextFormatter.format(formatted);
    }

    public String template() {
        return template;
    }
}
