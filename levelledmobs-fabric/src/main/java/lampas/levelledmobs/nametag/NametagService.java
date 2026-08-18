package lampas.levelledmobs.nametag;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service managing server-side custom nametags and formatting templates for levelled mobs.
 */
public class NametagService {
    private static final Logger LOGGER = LoggerFactory.getLogger("LevelledMobs");

    private NametagTemplate template;
    private NametagVisibility visibility = NametagVisibility.HOVER_ONLY;

    public NametagService() {
        this("<gray>Lv. <yellow><level></yellow> <white><mob_name></white>");
    }

    public NametagService(String templatePattern) {
        this.template = new NametagTemplate(templatePattern);
    }

    /**
     * Updates the custom nametag on the entity to show its level and formatted metadata.
     */
    public void updateNametag(LivingEntity entity, int level) {
        updateNametag(entity, level, "default");
    }

    /**
     * Updates the custom nametag on the entity with level and rule information.
     */
    public void updateNametag(LivingEntity entity, int level, String ruleSet) {
        if (entity == null || level <= 0) {
            return;
        }

        if (visibility == NametagVisibility.NEVER) {
            entity.setCustomName(null);
            entity.setCustomNameVisible(false);
            return;
        }

        try {
            Component nameComponent = template.render(entity, level, ruleSet);
            entity.setCustomName(nameComponent);
            // If ALWAYS, nametag is visible through blocks. If HOVER_ONLY, it only renders when aimed at in direct line of sight.
            entity.setCustomNameVisible(visibility == NametagVisibility.ALWAYS);
        } catch (Exception e) {
            LOGGER.error("Failed to format nametag for entity {} (UUID: {})",
                entity.getType().getDescription().getString(), entity.getUUID(), e);
        }
    }

    public void setTemplate(String templatePattern) {
        this.template = new NametagTemplate(templatePattern);
    }

    public NametagTemplate getTemplate() {
        return template;
    }

    public void setVisibility(NametagVisibility visibility) {
        this.visibility = (visibility != null) ? visibility : NametagVisibility.HOVER_ONLY;
    }

    public NametagVisibility getVisibility() {
        return visibility;
    }

    public void setNametagVisible(boolean visible) {
        this.visibility = visible ? NametagVisibility.ALWAYS : NametagVisibility.HOVER_ONLY;
    }

    public boolean isNametagVisible() {
        return visibility == NametagVisibility.ALWAYS;
    }
}
