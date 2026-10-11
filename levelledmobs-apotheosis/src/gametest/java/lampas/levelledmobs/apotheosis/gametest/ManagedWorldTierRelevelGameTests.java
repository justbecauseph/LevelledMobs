package lampas.levelledmobs.apotheosis.gametest;

import dev.shadowsoffire.apotheosis.mobs.ApothMobEvents;
import dev.shadowsoffire.apotheosis.tiers.TierContextBridge;
import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import lampas.levelledmobs.LevelledMobsModule;
import lampas.levelledmobs.attributes.AttributeDefinition;
import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import lampas.levelledmobs.rules.IntRange;
import lampas.levelledmobs.rules.LevelRule;
import lampas.levelledmobs.rules.strategy.StrategyRegistry;
import lampas.levelledmobs.apotheosis.ApotheosisWorldTierStrategy;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Live coverage for a managed mob whose creation tier changes after it has been levelled. The test installs a fake
 * Apotheosis tier provider, so the mob is managed without Yggdrasil.
 */
public final class ManagedWorldTierRelevelGameTests {
    private static final String RULE_ID = "gametest-managed-relevel";

    @GameTest(maxTicks = 80)
    @SuppressWarnings("removal")
    public void managedCreationTierChangeRelevelsLoadedMob(GameTestHelper helper) {
        var ruleManager = LevelledMobsModule.getRuleManager();
        var processingQueue = LevelledMobsModule.getProcessingQueue();
        var relevelQueue = LevelledMobsModule.getRelevelQueue();
        helper.assertTrue(ruleManager != null && processingQueue != null && relevelQueue != null,
            "LevelledMobs did not initialize its live services");
        helper.assertTrue(
            StrategyRegistry.INSTANCE.getStrategy(ApotheosisWorldTierStrategy.NAME)
                == ApotheosisWorldTierStrategy.INSTANCE,
            "the addon did not register its World Tier strategy"
        );

        ruleManager.setRules(List.of(LevelRule.builder(RULE_ID)
            .priority(1000)
            .levelRange(IntRange.of(1, 50))
            .strategy(ApotheosisWorldTierStrategy.NAME, fixedTierBands())
            .build()));
        processingQueue.clear();
        relevelQueue.clear();
        processingQueue.setMaxProcessTimeMs(50);

        FakeProvider provider = new FakeProvider();
        TierContextBridge.install(provider);
        try {
            Zombie mob = spawnNaturalZombie(helper, new BlockPos(1, 2, 2));
            provider.put(mob, creation(WorldTier.FRONTIER));
            helper.assertTrue(processingQueue.processTick() >= 1,
                "LevelledMobs did not process the managed test mob");
            assertLevel(helper, mob, 20);

            // Half health must survive the re-tier as the same ratio of the new maximum.
            mob.setHealth(mob.getMaxHealth() * 0.5f);
            float ratioBefore = mob.getHealth() / mob.getMaxHealth();

            // Yggdrasil rewrites the creation context, then announces the change.
            provider.put(mob, creation(WorldTier.SUMMIT));
            TierContextBridge.fireContextChanged(mob);
            helper.assertValueEqual(relevelQueue.size(), 1, "the context change was not queued for a re-tier");
            helper.assertValueEqual(((LevelledMobHolder) mob).lampas$getLevelData().level(), 20,
                "the re-tier must not run inside the listener call");

            helper.runAfterDelay(2, () -> {
                try {
                    helper.assertValueEqual(relevelQueue.size(), 0, "the re-tier queue was not drained");
                    assertLevel(helper, mob, 40);

                    float ratioAfter = mob.getHealth() / mob.getMaxHealth();
                    helper.assertTrue(Math.abs(ratioAfter - ratioBefore) < 0.01f,
                        "health ratio was not preserved: before " + ratioBefore + ", after " + ratioAfter);

                    AttributeInstance health = mob.getAttribute(AttributeDefinition.MAX_HEALTH.attribute());
                    helper.assertTrue(health != null, "the test mob did not expose its max-health attribute");
                    long matching = health.getModifiers().stream()
                        .filter(modifier -> modifier.id().equals(AttributeDefinition.MAX_HEALTH.modifierId()))
                        .count();
                    helper.assertValueEqual(matching, 1L,
                        "the LevelledMobs max-health modifier must be replaced, not stacked");
                    helper.assertTrue(health.getModifier(AttributeDefinition.MAX_HEALTH.modifierId()) != null,
                        "the LevelledMobs max-health modifier is missing after the re-tier");
                    helper.succeed();
                } finally {
                    TierContextBridge.clear(provider);
                }
            });
        } catch (RuntimeException ex) {
            TierContextBridge.clear(provider);
            throw ex;
        }
    }

