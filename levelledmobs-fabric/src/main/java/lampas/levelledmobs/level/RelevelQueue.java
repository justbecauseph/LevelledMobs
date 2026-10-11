package lampas.levelledmobs.level;

import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Budgeted queue of levelled mobs awaiting a managed re-tier. Entries are deduplicated by entity UUID, hold the
 * entity weakly, and are processed on the server thread at most {@link #DEFAULT_MAX_PER_TICK} per tick.
 *
 * <p>A mob whose managed creation context is temporarily unavailable is requeued for the next tick, up to
 * {@link #MAX_DEFERRED_ATTEMPTS} times. Each requeued mob is evaluated at most once per tick. A retry never
 * quarantines the mob or changes its level or data; when the retries run out the mob is dropped and keeps its
 * current level.</p>
 */
public final class RelevelQueue {
    private static final Logger LOGGER = LoggerFactory.getLogger("LevelledMobs");
    public static final int DEFAULT_MAX_PER_TICK = 32;
    /** Requeues allowed for a mob whose managed creation context stays unavailable. */
    public static final int MAX_DEFERRED_ATTEMPTS = 1_200;

    private final MobLevelingService levelingService;
    private final int maxPerTick;
    private final Queue<Entry> queue = new ConcurrentLinkedQueue<>();
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    public RelevelQueue(MobLevelingService levelingService) {
        this(levelingService, DEFAULT_MAX_PER_TICK);
    }

    public RelevelQueue(MobLevelingService levelingService, int maxPerTick) {
        this.levelingService = levelingService;
        this.maxPerTick = Math.max(1, maxPerTick);
    }

    /**
     * Queues an entity for re-evaluation. Returns true only when the entity was newly queued.
     */
    public boolean enqueue(LivingEntity entity) {
        return add(entity, 0);
    }

    /**
     * Re-evaluates up to {@link #DEFAULT_MAX_PER_TICK} queued entities. Only entries queued before this call are
     * considered, so an entity requeued for a retry waits for the next tick. Removed, dead or collected entities are
     * dropped without being evaluated. Returns the number of live entities that were evaluated.
     */
    public int processTick() {
        int budget = Math.min(maxPerTick, queue.size());
        int evaluated = 0;
        for (int i = 0; i < budget; i++) {
            Entry entry = queue.poll();
            if (entry == null) {
                break;
            }
            pending.remove(entry.uuid());

            LivingEntity entity = entry.ref().get();
            if (entity == null || entity.isRemoved() || !entity.isAlive()) {
                continue;
            }
            try {
                MobLevelingService.RelevelOutcome outcome = levelingService.relevelOutcome(entity);
                evaluated++;
                if (outcome == MobLevelingService.RelevelOutcome.DEFERRED) {
                    requeueDeferred(entity, entry.attempt());
                }
            } catch (RuntimeException ex) {
                LOGGER.error("Failed to re-tier managed LevelledMobs entity (UUID: {})", entry.uuid(), ex);
            }
        }
        return evaluated;
    }

    private void requeueDeferred(LivingEntity entity, int attempt) {
        int nextAttempt = attempt + 1;
        if (nextAttempt > MAX_DEFERRED_ATTEMPTS) {
            LOGGER.warn("Dropped managed re-tier of LevelledMobs entity (UUID: {}) after {} deferred attempts; "
                + "the mob keeps its current level", entity.getUUID(), MAX_DEFERRED_ATTEMPTS);
            return;
        }
        add(entity, nextAttempt);
    }

    private boolean add(LivingEntity entity, int attempt) {
        if (entity == null || entity.isRemoved()) {
            return false;
        }
        UUID uuid = entity.getUUID();
        if (uuid == null || !pending.add(uuid)) {
            return false;
        }
        queue.add(new Entry(uuid, new WeakReference<>(entity), attempt));
        return true;
    }

    public int size() {
        return queue.size();
    }

    public void clear() {
        queue.clear();
        pending.clear();
    }

    public int maxPerTick() {
        return maxPerTick;
    }

    private record Entry(UUID uuid, WeakReference<LivingEntity> ref, int attempt) {}
}
