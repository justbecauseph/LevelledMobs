package lampas.levelledmobs.apotheosis.gametest;

import dev.shadowsoffire.apotheosis.mobs.ApothMobEvents;
import lampas.levelledmobs.LevelledMobsModule;
import lampas.levelledmobs.rules.LevelRule;
import lampas.levelledmobs.rules.RuleManager;
import lampas.levelledmobs.rules.RuleParser;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/**
 * Live coverage for the death-XP mixin: a levelled mob's XP reward must follow the xp settings merged from the
 * rules that match it, in the same layout the Lampas pack ships (a dimension rule carries the xp block, a
 * higher-priority tier rule carries none).
 *
 * <p>Each test swaps the shared rule set and restores it before returning, so it never leaks into a test that
 * spans ticks.</p>
 */
public final class DeathXpGameTests {
    private static final int LEVEL = 200;
    /** Vanilla zombie experience reward with no equipment. */
    private static final int ZOMBIE_BASE_XP = 5;

    @GameTest(maxTicks = 20)
    public void deathXpUsesMergedRuleXpMultiplier(GameTestHelper helper) {
        withRules(helper, List.of(
            tierRuleWithoutXp(),
            RuleParser.parseRule("gametest-xp-overworld", Map.of(
                "priority", 1,
                "min_level", 1,
                "max_level", 50,
                "conditions", Map.of("dimensions", Map.of("include", List.of("minecraft:overworld"))),
                "xp", Map.of("multiplier", 0.05)
            )),
            // Matches no GameTest mob: its multiplier must not leak into the Overworld result.
            RuleParser.parseRule("gametest-xp-nether", Map.of(
                "priority", 10,
                "min_level", 10,
                "max_level", 60,
                "conditions", Map.of("dimensions", Map.of("include", List.of("minecraft:the_nether"))),
                "xp", Map.of("multiplier", 0.08)
            ))
        ), () -> {
            // 5 * (1 + 199 * 0.05) = 54.75 -> 55. The global 0.10 default would give 105.
            assertDeathXp(helper, 55, "a level 200 mob under the 0.05 Overworld xp rule");
        });
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void deathXpFallsBackToGlobalDefaultWithoutRuleXp(GameTestHelper helper) {
        double globalDefault = LevelledMobsModule.getConfigLoader().getCurrentConfig().defaultXpMultiplier();
        int expected = (int) Math.round(ZOMBIE_BASE_XP * (1.0 + (LEVEL - 1) * globalDefault));

        withRules(helper, List.of(tierRuleWithoutXp()), () ->
            assertDeathXp(helper, expected, "a level 200 mob whose rules carry no xp block"));
        helper.succeed();
    }

    private static LevelRule tierRuleWithoutXp() {
        return RuleParser.parseRule("gametest-xp-tier", Map.of(
            "priority", 100,
            "min_level", 1,
            "max_level", LEVEL
        ));
    }

    private static void withRules(GameTestHelper helper, List<LevelRule> rules, Runnable body) {
        RuleManager ruleManager = LevelledMobsModule.getRuleManager();
        helper.assertTrue(ruleManager != null && LevelledMobsModule.getMobLevelingService() != null
                && LevelledMobsModule.getConfigLoader() != null,
            "LevelledMobs did not initialize its live services");
        List<LevelRule> previous = List.copyOf(ruleManager.getRules());
        ruleManager.setRules(rules);
        try {
            body.run();
        } finally {
            ruleManager.setRules(previous);
        }
    }

    private static void assertDeathXp(GameTestHelper helper, int expected, String scenario) {
        Zombie zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.NATURAL);
        helper.assertTrue(zombie != null, "the death-XP test zombie could not be created");
        zombie.addTag(ApothMobEvents.NO_RANDOM_PROCESSING);
        zombie.setBaby(false);
        zombie.snapTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));

        helper.assertValueEqual(zombie.getExperienceReward(helper.getLevel(), null), ZOMBIE_BASE_XP,
            "the unlevelled zombie's vanilla experience reward");

        LevelledMobsModule.getMobLevelingService().setLevel(zombie, LEVEL, "gametest-xp-tier");
        helper.assertValueEqual(zombie.getExperienceReward(helper.getLevel(), null), expected,
            "the death experience reward of " + scenario);
    }
}
