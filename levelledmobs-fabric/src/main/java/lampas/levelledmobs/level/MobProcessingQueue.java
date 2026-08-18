package lampas.levelledmobs.level;

import lampas.levelledmobs.data.SpawnReason;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Processing queue that buffers mob leveling requests and executes them with a strict per-tick budget,
 * eliminating TPS lag spikes during bulk chunk loads or mob spawner bursts.
 */
public class MobProcessingQueue {
    private static final Logger LOGGER = LoggerFactory.getLogger("LevelledMobs");

    private final MobLevelingService levelingService;
    private final Queue<QueueEntry> queue = new ConcurrentLinkedQueue<>();
    private int maxMobsPerTick = 50;

    public MobProcessingQueue(MobLevelingService levelingService) {
        this.levelingService = levelingService;
    }

    /**
     * Enqueues an entity for leveling processing.
     */
    public void enqueue(LivingEntity entity, SpawnReason reason) {
        if (entity == null || entity.isRemoved()) {
            return;
        }
        queue.add(new QueueEntry(entity, reason));
    }

    /**
     * Processes up to maxMobsPerTick queued entities on the server main thread.
     * Returns the count of mobs processed in this tick.
     */
    public int processTick() {
        if (queue.isEmpty()) {
            return 0;
        }

        int processed = 0;
        while (processed < maxMobsPerTick && !queue.isEmpty()) {
            QueueEntry entry = queue.poll();
            if (entry == null) break;

            LivingEntity entity = entry.entity();
            if (entity != null && !entity.isRemoved() && entity.isAlive()) {
                try {
                    levelingService.onEntityLoad(entity, entry.reason());
                    processed++;
                } catch (Exception e) {
                    LOGGER.error("Failed to process queued entity leveling for {} (UUID: {})",
                        entity.getType().getDescription().getString(), entity.getUUID(), e);
                }
            }
        }

        return processed;
    }

    public int size() {
        return queue.size();
    }

    public void clear() {
        queue.clear();
    }

    public int maxMobsPerTick() {
        return maxMobsPerTick;
    }

    public void setMaxMobsPerTick(int max) {
        this.maxMobsPerTick = Math.max(1, max);
    }

    public record QueueEntry(LivingEntity entity, SpawnReason reason) {}
}
