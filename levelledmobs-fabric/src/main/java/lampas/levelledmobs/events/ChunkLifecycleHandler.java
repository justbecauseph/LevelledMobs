package lampas.levelledmobs.events;

import lampas.levelledmobs.level.MobProcessingQueue;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Handles chunk load lifecycle events.
 */
public class ChunkLifecycleHandler {
    private final MobProcessingQueue processingQueue;

    public ChunkLifecycleHandler(MobProcessingQueue processingQueue) {
        this.processingQueue = processingQueue;
    }

    public void register() {
        ServerChunkEvents.CHUNK_LOAD.register((ServerLevel world, LevelChunk chunk, boolean isNew) -> {
            // Chunk entity validation is handled per-entity via ServerEntityEvents.ENTITY_LOAD
            // and queued through MobProcessingQueue to avoid chunk generation lag spikes.
        });
    }
}
