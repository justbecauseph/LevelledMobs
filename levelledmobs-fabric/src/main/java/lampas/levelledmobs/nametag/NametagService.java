package lampas.levelledmobs.nametag;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;

/**
 * Service for managing server-side custom nametags for levelled mobs.
 */
public class NametagService {
    /**
     * Updates the custom name tag on the entity to show its level and type.
     */
    public void updateNametag(LivingEntity entity, int level) {
        if (level <= 0) {
            return;
        }

        MutableComponent levelComponent = Component.literal("Lv. " + level + " ")
            .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD);

        MutableComponent nameComponent = Component.empty()
            .append(levelComponent)
            .append(entity.getType().getDescription().copy().withStyle(ChatFormatting.WHITE));

        entity.setCustomName(nameComponent);
        entity.setCustomNameVisible(true);
    }
}