    private static Zombie spawnNaturalZombie(GameTestHelper helper, BlockPos relativePos) {
        Zombie zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.NATURAL);
        helper.assertTrue(zombie != null, "a natural-spawn test zombie could not be created");
        zombie.snapTo(center(helper, relativePos));
        zombie.addTag(ApothMobEvents.NO_RANDOM_PROCESSING);
        zombie.finalizeSpawn(
            helper.getLevel(),
            helper.getLevel().getCurrentDifficultyAt(zombie.blockPosition()),
            EntitySpawnReason.NATURAL,
            null
        );
        zombie.setBaby(false);
        helper.assertTrue(helper.getLevel().addFreshEntity(zombie),
            "the natural-spawn test zombie could not be added to the level");
        return zombie;
    }

    private static void assertLevel(GameTestHelper helper, Zombie mob, int expectedLevel) {
        helper.assertTrue(mob instanceof LevelledMobHolder,
            "the LevelledMobs data-holder mixin was not applied to the test mob");
        LevelledMobData data = ((LevelledMobHolder) mob).lampas$getLevelData();
        helper.assertTrue(data != null && data.levelled(), "the test mob was not levelled");
        helper.assertValueEqual(data.level(), expectedLevel, "the test mob's LevelledMobs level");
        helper.assertValueEqual(data.ruleSet(), RULE_ID, "the test mob's rule id");
        helper.assertTrue(!data.quarantined(), "the managed re-tier must not quarantine the test mob");
    }

    private static TierContextBridge.CreationContext creation(WorldTier tier) {
        return new TierContextBridge.CreationContext(
            1,
            "gametest-revision",
            "gametest-region",
            "gametest-policy",
            "AUTHORED",
            null,
            tier,
            1,
            50,
            null
        );
    }

    private static Vec3 center(GameTestHelper helper, BlockPos relativePos) {
        return Vec3.atCenterOf(helper.absolutePos(relativePos));
    }

    private static Map<String, Object> fixedTierBands() {
        return Map.of("tier_bands", Map.of(
            "haven", band(10),
            "frontier", band(20),
            "ascent", band(30),
            "summit", band(40),
            "pinnacle", band(50)
        ));
    }

    private static Map<String, Object> band(int level) {
        return Map.of("min", level, "max", level);
    }

    /**
     * Stands in for Yggdrasil: it manages exactly the mobs it was given a context for, and it does not take over
     * player tier assignment.
     */
    private static final class FakeProvider implements TierContextBridge.Provider {
        private final Map<UUID, TierContextBridge.CreationContext> contexts = new HashMap<>();

        void put(Mob mob, TierContextBridge.CreationContext context) {
            contexts.put(mob.getUUID(), context);
        }

        @Override
        public boolean controlsAssignments() {
            return false;
        }

        @Override
        public WorldTier effectiveTier(Player player, BlockPos origin) {
            return null;
        }

        @Override
        public Optional<TierContextBridge.CreationContext> creation(Mob mob) {
            return Optional.ofNullable(contexts.get(mob.getUUID()));
        }

        @Override
        public boolean managed(Mob mob) {
            return contexts.containsKey(mob.getUUID());
        }
    }
}
