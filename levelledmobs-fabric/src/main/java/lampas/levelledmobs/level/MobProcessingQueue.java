package lampas.levelledmobs.level;

import lampas.levelledmobs.data.SpawnReason;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.LongSupplier;

/**
 * Processing queue that buffers mob leveling requests and executes them with a strict per-tick budget,
 * eliminating TPS lag spikes during bulk chunk loads or mob spawner bursts.
 */
public class MobProcessingQueue {
    private static final Logger LOGGER = LoggerFactory.getLogger("LevelledMobs");
    public static final int DEFAULT_MAX_PROCESS_TIME_MS = 2;

    private final MobLevelingService levelingService;
    private final LongSupplier nanoClock;
    private final Queue<QueueEntry> queue = new ConcurrentLinkedQueue<>();
    private final Set<UUID> queuedEntities = ConcurrentHashMap.newKeySet();
    private int maxMobsPerTick = 50;
    private int maxProcessTimeMs = DEFAULT_MAX_PROCESS_TIME_MS;

    public MobProcessingQueue(MobLevelingService levelingService) {
        this(levelingService, System::nanoTime);
    }

    public MobProcessingQueue(MobLevelingService levelingService, LongSupplier nanoClock) {
        this.levelingService = levelingService;
        this.nanoClock = (nanoClock != null) ? nanoClock : System::nanoTime;
    }

    /**
     * Enqueues an entity for leveling processing with duplicate rejection.
     */
    public void enqueue(LivingEntity entity, SpawnReason reason) {
        if (entity == null || entity.isRemoved()) {
            return;
        }
        UUID uuid = entity.getUUID();
        if (uuid == null || queuedEntities.add(uuid)) {
            queue.add(new QueueEntry(entity, reason));
        }
    }

    /**
     * Processes queued entities on the server main thread up to maxMobsPerTick and maxProcessTimeMs attempts,
     * guaranteeing at least one dequeue when non-empty (forward progress).
     * Returns the count of mobs successfully processed in this tick.
     */
    public int processTick() {
        if (queue.isEmpty()) {
            return 0;
        }

        long startNs = nanoClock.getAsLong();
        long budgetNs = (long) maxProcessTimeMs * 1_000_000L;
        int attempts = 0;
        int processed = 0;

        while (attempts < maxMobsPerTick && !queue.isEmpty()) {
            if (attempts > 0 && (nanoClock.getAsLong() - startNs) >= budgetNs) {
                break;
            }

            QueueEntry entry = queue.poll();
            if (entry == null) break;
            attempts++;

            LivingEntity entity = entry.entity();
            if (entity != null) {
                if (entity.getUUID() != null) {
                    queuedEntities.remove(entity.getUUID());
                }
                if (!entity.isRemoved() && entity.isAlive()) {
                    try {
                        levelingService.onEntityLoad(entity, entry.reason());
                        processed++;
                    } catch (Exception e) {
                        LOGGER.error("Failed to process queued entity leveling for {} (UUID: {})",
                            entity.getType().getDescription().getString(), entity.getUUID(), e);
                    }
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
        queuedEntities.clear();
    }

    public int maxMobsPerTick() {
        return maxMobsPerTick;
    }

    public void setMaxMobsPerTick(int max) {
        this.maxMobsPerTick = Math.max(1, max);
    }

    public int maxProcessTimeMs() {
        return maxProcessTimeMs;
    }

    public void setMaxProcessTimeMs(int maxMs) {
        this.maxProcessTimeMs = Math.max(1, maxMs);
    }

    public record QueueEntry(LivingEntity entity, SpawnReason reason) {}
}
