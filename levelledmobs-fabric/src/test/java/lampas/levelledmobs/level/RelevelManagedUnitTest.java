package lampas.levelledmobs.level;

import lampas.levelledmobs.attributes.AttributeDefinition;
import lampas.levelledmobs.attributes.AttributeScalingService;
import lampas.levelledmobs.attributes.HealthPolicy;
import lampas.levelledmobs.context.MobContext;
import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobModifier;
import lampas.levelledmobs.data.SpawnReason;
import lampas.levelledmobs.rules.EffectiveRule;
import lampas.levelledmobs.rules.IntRange;
import lampas.levelledmobs.rules.LevelRule;
import lampas.levelledmobs.rules.RuleManager;
import lampas.levelledmobs.rules.strategy.LevelStrategy;
import lampas.levelledmobs.rules.strategy.StrategyRegistry;
import lampas.levelledmobs.testsupport.TestMobs;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit coverage for {@link MobLevelingService#relevelManaged}, the managed relevel queue and its barrier retries.
 * The strategy is a test double with a controllable accepted band and a controllable temporary-unavailability flag.
 * Ordinary loads never relevel: a stale managed mob keeps its level until a context change is announced.
 */
public class RelevelManagedUnitTest {

    private static final String RULE_SET = "apotheosis_scaled_mobs";
    private static final String STRATEGY = "TEST_BAND";
    private static final long GENERATED_AT = 1_234_567L;

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Accepts levels inside [min, max] and returns the configured next level from calculateLevel. */
    private static final class BandStrategy implements LevelStrategy {
        volatile int min = 1;
        volatile int max = 100;
        volatile int next = 7;
        volatile boolean available = true;
        /** When true, an unavailable result means a temporary barrier (retry), not a permanent refusal. */
        volatile boolean deferWhileUnavailable = false;
        final AtomicInteger calculations = new AtomicInteger();

        @Override
        public String name() {
            return STRATEGY;
        }

        @Override
        public int calculateLevel(MobContext context, EffectiveRule rule) {
            calculations.incrementAndGet();
            return available ? next : 0;
        }

        @Override
        public int constrainLevel(MobContext context, EffectiveRule rule, int level) {
            if (!available) {
                return 0;
            }
            return level >= min && level <= max ? level : 0;
        }

        @Override
        public boolean shouldRetryDeferredLevel(MobContext context, EffectiveRule rule) {
            return deferWhileUnavailable;
        }
    }

    private static final class Fixture {
        final BandStrategy strategy = new BandStrategy();
        final AttributeScalingService attributes = new AttributeScalingService();
        final TestMobs.RecordingNametagService nametags = new TestMobs.RecordingNametagService();
        final MobLevelingService service;
        final EffectiveRule rule;

        Fixture() {
            StrategyRegistry registry = new StrategyRegistry();
            registry.register(strategy);
            LevelRule levelRule = LevelRule.builder("managed_rule")
                .priority(100)
                .levelRange(IntRange.of(1, 100))
                .strategy(STRATEGY, null)
                .attributeSettings(Map.of(
                    "max_health", 2.5,
                    "attack_damage", 0.25,
                    "movement_speed", 0.001,
                    "projectile_damage", 0.03,
                    "explosion_damage", 0.02
                ))
                .build();
            RuleManager ruleManager = new RuleManager();
            ruleManager.setRules(List.of(levelRule));
            this.service = new MobLevelingService(ruleManager, attributes, nametags, new BossClassifier(), registry);
            this.rule = ruleManager.resolve(MobContext.of(TestMobs.monster(), SpawnReason.NATURAL)).effectiveRule();
        }

        /** A levelled mob with its plan applied, as the fresh-level path leaves it. */
        TestMobs.TestMonster levelled(int level, String ruleSet, boolean quarantined) {
            TestMobs.TestMonster mob = TestMobs.monster();
            List<LevelledMobModifier> plan = attributes.resolveEffectiveModifierPlan(level, rule);
            attributes.applyModifiers(mob, plan, HealthPolicy.FRESH_SPAWN);
            mob.lampas$setLevelData(new LevelledMobData(level, true, ruleSet, GENERATED_AT,
                LevelledMobData.CURRENT_RETENTION_VERSION, plan, quarantined));
            return mob;
        }

        AttributeInstance instance(LivingEntity mob, AttributeDefinition def) {
            return mob.getAttribute(def.attribute());
        }
    }

    @Test
    public void freshLevelStoresTheRuleDamageFractions() {
        Fixture fixture = new Fixture();
        fixture.strategy.min = 1;
        fixture.strategy.max = 100;
        fixture.strategy.next = 7;
        TestMobs.TestMonster mob = TestMobs.monster();

        fixture.service.level(mob, SpawnReason.NATURAL);

        LevelledMobData data = mob.lampas$getLevelData();
        assertTrue(data.levelled(), "the managed rule must level the mob");
        assertEquals(0.03, data.projectileDamagePerLevel());
        assertEquals(0.02, data.explosionDamagePerLevel());
    }

    @Test
    public void relevelStoresTheCurrentRuleDamageFractions() {
        Fixture fixture = new Fixture();
        fixture.strategy.min = 5;
        fixture.strategy.max = 10;
        fixture.strategy.next = 7;
        TestMobs.TestMonster mob = fixture.levelled(2, RULE_SET, false);
        assertTrue(Double.isNaN(mob.lampas$getLevelData().projectileDamagePerLevel()),
            "a mob levelled before the damage fields existed stores no override");

        assertTrue(fixture.service.relevelManaged(mob));

        assertEquals(0.03, mob.lampas$getLevelData().projectileDamagePerLevel());
        assertEquals(0.02, mob.lampas$getLevelData().explosionDamagePerLevel());
    }

    @Test
    public void adminSetLevelStoresTheMatchingRuleDamageFractions() {
        Fixture fixture = new Fixture();
        TestMobs.TestMonster mob = TestMobs.monster();

        fixture.service.setLevel(mob, 30, "custom");

        assertEquals(0.03, mob.lampas$getLevelData().projectileDamagePerLevel());
        assertEquals(0.02, mob.lampas$getLevelData().explosionDamagePerLevel());
    }

    @Test
    public void adminSetLevelWithNoMatchingRuleStoresNoDamageOverride() {
        TestMobs.TestMonster mob = TestMobs.monster();

        countingService(new AtomicInteger()).setLevel(mob, 30, "manual");

        assertTrue(Double.isNaN(mob.lampas$getLevelData().projectileDamagePerLevel()));
        assertTrue(Double.isNaN(mob.lampas$getLevelData().explosionDamagePerLevel()));
    }

    private static double amount(LivingEntity mob, AttributeDefinition def) {
        AttributeModifier modifier = mob.getAttribute(def.attribute()).getModifier(def.modifierId());
        return modifier == null ? 0.0 : modifier.amount();
    }

    @Test
    public void relevelKeepsStoredRuleIdGenerationTimeAndQuarantineState() {
        Fixture fixture = new Fixture();
        fixture.strategy.min = 5;
        fixture.strategy.max = 10;
        fixture.strategy.next = 7;
        TestMobs.TestMonster mob = fixture.levelled(2, RULE_SET, false);

        assertTrue(fixture.service.relevelManaged(mob));

        LevelledMobData data = mob.lampas$getLevelData();
        assertEquals(7, data.level());
        assertEquals(RULE_SET, data.ruleSet(), "The stored rule id must be kept, not the merged primary rule");
        assertEquals(GENERATED_AT, data.generatedAt());
        assertFalse(data.quarantined());
        assertTrue(data.levelled());
        assertEquals(LevelledMobData.CURRENT_RETENTION_VERSION, data.retentionVersion());
        assertTrue(data.hasPersistedModifiers());
        assertEquals(List.of("7/" + RULE_SET), fixture.nametags.requests);
    }

    @Test
    public void relevelKeepsTheBossGroupFactorAndHealthRatio() {
        Fixture fixture = new Fixture();
        fixture.strategy.min = 5;
        fixture.strategy.max = 10;
        fixture.strategy.next = 7;
        TestMobs.TestMonster mob = TestMobs.monster();
        List<LevelledMobModifier> plan = fixture.attributes.resolveEffectiveModifierPlan(2, fixture.rule);
        fixture.attributes.applyModifiers(mob, plan, 1.5, HealthPolicy.FRESH_SPAWN);
        mob.lampas$setLevelData(new LevelledMobData(2, true, RULE_SET, GENERATED_AT,
            LevelledMobData.CURRENT_RETENTION_VERSION, plan, false, 1.5));
        // (20 + 2.5) * 1.5 = 33.75; take it to half health.
        assertEquals(33.75, mob.getMaxHealth(), 1e-4);
        mob.setHealth(33.75f / 2);

        assertTrue(fixture.service.relevelManaged(mob));

        // Level 7: (20 + 6 * 2.5) * 1.5 = 52.5, still at half health.
        assertEquals(1.5, mob.lampas$getLevelData().groupFactor(), 1e-9);
        assertEquals(52.5, mob.getMaxHealth(), 1e-4);
        assertEquals(52.5 / 2, mob.getHealth(), 1e-4);
        AttributeModifier group = mob.getAttribute(AttributeDefinition.MAX_HEALTH.attribute())
            .getModifier(AttributeScalingService.GROUP_FACTOR_MODIFIER_ID);
        assertNotNull(group, "relevel must keep the group factor modifier");
        assertEquals(0.5, group.amount(), 1e-9);
    }

    @Test
    public void relevelUsesMergedRuleRatesNotServiceDefaults() {
        Fixture fixture = new Fixture();
        fixture.strategy.min = 5;
        fixture.strategy.max = 10;
        fixture.strategy.next = 7;
        TestMobs.TestMonster mob = fixture.levelled(2, RULE_SET, false);

        assertTrue(fixture.service.relevelManaged(mob));

        // Level 7 is six steps above level 1. The merged rule supplies the per-level rates.
        assertEquals(6 * 2.5, amount(mob, AttributeDefinition.MAX_HEALTH), 1e-9,
            "Health must use the merged rule rate (2.5), not the service default (2.0)");
        assertEquals(6 * 0.25, amount(mob, AttributeDefinition.ATTACK_DAMAGE), 1e-9,
            "Damage must use the merged rule rate (0.25), not the service default (0.5)");
        assertEquals(6 * 0.001, amount(mob, AttributeDefinition.MOVEMENT_SPEED), 1e-9,
            "Speed must use the merged rule rate (0.001), not the service default (0.01)");

        double storedHealth = mob.lampas$getLevelData().effectiveModifiers().stream()
            .filter(m -> m.attributeKey().equals(AttributeDefinition.MAX_HEALTH.key()))
            .mapToDouble(LevelledMobModifier::amount)
            .findFirst()
            .orElse(Double.NaN);
        assertEquals(6 * 2.5, storedHealth, 1e-9, "The stored plan must hold the same merged amounts");
    }

    @Test
    public void relevelReplacesEachModifierOnceByStableId() {
        Fixture fixture = new Fixture();
        fixture.strategy.min = 5;
        fixture.strategy.max = 10;
        fixture.strategy.next = 7;
        TestMobs.TestMonster mob = fixture.levelled(2, RULE_SET, false);

        assertTrue(fixture.service.relevelManaged(mob));
        fixture.strategy.min = 9;
        fixture.strategy.next = 9;
        assertTrue(fixture.service.relevelManaged(mob));

        AttributeInstance health = fixture.instance(mob, AttributeDefinition.MAX_HEALTH);
        long matching = health.getModifiers().stream()
            .filter(m -> m.id().equals(AttributeDefinition.MAX_HEALTH.modifierId()))
            .count();
        assertEquals(1, matching, "A relevel must replace the LevelledMobs modifier, never stack it");
        assertEquals(8 * 2.5, amount(mob, AttributeDefinition.MAX_HEALTH), 1e-9);
        assertEquals(9, mob.lampas$getLevelData().level());
    }

    @Test
    public void relevelIsNoOpWhenLevelStillInsideBand() {
        Fixture fixture = new Fixture();
        fixture.strategy.min = 5;
        fixture.strategy.max = 10;
        TestMobs.TestMonster mob = fixture.levelled(6, RULE_SET, false);
        AttributeInstance health = fixture.instance(mob, AttributeDefinition.MAX_HEALTH);
        AttributeModifier before = health.getModifier(AttributeDefinition.MAX_HEALTH.modifierId());
        LevelledMobData beforeData = mob.lampas$getLevelData();

        assertFalse(fixture.service.relevelManaged(mob));

        assertSame(before, health.getModifier(AttributeDefinition.MAX_HEALTH.modifierId()),
            "A level inside the band must keep the same modifier instance");
        assertSame(beforeData, mob.lampas$getLevelData());
        assertEquals(0, fixture.strategy.calculations.get(), "No new level may be calculated inside the band");
        assertTrue(fixture.nametags.requests.isEmpty());
    }

    @Test
    public void relevelLeavesQuarantinedAndAdminSetMobsUntouched() {
        Fixture fixture = new Fixture();
        fixture.strategy.min = 5;
        fixture.strategy.max = 10;
        fixture.strategy.next = 7;

        TestMobs.TestMonster quarantined = fixture.levelled(2, RULE_SET, true);
        TestMobs.TestMonster custom = fixture.levelled(2, "custom", false);
        TestMobs.TestMonster manual = fixture.levelled(2, "manual", false);
        TestMobs.TestMonster[] mobs = {quarantined, custom, manual};
        List<LevelledMobData> before = new ArrayList<>();
        for (TestMobs.TestMonster mob : mobs) {
            before.add(mob.lampas$getLevelData());
        }

        for (TestMobs.TestMonster mob : mobs) {
            assertFalse(fixture.service.relevelManaged(mob));
        }

        for (int i = 0; i < mobs.length; i++) {
            assertSame(before.get(i), mobs[i].lampas$getLevelData());
        }
        assertEquals(0, fixture.strategy.calculations.get());
        assertTrue(fixture.nametags.requests.isEmpty());
    }

    @Test
    public void zeroLevelKeepsEverythingAndNeverQuarantines() {
        Fixture fixture = new Fixture();
        fixture.strategy.min = 5;
        fixture.strategy.max = 10;
        fixture.strategy.available = false;
        TestMobs.TestMonster mob = fixture.levelled(2, RULE_SET, false);
        LevelledMobData before = mob.lampas$getLevelData();
        AttributeModifier healthBefore = fixture.instance(mob, AttributeDefinition.MAX_HEALTH)
            .getModifier(AttributeDefinition.MAX_HEALTH.modifierId());

        assertFalse(fixture.service.relevelManaged(mob));

        assertEquals(before, mob.lampas$getLevelData(), "Level, plan and stored state must be unchanged");
        assertFalse(mob.lampas$getLevelData().quarantined(), "A refused relevel must never quarantine a levelled mob");
        assertSame(healthBefore, fixture.instance(mob, AttributeDefinition.MAX_HEALTH)
            .getModifier(AttributeDefinition.MAX_HEALTH.modifierId()));
        assertTrue(fixture.nametags.requests.isEmpty());
    }

    @Test
    public void newLevelOutsideAcceptedBandIsRejectedAndKeepsCurrentLevel() {
        Fixture fixture = new Fixture();
        fixture.strategy.min = 5;
        fixture.strategy.max = 10;
        fixture.strategy.next = 3;
        TestMobs.TestMonster mob = fixture.levelled(2, RULE_SET, false);
        LevelledMobData before = mob.lampas$getLevelData();

        assertFalse(fixture.service.relevelManaged(mob));

        assertEquals(before, mob.lampas$getLevelData());
        assertFalse(mob.lampas$getLevelData().quarantined());
    }

    @Test
    public void relevelSkipsUnlevelledDeadAndBabyMobs() {
        Fixture fixture = new Fixture();
        fixture.strategy.min = 5;
        fixture.strategy.max = 10;

        TestMobs.TestMonster unlevelled = TestMobs.monster();
        assertFalse(fixture.service.relevelManaged(unlevelled));

        TestMobs.TestMonster dead = fixture.levelled(2, RULE_SET, false);
        dead.setAlive(false);
        assertFalse(fixture.service.relevelManaged(dead));

        TestMobs.TestMonster baby = fixture.levelled(2, RULE_SET, false);
        baby.setBaby(true);
        assertFalse(fixture.service.relevelManaged(baby));

        assertEquals(0, fixture.strategy.calculations.get());
    }

    @Test
    public void ordinaryLoadDoesNotRetireStaleManagedMob() {
        // YG-10: a band edit or context change never rerolls an existing mob on an ordinary load. Only an
        // announced context change (the queued relevel) re-tiers it.
        Fixture fixture = new Fixture();
        fixture.strategy.min = 5;
        fixture.strategy.max = 10;
        fixture.strategy.next = 7;
        TestMobs.TestMonster mob = fixture.levelled(2, RULE_SET, false);
        LevelledMobData before = mob.lampas$getLevelData();

        fixture.service.onEntityLoad(mob, SpawnReason.NATURAL);

        assertSame(before, mob.lampas$getLevelData(), "The stored level and plan must be untouched by a load");
        assertEquals(2, mob.lampas$getLevelData().level());
        assertEquals(1 * 2.5, amount(mob, AttributeDefinition.MAX_HEALTH), 1e-9);
        assertEquals(0, fixture.strategy.calculations.get(), "A load must not calculate a new level");
        assertEquals(List.of("2/default"), fixture.nametags.requests);
    }

    @Test
    public void barrierDeferralLeavesMobUnchangedAndRequeuesOncePerTick() {
        Fixture fixture = new Fixture();
        fixture.strategy.min = 5;
        fixture.strategy.max = 10;
        fixture.strategy.available = false;
        fixture.strategy.deferWhileUnavailable = true;
        TestMobs.TestMonster mob = fixture.levelled(2, RULE_SET, false);
        LevelledMobData before = mob.lampas$getLevelData();
        AttributeModifier healthBefore = fixture.instance(mob, AttributeDefinition.MAX_HEALTH)
            .getModifier(AttributeDefinition.MAX_HEALTH.modifierId());

        assertEquals(MobLevelingService.RelevelOutcome.DEFERRED, fixture.service.relevelOutcome(mob));
        assertFalse(fixture.service.relevelManaged(mob));

        RelevelQueue queue = new RelevelQueue(fixture.service);
        assertTrue(queue.enqueue(mob));
        assertEquals(1, queue.processTick());
        assertEquals(1, queue.size(), "A barrier deferral must be requeued for a later tick");
        assertEquals(1, queue.processTick());
        assertEquals(1, queue.size());

        assertSame(before, mob.lampas$getLevelData(), "A deferral must not change level, plan or state");
        assertFalse(mob.lampas$getLevelData().quarantined(), "A deferral must never quarantine a levelled mob");
        assertSame(healthBefore, fixture.instance(mob, AttributeDefinition.MAX_HEALTH)
            .getModifier(AttributeDefinition.MAX_HEALTH.modifierId()));
        assertTrue(fixture.nametags.requests.isEmpty());
    }

    @Test
    public void barrierRetriesAreBoundedAndThenDroppedWithoutQuarantine() {
        Fixture fixture = new Fixture();
        fixture.strategy.min = 5;
        fixture.strategy.max = 10;
        fixture.strategy.available = false;
        fixture.strategy.deferWhileUnavailable = true;
        TestMobs.TestMonster mob = fixture.levelled(2, RULE_SET, false);
        LevelledMobData before = mob.lampas$getLevelData();
        RelevelQueue queue = new RelevelQueue(fixture.service);
        assertEquals(1200, RelevelQueue.MAX_DEFERRED_ATTEMPTS);
        assertTrue(queue.enqueue(mob));

        // One evaluation per tick: the first plus 1200 requeued retries.
        for (int tick = 1; tick <= RelevelQueue.MAX_DEFERRED_ATTEMPTS; tick++) {
            assertEquals(1, queue.processTick());
        }
        assertEquals(1, queue.size(), "Retries stay queued until the bound is reached");
        assertEquals(1, queue.processTick(), "The final retry is evaluated");
        assertEquals(0, queue.size(), "After the bound the mob is dropped from the queue");
        assertEquals(RelevelQueue.MAX_DEFERRED_ATTEMPTS + 1, fixture.strategy.calculations.get());

        assertSame(before, mob.lampas$getLevelData());
        assertFalse(mob.lampas$getLevelData().quarantined());
    }

    @Test
    public void unknownContextIsDroppedWithoutRequeue() {
        Fixture fixture = new Fixture();
        fixture.strategy.min = 5;
        fixture.strategy.max = 10;
        fixture.strategy.available = false;
        fixture.strategy.deferWhileUnavailable = false;
        TestMobs.TestMonster mob = fixture.levelled(2, RULE_SET, false);
        LevelledMobData before = mob.lampas$getLevelData();

        assertEquals(MobLevelingService.RelevelOutcome.DROPPED, fixture.service.relevelOutcome(mob));

        RelevelQueue queue = new RelevelQueue(fixture.service);
        assertTrue(queue.enqueue(mob));
        assertEquals(1, queue.processTick());
        assertEquals(0, queue.size(), "A permanent refusal must not be requeued");
        assertSame(before, mob.lampas$getLevelData());
        assertFalse(mob.lampas$getLevelData().quarantined());
    }

    /** Service whose relevel attempts are counted and always report no change. */
    private static MobLevelingService countingService(AtomicInteger evaluations) {
        return new MobLevelingService(
            new RuleManager(), new AttributeScalingService(), new TestMobs.RecordingNametagService(),
            new BossClassifier(), new StrategyRegistry()) {
            @Override
            RelevelOutcome relevelOutcome(LivingEntity entity) {
                evaluations.incrementAndGet();
                return RelevelOutcome.UNCHANGED;
            }
        };
    }

    @Test
    public void relevelQueueDeduplicatesByUuid() {
        Fixture fixture = new Fixture();
        RelevelQueue queue = new RelevelQueue(fixture.service);
        TestMobs.TestMonster mob = fixture.levelled(2, RULE_SET, false);

        assertTrue(queue.enqueue(mob));
        assertFalse(queue.enqueue(mob), "A second request for the same mob must be rejected");
        assertEquals(1, queue.size());
    }

    @Test
    public void relevelQueueRespectsPerTickBudget() {
        AtomicInteger evaluations = new AtomicInteger();
        MobLevelingService counting = countingService(evaluations);
        RelevelQueue queue = new RelevelQueue(counting);
        assertEquals(32, queue.maxPerTick());

        List<TestMobs.TestMonster> mobs = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            TestMobs.TestMonster mob = TestMobs.monster();
            mobs.add(mob);
            assertTrue(queue.enqueue(mob));
        }
        assertEquals(40, queue.size());

        assertEquals(32, queue.processTick(), "At most 32 mobs are evaluated in one server tick");
        assertEquals(32, evaluations.get());
        assertEquals(8, queue.size());

        assertEquals(8, queue.processTick());
        assertEquals(40, evaluations.get());
        assertEquals(0, queue.size());
    }

    @Test
    public void relevelQueueDropsRemovedAndDeadMobsWithoutEvaluatingThem() {
        AtomicInteger evaluations = new AtomicInteger();
        RelevelQueue queue = new RelevelQueue(countingService(evaluations));

        TestMobs.TestMonster removed = TestMobs.monster();
        TestMobs.TestMonster dead = TestMobs.monster();
        TestMobs.TestMonster alive = TestMobs.monster();
        queue.enqueue(removed);
        queue.enqueue(dead);
        queue.enqueue(alive);
        TestMobs.discard(removed);
        dead.setAlive(false);

        assertEquals(1, queue.processTick(), "Only the live mob is evaluated");
        assertEquals(1, evaluations.get());
        assertEquals(0, queue.size());
    }
}
