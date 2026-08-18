package lampas.levelledmobs.api.events;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Event triggered immediately after an entity has been levelled and attributes applied.
 */
public interface MobPostLevelCallback {
    Event<MobPostLevelCallback> EVENT = EventFactory.createArrayBacked(MobPostLevelCallback.class,
        (listeners) -> (entity, level, ruleSet) -> {
            for (MobPostLevelCallback listener : listeners) {
                listener.onPostLevel(entity, level, ruleSet);
            }
        });

    void onPostLevel(LivingEntity entity, int level, String ruleSet);
}
