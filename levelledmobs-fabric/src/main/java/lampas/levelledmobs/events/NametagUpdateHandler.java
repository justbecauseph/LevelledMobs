package lampas.levelledmobs.events;

import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import lampas.levelledmobs.nametag.NametagService;
import net.minecraft.world.entity.LivingEntity;

/**
 * Event-driven nametag update listener that refreshes mob health displays upon taking damage or healing,
 * completely avoiding global per-tick polling loops.
 */
public class NametagUpdateHandler {
    private final NametagService nametagService;

    public NametagUpdateHandler(NametagService nametagService) {
        this.nametagService = nametagService;
    }

    /**
     * Called when a living entity receives damage or heals to update its nametag if it is levelled.
     */
    public void onHealthChanged(LivingEntity entity) {
        if (entity == null || entity.isDeadOrDying()) return;

        if (entity instanceof LevelledMobHolder holder) {
            LevelledMobData data = holder.lampas$getLevelData();
            if (data != null && data.levelled() && data.level() > 0) {
                nametagService.updateNametag(entity, data.level(), data.ruleSet());
            }
        }
    }
}
