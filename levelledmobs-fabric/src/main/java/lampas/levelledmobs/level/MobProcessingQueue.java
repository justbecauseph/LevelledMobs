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
    /** One retry per tick for up to 60 seconds while the managed provider boots. */
    public static final int MAX_DEFERRED_RETRIES = 1_200;
    /** Hard cap on retained entity references waiting for managed context. */
    public static final int MAX_DEFERRED_PENDING = 4_096;

    private final MobLevelingService levelingService;
    private final LongSupplier nanoClock;
    private final Queue<QueueEntry> queue = new ConcurrentLinkedQueue<>();
    private final Set<UUID> queuedEntities = ConcurrentHashMap.newKeySet();
    private final java.util.Map<UUID, Integer> deferredAttempts = new ConcurrentHashMap<>();
    private int maxMobsPerTick = 50;
    private int maxProcessTimeMs = DEFAULT_MAX_PROCESS_TIME_MS;
    private long tickCounter;

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
            queue.add(new QueueEntry(entity, reason, tickCounter, 0));
        }
    }

    /**
     * Requeues a managed entity for the next server tick after a strategy
     * reports that its external creation context is not ready. Retries are
     * capped so an unavailable provider cannot retain entities indefinitely.
     */
    private void defer(LivingEntity entity, SpawnReason reason) {
        if (entity == null || entity.isRemoved() || entity.getUUID() == null) return;
        UUID uuid = entity.getUUID();
        boolean alreadyPending = deferredAttempts.containsKey(uuid);
        int attempt = deferredAttempts.merge(uuid, 1, Integer::sum);
        if (attempt > MAX_DEFERRED_RETRIES) {
            deferredAttempts.remove(uuid);
            levelingService.markQuarantined(entity, "managed creation context did not become available");
            LOGGER.warn("Exhausted deferred LevelledMobs leveling for {} (UUID: {}) after {} retries; mob is quarantined",
                entity.getType().getDescription().getString(), uuid, MAX_DEFERRED_RETRIES);
            return;
        }
        if (!alreadyPending && deferredAttempts.size() > MAX_DEFERRED_PENDING) {
            deferredAttempts.remove(uuid);
            levelingService.markQuarantined(entity, "deferred managed-mob queue capacity reached");
            LOGGER.warn("Quarantined deferred LevelledMobs entity {} (UUID: {}): queue capacity {} reached",
                entity.getType().getDescription().getString(), uuid, MAX_DEFERRED_PENDING);
            return;
        }
        if (queuedEntities.add(uuid)) {
            queue.add(new QueueEntry(entity, reason, tickCounter + 1, attempt));
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

        tickCounter++;

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
            if (entry.readyTick() > tickCounter) {
                queue.add(entry);
                break;
            }
            attempts++;

            LivingEntity entity = entry.entity();
            if (entity != null) {
                UUID entityUuid = entity.getUUID();
                if (entityUuid != null) {
                    queuedEntities.remove(entityUuid);
                }
                if (!entity.isRemoved() && entity.isAlive()) {
                    try {
                        levelingService.onEntityLoad(entity, entry.reason());
                        if (levelingService.consumeDeferred(entity)) {
                            defer(entity, entry.reason());
                        } else {
                            if (entityUuid != null) {
                                deferredAttempts.remove(entityUuid);
                            }
                            processed++;
                        }
                    } catch (Exception e) {
                        if (entityUuid != null) {
                            deferredAttempts.remove(entityUuid);
                        }
                        levelingService.consumeDeferred(entity);
                        LOGGER.error("Failed to process queued entity leveling for {} (UUID: {})",
                            entity.getType().getDescription().getString(), entity.getUUID(), e);
                    }
                } else if (entityUuid != null) {
                    deferredAttempts.remove(entityUuid);
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
        deferredAttempts.clear();
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

    public record QueueEntry(LivingEntity entity, SpawnReason reason, long readyTick, int attempt) {
        public QueueEntry(LivingEntity entity, SpawnReason reason) {
            this(entity, reason, 0L, 0);
        }
    }
}
