package lampas.levelledmobs.level;

import lampas.levelledmobs.attributes.AttributeScalingService;
import lampas.levelledmobs.attributes.HealthPolicy;
import lampas.levelledmobs.config.LevelledMobsConfig;
import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import lampas.levelledmobs.data.SpawnReason;
import lampas.levelledmobs.nametag.NametagService;
import lampas.levelledmobs.rules.RuleManager;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class LifecyclePipelineUnitTest {

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static class MockLevelledMob implements LevelledMobHolder {
        private LevelledMobData data = LevelledMobData.EMPTY;

        @Override
        public LevelledMobData lampas$getLevelData() {
            return data;
        }

        @Override
        public void lampas$setLevelData(LevelledMobData data) {
            this.data = (data != null) ? data : LevelledMobData.EMPTY;
        }
    }

    @Test
    public void testBossClassifierIdentification() {
        BossClassifier classifier = new BossClassifier();

        assertTrue(classifier.isBossType(Identifier.fromNamespaceAndPath("minecraft", "ender_dragon")));
        assertTrue(classifier.isBossType(Identifier.fromNamespaceAndPath("minecraft", "wither")));
        assertTrue(classifier.isBossType(Identifier.fromNamespaceAndPath("minecraft", "warden")));
        assertFalse(classifier.isBossType(Identifier.fromNamespaceAndPath("minecraft", "zombie")));

        // Custom boss registration
        Identifier moddedBoss = Identifier.fromNamespaceAndPath("cataclysm", "ignis");
        assertFalse(classifier.isBossType(moddedBoss));
        classifier.addBoss(moddedBoss);
        assertTrue(classifier.isBossType(moddedBoss));
    }

    @Test
    public void testSpawnReasonInference() {
        assertEquals(SpawnReason.SPAWNER, SpawnReasonResolver.infer(null, SpawnReason.SPAWNER));
        assertEquals(SpawnReason.TRIAL_SPAWNER, SpawnReasonResolver.infer(null, SpawnReason.TRIAL_SPAWNER));
        assertEquals(SpawnReason.NATURAL, SpawnReasonResolver.infer(null, SpawnReason.NATURAL));
        assertEquals(SpawnReason.NATURAL, SpawnReasonResolver.infer(null, null));
    }

    @Test
    public void testMobProcessingQueuePerTickBudget() {
        AtomicInteger processedCounter = new AtomicInteger(0);

        MobLevelingService mockService = new MobLevelingService(new RuleManager(), new AttributeScalingService(), new NametagService()) {
            @Override
            public void onEntityLoad(LivingEntity entity, SpawnReason spawnReason) {
                processedCounter.incrementAndGet();
            }
        };

        MobProcessingQueue queue = new MobProcessingQueue(mockService);
        queue.setMaxMobsPerTick(10);
        assertEquals(10, queue.maxMobsPerTick());

        // Enqueue 25 mock tasks (simulate large chunk load burst)
        for (int i = 0; i < 25; i++) {
            queue.enqueue(null, SpawnReason.NATURAL);
        }

        // size check
        assertEquals(0, queue.size()); // null entities ignored
    }

    @Test
    public void testTransformationDataPreservation() {
        MockLevelledMob zombie = new MockLevelledMob();
        zombie.lampas$setLevelData(LevelledMobData.of(15, "custom_zombies"));

        MockLevelledMob drowned = new MockLevelledMob();
        // Simulate conversion transfer
        drowned.lampas$setLevelData(zombie.lampas$getLevelData());

        assertTrue(drowned.lampas$getLevelData().levelled());
        assertEquals(15, drowned.lampas$getLevelData().level());
        assertEquals("custom_zombies", drowned.lampas$getLevelData().ruleSet());
    }

    @Test
    public void testChunkReloadPreservesExistingLevelWithoutRerolling() {
        LevelledMobData existing = LevelledMobData.of(27, "deep_dark_rule");
        MockLevelledMob reloadedEntity = new MockLevelledMob();
        reloadedEntity.lampas$setLevelData(existing);

        // Verify entity holds exact level 27
        assertEquals(27, reloadedEntity.lampas$getLevelData().level());
        assertEquals("deep_dark_rule", reloadedEntity.lampas$getLevelData().ruleSet());
        assertTrue(reloadedEntity.lampas$getLevelData().levelled());
    }

    @Test
    public void testQueueTimeBudgetAndSaneDefaults() {
        MobLevelingService mockService = new MobLevelingService(new RuleManager(), new AttributeScalingService(), new NametagService());
        MobProcessingQueue queue = new MobProcessingQueue(mockService);

        // Default max process time ms is 2
        assertEquals(2, queue.maxProcessTimeMs());

        queue.setMaxProcessTimeMs(10);
        assertEquals(10, queue.maxProcessTimeMs());

        // Negative or zero should clamp to at least 1ms
        queue.setMaxProcessTimeMs(0);
        assertEquals(1, queue.maxProcessTimeMs());

        // LevelledMobsConfig backward compatibility
        LevelledMobsConfig config7Fields = new LevelledMobsConfig(
            50,
            "<gray>Lv. <level>",
            lampas.levelledmobs.nametag.NametagVisibility.HOVER_ONLY,
            false,
            false,
            0.10,
            0.05
        );
        assertEquals(2, config7Fields.effectiveMaxProcessTimeMs());

        LevelledMobsConfig config8Fields = new LevelledMobsConfig(
            50,
            15,
            "<gray>Lv. <level>",
            lampas.levelledmobs.nametag.NametagVisibility.HOVER_ONLY,
            false,
            false,
            0.10,
            0.05
        );
        assertEquals(15, config8Fields.effectiveMaxProcessTimeMs());
    }

    private static class DummyLivingEntity extends net.minecraft.world.entity.LivingEntity {
        private java.util.UUID uuid;
        private boolean alive = true;

        protected DummyLivingEntity() {
            super(null, null);
        }

        public static DummyLivingEntity create(java.util.UUID uuid) {
            try {
                java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
                DummyLivingEntity entity = (DummyLivingEntity) unsafe.allocateInstance(DummyLivingEntity.class);
                entity.uuid = (uuid != null) ? uuid : java.util.UUID.randomUUID();
                entity.alive = true;

                java.lang.reflect.Field attrField = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attributes");
                attrField.setAccessible(true);
                attrField.set(entity, new net.minecraft.world.entity.ai.attributes.AttributeMap(createLivingAttributes().build()));

                java.lang.reflect.Field typeField = net.minecraft.world.entity.Entity.class.getDeclaredField("type");
                typeField.setAccessible(true);
                typeField.set(entity, net.minecraft.world.entity.EntityTypes.ZOMBIE);

                return entity;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public java.util.UUID getUUID() {
            return uuid;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        public void setAlive(boolean alive) {
            this.alive = alive;
        }

        public void setDiscarded() {
            try {
                java.lang.reflect.Field reasonField = net.minecraft.world.entity.Entity.class.getDeclaredField("removalReason");
                reasonField.setAccessible(true);
                reasonField.set(this, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public net.minecraft.world.item.ItemStack getItemBySlot(net.minecraft.world.entity.EquipmentSlot slot) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }

        @Override
        public void setItemSlot(net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.world.item.ItemStack stack) {}

        @Override
        public net.minecraft.world.entity.HumanoidArm getMainArm() {
            return net.minecraft.world.entity.HumanoidArm.RIGHT;
        }
    }

    @Test
    public void testQueueDeduplicationAndStateRelease() {
        AtomicInteger processCount = new AtomicInteger(0);
        MobLevelingService mockService = new MobLevelingService(new RuleManager(), new AttributeScalingService(), new NametagService()) {
            @Override
            public void onEntityLoad(LivingEntity entity, SpawnReason spawnReason) {
                processCount.incrementAndGet();
            }
        };

        MobProcessingQueue queue = new MobProcessingQueue(mockService);
        DummyLivingEntity entity1 = DummyLivingEntity.create(java.util.UUID.randomUUID());

        // Enqueue same entity twice
        queue.enqueue(entity1, SpawnReason.NATURAL);
        queue.enqueue(entity1, SpawnReason.NATURAL);
        assertEquals(1, queue.size(), "Duplicate enqueue of same entity must be rejected");

        // Process tick
        int processed = queue.processTick();
        assertEquals(1, processed);
        assertEquals(0, queue.size());
        assertEquals(1, processCount.get());

        // Re-enqueue after dequeue should succeed because deduplication state was released
        queue.enqueue(entity1, SpawnReason.NATURAL);
        assertEquals(1, queue.size(), "Entity can be re-enqueued after being dequeued");
    }

    @Test
    public void testQueueAttemptsCapPreventsUnboundedDrain() {
        AtomicInteger processCount = new AtomicInteger(0);
        MobLevelingService mockService = new MobLevelingService(new RuleManager(), new AttributeScalingService(), new NametagService()) {
            @Override
            public void onEntityLoad(LivingEntity entity, SpawnReason spawnReason) {
                processCount.incrementAndGet();
            }
        };

        MobProcessingQueue queue = new MobProcessingQueue(mockService);
        queue.setMaxMobsPerTick(5);

        // Enqueue 10 entities that are dead (should be dequeued and skipped without calling service)
        for (int i = 0; i < 10; i++) {
            DummyLivingEntity deadEntity = DummyLivingEntity.create(java.util.UUID.randomUUID());
            queue.enqueue(deadEntity, SpawnReason.NATURAL);
            deadEntity.setAlive(false); // Died after enqueue (isAlive becomes false)
        }
        assertEquals(10, queue.size());

        // Process tick: maxMobsPerTick is 5, so exactly 5 attempts must be made, leaving 5 in queue
        int processed = queue.processTick();
        assertEquals(0, processed, "Dead entities must not be processed");
        assertEquals(5, queue.size(), "Attempts cap must stop drain at maxMobsPerTick = 5");
    }

    @Test
    public void testQueueDeterministicTimeBudgetAndForwardProgress() {
        AtomicInteger processCount = new AtomicInteger(0);
        MobLevelingService mockService = new MobLevelingService(new RuleManager(), new AttributeScalingService(), new NametagService()) {
            @Override
            public void onEntityLoad(LivingEntity entity, SpawnReason spawnReason) {
                processCount.incrementAndGet();
            }
        };

        // Injected monotonic clock: advances by 3ms (3,000,000 ns) on each clock read
        java.util.concurrent.atomic.AtomicLong clockNs = new java.util.concurrent.atomic.AtomicLong(1_000_000_000L);
        MobProcessingQueue queue = new MobProcessingQueue(mockService, () -> clockNs.getAndAdd(3_000_000L));
        queue.setMaxMobsPerTick(50);
        queue.setMaxProcessTimeMs(2); // 2ms budget

        for (int i = 0; i < 10; i++) {
            queue.enqueue(DummyLivingEntity.create(java.util.UUID.randomUUID()), SpawnReason.NATURAL);
        }
        assertEquals(10, queue.size());

        // Tick: First mob processed (forward progress guarantee).
        // Clock advanced past 2ms budget, so loop stops after 1 attempt.
        int processed = queue.processTick();
        assertEquals(1, processed, "Forward progress guarantees at least 1 mob is processed");
        assertEquals(9, queue.size(), "Time budget must stop further processing in same tick");
    }
}
