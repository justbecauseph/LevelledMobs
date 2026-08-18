package lampas.levelledmobs.events;

import lampas.levelledmobs.data.SpawnReason;
import lampas.levelledmobs.level.MobProcessingQueue;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;

/**
 * Handles entity spawn and world load events using Fabric API callbacks.
 */
public class EntityLifecycleHandler {
    private final MobProcessingQueue processingQueue;

    public EntityLifecycleHandler(MobProcessingQueue processingQueue) {
        this.processingQueue = processingQueue;
    }

    public void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof LivingEntity living && living instanceof Monster && !living.isBaby()) {
                processingQueue.enqueue(living, SpawnReason.NATURAL);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            processingQueue.processTick();
        });
    }
}
