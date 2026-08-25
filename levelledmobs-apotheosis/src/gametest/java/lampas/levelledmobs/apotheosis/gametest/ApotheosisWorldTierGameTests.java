package lampas.levelledmobs.apotheosis.gametest;

import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.mobs.ApothMobEvents;
import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import lampas.levelledmobs.LevelledMobsModule;
import lampas.levelledmobs.attributes.AttributeDefinition;
import lampas.levelledmobs.context.MobContext;
import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import lampas.levelledmobs.data.SpawnReason;
import lampas.levelledmobs.rules.IntRange;
import lampas.levelledmobs.rules.LevelRule;
import lampas.levelledmobs.rules.strategy.StrategyRegistry;
import lampas.levelledmobs.apotheosis.ApotheosisWorldTierStrategy;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/** Live Fabric coverage for the LevelledMobs and Apotheosis World Tier boundary. */
public final class ApotheosisWorldTierGameTests {

    @GameTest(maxTicks = 40)
    @SuppressWarnings("removal")
    public void nearestWorldTierDrivesLifecycleLevelAndPersists(GameTestHelper helper) {
        var ruleManager = LevelledMobsModule.getRuleManager();
        var processingQueue = LevelledMobsModule.getProcessingQueue();
        var levelingService = LevelledMobsModule.getMobLevelingService();
        helper.assertTrue(ruleManager != null && processingQueue != null && levelingService != null,
            "LevelledMobs did not initialize its live services");
        helper.assertTrue(
            StrategyRegistry.INSTANCE.getStrategy(ApotheosisWorldTierStrategy.NAME)
                == ApotheosisWorldTierStrategy.INSTANCE,
            "the addon did not register its World Tier strategy"
        );

        ruleManager.setRules(List.of(LevelRule.builder("gametest-world-tier")
            .priority(1000)
            .levelRange(IntRange.of(1, 50))
            .strategy(ApotheosisWorldTierStrategy.NAME, fixedTierBands())
            .build()));
        processingQueue.clear();
        processingQueue.setMaxProcessTimeMs(50);

        ServerPlayer frontierPlayer = helper.makeMockServerPlayerInLevel();
        ServerPlayer pinnaclePlayer = helper.makeMockServerPlayerInLevel();
        frontierPlayer.setPos(center(helper, new BlockPos(0, 2, 2)));
        pinnaclePlayer.setPos(center(helper, new BlockPos(4, 2, 2)));
        WorldTier.setTier(frontierPlayer, WorldTier.FRONTIER);
        WorldTier.setTier(pinnaclePlayer, WorldTier.PINNACLE);
        Apoth.Attachments.INVADER_COOLDOWN.set(frontierPlayer, Long.MAX_VALUE);
        Apoth.Attachments.INVADER_COOLDOWN.set(pinnaclePlayer, Long.MAX_VALUE);

        Zombie frontierMob = spawnNaturalZombie(helper, new BlockPos(1, 2, 2));
        Zombie pinnacleMob = spawnNaturalZombie(helper, new BlockPos(3, 2, 2));
        helper.assertTrue(processingQueue.size() >= 2,
            "Fabric entity-load callbacks did not enqueue both test mobs");
        helper.assertTrue(processingQueue.processTick() >= 2,
            "LevelledMobs did not process both queued test mobs");

        assertLevelAndModifiers(helper, frontierMob, 20, "frontier");
        assertLevelAndModifiers(helper, pinnacleMob, 50, "pinnacle");

        // The integration intentionally matches Apotheosis' unlimited nearest-player lookup,
        // rather than MobContext.nearestPlayer(), which is capped at 64 blocks.
        WorldTier.setTier(pinnaclePlayer, WorldTier.SUMMIT);
        Zombie remoteMob = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.NATURAL);
        helper.assertTrue(remoteMob != null, "the remote-distance test mob could not be created");
        remoteMob.snapTo(center(helper, new BlockPos(100, 2, 2)));
        helper.assertTrue(MobContext.of(remoteMob, SpawnReason.NATURAL).nearestPlayer().isEmpty(),
            "the remote mob was not outside LevelledMobs' generic 64-block player lookup");
        levelingService.level(remoteMob, SpawnReason.NATURAL);
        assertLevel(helper, remoteMob, 40);

        // A mob's chosen LevelledMobs level is persistent. Later player-tier changes restore the
        // existing level and must not overwrite Apotheosis' already-applied monster augments.
        WorldTier.setTier(frontierPlayer, WorldTier.PINNACLE);
        var preArmorInstance = frontierMob.getAttribute(AttributeDefinition.ARMOR.attribute());
        var preModifier = (preArmorInstance != null) ? preArmorInstance.getModifier(AttributeDefinition.ARMOR.modifierId()) : null;

        levelingService.onEntityLoad(frontierMob, SpawnReason.NATURAL);
        levelingService.onEntityLoad(pinnacleMob, SpawnReason.NATURAL);

        var postArmorInstance = frontierMob.getAttribute(AttributeDefinition.ARMOR.attribute());
        var postModifier = (postArmorInstance != null) ? postArmorInstance.getModifier(AttributeDefinition.ARMOR.modifierId()) : null;

        helper.assertTrue(preModifier != null && preModifier == postModifier,
            "Attribute scaling on entity reload must retain existing matching modifier without churn");
        assertLevelAndModifiers(helper, frontierMob, 20, "frontier");
        assertLevelAndModifiers(helper, pinnacleMob, 50, "pinnacle");

        helper.succeed();
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

    private static void assertLevelAndModifiers(
        GameTestHelper helper,
        Zombie mob,
        int expectedLevel,
        String apotheosisTier
    ) {
        assertLevel(helper, mob, expectedLevel);
        helper.assertTrue(Apoth.Attachments.TIER_AUGMENTS_APPLIED.get(mob),
            "Apotheosis did not apply monster tier augments to the " + apotheosisTier + " mob");

        var armor = mob.getAttribute(AttributeDefinition.ARMOR.attribute());
        helper.assertTrue(armor != null, "the test mob did not expose its armor attribute");
        helper.assertTrue(armor.hasModifier(AttributeDefinition.ARMOR.modifierId()),
            "LevelledMobs' armor modifier was not present on the " + apotheosisTier + " mob");
        helper.assertTrue(armor.hasModifier(Identifier.fromNamespaceAndPath(
                "apotheosis", apotheosisTier + "/armor")),
            "Apotheosis' armor modifier was not present beside LevelledMobs on the "
                + apotheosisTier + " mob");
    }

    private static void assertLevel(GameTestHelper helper, Zombie mob, int expectedLevel) {
        helper.assertTrue(mob instanceof LevelledMobHolder,
            "the LevelledMobs data-holder mixin was not applied to the test mob");
        LevelledMobData data = ((LevelledMobHolder) mob).lampas$getLevelData();
        helper.assertTrue(data != null && data.levelled(), "the test mob was not levelled");
        helper.assertValueEqual(data.level(), expectedLevel, "the test mob's LevelledMobs level");
        helper.assertValueEqual(data.ruleSet(), "gametest-world-tier", "the test mob's rule id");
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
}
