package lampas.levelledmobs.apotheosis.gametest;

import dev.shadowsoffire.apotheosis.mobs.ApothMobEvents;
import lampas.levelledmobs.LevelledMobsModule;
import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import lampas.levelledmobs.level.BossScaling;
import lampas.levelledmobs.rules.LevelRule;
import lampas.levelledmobs.rules.RuleManager;
import lampas.levelledmobs.rules.RuleParser;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/**
 * Live coverage for admin-set levels ({@code /lm summon} and {@code /lm level} call
 * {@code MobLevelingService.setLevel}): the level is used as given, and the attribute values per level come from the
 * rules that match the mob, or from the built-in defaults when none does.
 *
 * <p>The test swaps the shared rule set and restores it before returning.</p>
 */
public final class AdminLevelGameTests {
    /** Above both rules' max_level, so a clamp to the band would show. */
    private static final int LEVEL = 3;
    private static final double ZOMBIE_HEALTH_PER_LEVEL = 4.0;
    private static final double ZOMBIE_ARMOR_PER_LEVEL = 1.5;
    private static final double WITHER_HEALTH_PER_LEVEL = 150.0;
    private static final double WITHER_ARMOR_PER_LEVEL = 0.25;
    /** AttributeScalingService's built-in values: +2 max health and +0.5 armor per level. */
    private static final double DEFAULT_HEALTH_PER_LEVEL = 2.0;
    private static final double DEFAULT_ARMOR_PER_LEVEL = 0.5;

    @GameTest(maxTicks = 20)
    public void adminLevelTakesAttributeValuesFromTheMatchingRule(GameTestHelper helper) {
        withRules(helper, List.of(
            adminRule("gametest-admin-zombie", "minecraft:zombie", ZOMBIE_HEALTH_PER_LEVEL, ZOMBIE_ARMOR_PER_LEVEL),
            adminRule("gametest-admin-wither", "minecraft:wither", WITHER_HEALTH_PER_LEVEL, WITHER_ARMOR_PER_LEVEL)
        ), () -> {
            Zombie zombie = createAt(helper, EntityTypes.ZOMBIE, Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
            Skeleton skeleton = createAt(helper, EntityTypes.SKELETON, Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, 2, 1))));
            // Away from the GameTest grid and from BossLevellingGameTests' mock players, so the group factor stays 1.
            WitherBoss wither = createAt(helper, EntityTypes.WITHER,
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(0, 2, 0))).add(-512, 0, -512));
            helper.assertValueEqual(BossScaling.countNearbyPlayers(wither), 0, "players near the admin-set Wither");

            for (LivingEntity mob : List.of(zombie, skeleton, wither)) {
                LevelledMobsModule.getMobLevelingService().setLevel(mob, LEVEL, "custom");
            }

            assertAdminLevel(helper, zombie, ZOMBIE_HEALTH_PER_LEVEL, ZOMBIE_ARMOR_PER_LEVEL, "the zombie under its rule");
            assertAdminLevel(helper, wither, WITHER_HEALTH_PER_LEVEL, WITHER_ARMOR_PER_LEVEL, "the Wither under its rule");
            assertAdminLevel(helper, skeleton, DEFAULT_HEALTH_PER_LEVEL, DEFAULT_ARMOR_PER_LEVEL,
                "the skeleton, which matches no rule");
        });
        helper.succeed();
    }

    private static void assertAdminLevel(
        GameTestHelper helper,
        LivingEntity mob,
        double healthPerLevel,
        double armorPerLevel,
        String scenario
    ) {
        LevelledMobData data = ((LevelledMobHolder) mob).lampas$getLevelData();
        helper.assertTrue(data != null && data.levelled(), scenario + " was not levelled");
        helper.assertValueEqual(data.level(), LEVEL, "the admin-set level of " + scenario);
        helper.assertValueEqual(data.ruleSet(), "custom", "the rule set marker of " + scenario);
        helper.assertTrue(data.groupFactor() == LevelledMobData.NO_GROUP_FACTOR,
            "the group factor of " + scenario + ": " + data.groupFactor());

        double expectedHealth = mob.getAttribute(Attributes.MAX_HEALTH).getBaseValue() + healthPerLevel * (LEVEL - 1);
        helper.assertTrue(Math.abs(mob.getMaxHealth() - expectedHealth) < 1e-6,
            "the max health of " + scenario + ": expected " + expectedHealth + ", got " + mob.getMaxHealth());
        double expectedArmor = mob.getAttribute(Attributes.ARMOR).getBaseValue() + armorPerLevel * (LEVEL - 1);
        double armor = mob.getAttributeValue(Attributes.ARMOR);
        helper.assertTrue(Math.abs(armor - expectedArmor) < 1e-6,
            "the armor of " + scenario + ": expected " + expectedArmor + ", got " + armor);
    }

    /** A rule for one entity with explicit per-level max health and armor and a band below the admin-set level. */
    private static LevelRule adminRule(String id, String entityId, double healthPerLevel, double armorPerLevel) {
        return RuleParser.parseRule(id, Map.of(
            "priority", 10,
            "min_level", 1,
            "max_level", 1,
            "conditions", Map.of("entities", Map.of("include", List.of(entityId))),
            "attributes", Map.of("max_health", healthPerLevel, "armor", armorPerLevel)
        ));
    }

    private static <E extends Mob> E createAt(GameTestHelper helper, EntityType<E> type, Vec3 position) {
        E mob = type.create(helper.getLevel(), EntitySpawnReason.NATURAL);
        helper.assertTrue(mob != null, "the test " + type.getDescription().getString() + " could not be created");
        mob.addTag(ApothMobEvents.NO_RANDOM_PROCESSING);
        mob.setBaby(false);
        mob.snapTo(position);
        return mob;
    }

    private static void withRules(GameTestHelper helper, List<LevelRule> rules, Runnable body) {
        RuleManager ruleManager = LevelledMobsModule.getRuleManager();
        helper.assertTrue(ruleManager != null && LevelledMobsModule.getMobLevelingService() != null,
            "LevelledMobs did not initialize its live services");
        List<LevelRule> previous = List.copyOf(ruleManager.getRules());
        ruleManager.setRules(rules);
        try {
            body.run();
        } finally {
            ruleManager.setRules(previous);
        }
    }
}
