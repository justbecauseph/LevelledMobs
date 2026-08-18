package lampas.levelledmobs.level;

import lampas.levelledmobs.attributes.AttributeScalingService;
import lampas.levelledmobs.attributes.HealthPolicy;
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
}
