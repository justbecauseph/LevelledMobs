package lampas.levelledmobs.apotheosis.gametest;

import lampas.levelledmobs.LevelledMobsModule;
import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import lampas.levelledmobs.data.SpawnReason;
import lampas.levelledmobs.level.MobLevelingService;
import lampas.levelledmobs.rules.LevelRule;
import lampas.levelledmobs.rules.RuleManager;
import lampas.levelledmobs.rules.RuleParser;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/**
 * Live coverage for LevelledMobs' projectile and explosion damage. A rule sets the fraction per level above 1 with
 * "projectile_damage" and "explosion_damage". A mob whose rule sets neither uses the settings defaults, 0.05 for
 * projectiles and 0.01 for explosions. Player overrides actuallyHurt without calling super, so players need their own
 * coverage, and a mob victim must take each multiplier once. Only the mob's own blast is multiplied, not TNT it sets
 * off, and melee hits are left alone.
 *
 * <p>Each test swaps the shared rule set and restores it before returning, so it never leaks into a test that spans
 * ticks.</p>
 */
public final class RangedDamageGameTests {
    private static final String RULE_ID = "gametest-ranged-damage";
    /** The fractions the Lampas pack ships: 0.05 per level for projectiles and 0.01 for explosions. */
    private static final Map<String, Object> PACK_DAMAGE = Map.of("projectile_damage", 0.05, "explosion_damage", 0.01);
    private static final int LEVEL = 21;
    private static final int HIGH_LEVEL = 200;
    /** 1 + 0.05 * (21 - 1), with the default projectile fraction. */
    private static final double DEFAULT_ARROW_MULTIPLIER = 2.0;
    /** 1 + 0.01 * (21 - 1), with the default explosion fraction. */
    private static final double DEFAULT_BLAST_MULTIPLIER = 1.2;
    /** 1 + 0.05 * (200 - 1) */
    private static final double HIGH_ARROW_MULTIPLIER = 10.95;
    /** 1 + 0.01 * (200 - 1) */
    private static final double HIGH_BLAST_MULTIPLIER = 2.99;
    private static final float ARROW_DAMAGE = 6.0f;
    private static final float EXPLOSION_DAMAGE = 20.0f;
    private static final double VICTIM_HEALTH = 1000.0;

    @GameTest(maxTicks = 20)
    public void levelledMobArrowsAndExplosionsUseSettingsDefaultsOnceForPlayersAndMobs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        withRules(helper, List.of(levelRule(LEVEL, Map.of())), () -> {
            Skeleton skeleton = levelledAttacker(helper, EntityTypes.SKELETON, new BlockPos(4, 2, 1), LEVEL);
            Creeper creeper = levelledAttacker(helper, EntityTypes.CREEPER, new BlockPos(4, 2, 4), LEVEL);
            assertRangedDamage(helper, skeleton, creeper, DEFAULT_ARROW_MULTIPLIER, DEFAULT_BLAST_MULTIPLIER);

            // TNT the creeper set off, and a melee hit, which grows through attack_damage instead.
            DamageSource chainedTnt = level.damageSources().explosion(
                createAt(helper, EntityTypes.TNT, new BlockPos(4, 2, 6)), creeper);
            DamageSource melee = level.damageSources().mobAttack(skeleton);
            withNormalDifficulty(helper, () -> {
                assertDamageTaken(helper, victimPlayer(helper), chainedTnt, EXPLOSION_DAMAGE, EXPLOSION_DAMAGE,
                    "TNT a levelled creeper set off hitting a player");
                assertDamageTaken(helper, victimPlayer(helper), melee, ARROW_DAMAGE, ARROW_DAMAGE,
                    "a levelled skeleton's melee hit on a player");
            });
        });
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void packRuleDamageScalesArrowsAndBlastsAtLevel200(GameTestHelper helper) {
        withRules(helper, List.of(levelRule(HIGH_LEVEL, PACK_DAMAGE)), () -> {
            Skeleton skeleton = levelledAttacker(helper, EntityTypes.SKELETON, new BlockPos(4, 2, 1), HIGH_LEVEL);
            Creeper creeper = levelledAttacker(helper, EntityTypes.CREEPER, new BlockPos(4, 2, 4), HIGH_LEVEL);
            assertRangedDamage(helper, skeleton, creeper, HIGH_ARROW_MULTIPLIER, HIGH_BLAST_MULTIPLIER);
        });
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void explosionDamageZeroTurnsBlastsOffWhileArrowsKeepTheirFraction(GameTestHelper helper) {
        withRules(helper, List.of(levelRule(HIGH_LEVEL, Map.of("explosion_damage", 0.0))), () -> {
            Skeleton skeleton = levelledAttacker(helper, EntityTypes.SKELETON, new BlockPos(4, 2, 1), HIGH_LEVEL);
            Creeper creeper = levelledAttacker(helper, EntityTypes.CREEPER, new BlockPos(4, 2, 4), HIGH_LEVEL);
            // The rule sets no projectile fraction, so the arrow uses the settings default.
            assertRangedDamage(helper, skeleton, creeper, HIGH_ARROW_MULTIPLIER, 1.0);
        });
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void ruleProjectileDamageOverridesTheSettingsDefault(GameTestHelper helper) {
        withRules(helper, List.of(levelRule(LEVEL, Map.of("projectile_damage", 0.02))), () -> {
            Skeleton skeleton = levelledAttacker(helper, EntityTypes.SKELETON, new BlockPos(4, 2, 1), LEVEL);
            Creeper creeper = levelledAttacker(helper, EntityTypes.CREEPER, new BlockPos(4, 2, 4), LEVEL);
            // 1 + 0.02 x 20 = 1.4 from the rule, not the 0.05 default's 2.0; the rule sets no explosion fraction,
            // so the blast keeps the settings default, 1 + 0.01 x 20 = 1.2.
            assertRangedDamage(helper, skeleton, creeper, 1.4, DEFAULT_BLAST_MULTIPLIER);
        });
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void explosionDamageSurvivesAnNbtReloadAndDefaultsForLegacySaves(GameTestHelper helper) {
        withRules(helper, List.of(levelRule(HIGH_LEVEL, Map.of("explosion_damage", 0.0))), () -> {
            Creeper creeper = levelledAttacker(helper, EntityTypes.CREEPER, new BlockPos(4, 2, 4), HIGH_LEVEL);

            // The stored override survives a save and load: a 0.0 rule still gives no blast multiplier.
            Creeper reloaded = reload(helper, creeper, false);
            assertBlastOnMob(helper, reloaded, 1.0, "a creeper reloaded with its stored explosion fraction");

            // A save from before the field existed has no key, so the reloaded creeper takes the settings default.
            Creeper legacy = reload(helper, creeper, true);
            assertBlastOnMob(helper, legacy, HIGH_BLAST_MULTIPLIER, "a creeper saved without the explosion key");
        });
        helper.succeed();
    }

    /** Checks a levelled skeleton's arrow and a creeper's blast against a player and against a mob, each once. */
    private static void assertRangedDamage(GameTestHelper helper, Skeleton skeleton, Creeper creeper,
                                           double arrowMultiplier, double blastMultiplier) {
        ServerLevel level = helper.getLevel();
        DamageSource arrow = level.damageSources().arrow(
            createAt(helper, EntityTypes.ARROW, new BlockPos(4, 3, 1)), skeleton);
        // The source vanilla gives a creeper's blast: it is both the direct and the causing entity.
        DamageSource blast = level.damageSources().explosion(creeper, creeper);

        withNormalDifficulty(helper, () -> {
            assertDamageTaken(helper, victimPlayer(helper), arrow, ARROW_DAMAGE, ARROW_DAMAGE * arrowMultiplier,
                "a levelled skeleton's arrow hitting a player");
            assertDamageTaken(helper, victimPlayer(helper), blast, EXPLOSION_DAMAGE, EXPLOSION_DAMAGE * blastMultiplier,
                "a levelled creeper's explosion hitting a player");
        });

        assertDamageTaken(helper, victimMob(helper), arrow, ARROW_DAMAGE, ARROW_DAMAGE * arrowMultiplier,
            "a levelled skeleton's arrow hitting a mob");
        assertDamageTaken(helper, victimMob(helper), blast, EXPLOSION_DAMAGE, EXPLOSION_DAMAGE * blastMultiplier,
            "a levelled creeper's explosion hitting a mob");
    }

    private static void assertBlastOnMob(GameTestHelper helper, Creeper creeper, double blastMultiplier, String scenario) {
        DamageSource blast = helper.getLevel().damageSources().explosion(creeper, creeper);
        assertDamageTaken(helper, victimMob(helper), blast, EXPLOSION_DAMAGE, EXPLOSION_DAMAGE * blastMultiplier, scenario);
    }

    /** Normal difficulty leaves mob damage to players as is. The previous difficulty is restored afterwards. */
    private static void withNormalDifficulty(GameTestHelper helper, Runnable body) {
        ServerLevel level = helper.getLevel();
        Difficulty previousDifficulty = level.getDifficulty();
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
        try {
            body.run();
        } finally {
            level.getServer().setDifficulty(previousDifficulty, true);
        }
    }

    private static LevelRule levelRule(int level, Map<String, Object> attributes) {
        return RuleParser.parseRule(RULE_ID, Map.of(
            "priority", 100,
            "min_level", level,
            "max_level", level,
            "attributes", attributes
        ));
    }

    private static void withRules(GameTestHelper helper, List<LevelRule> rules, Runnable body) {
        RuleManager ruleManager = LevelledMobsModule.getRuleManager();
        helper.assertTrue(ruleManager != null && LevelledMobsModule.getMobLevelingService() != null,
            "LevelledMobs did not initialize its live services");
        List<LevelRule> previousRules = List.copyOf(ruleManager.getRules());
        ruleManager.setRules(rules);
        try {
            body.run();
        } finally {
            ruleManager.setRules(previousRules);
        }
    }

    /**
     * An attacker levelled to the given level by the rule set the caller has installed, with Apothic Attributes'
     * random crits cleared.
     */
    private static <E extends Mob> E levelledAttacker(GameTestHelper helper, EntityType<E> type, BlockPos relativePos,
                                                      int level) {
        E mob = createAt(helper, type, relativePos);
        withoutCrits(helper, mob);

        MobLevelingService levelingService = LevelledMobsModule.getMobLevelingService();
        helper.assertTrue(levelingService != null, "LevelledMobs did not initialize its leveling service");
        levelingService.level(mob, SpawnReason.NATURAL);

        String name = "the test " + type.getDescription().getString();
        helper.assertTrue(mob instanceof LevelledMobHolder,
            "the LevelledMobs data-holder mixin was not applied to " + name);
        LevelledMobData data = ((LevelledMobHolder) mob).lampas$getLevelData();
        helper.assertTrue(data != null && data.levelled(), name + " was not levelled");
        helper.assertValueEqual(data.level(), level, "the LevelledMobs level of " + name);
        return mob;
    }

    /**
     * Apothic Attributes gives every mob a chance to critically strike any hit it causes, which would multiply the
     * measured damage at random.
     */
    private static void withoutCrits(GameTestHelper helper, LivingEntity attacker) {
        BuiltInRegistries.ATTRIBUTE.get(Identifier.fromNamespaceAndPath("apothic_attributes", "crit_chance"))
            .ifPresent(critChance -> {
                var instance = attacker.getAttribute(critChance);
                helper.assertTrue(instance != null, "the test attacker has no Apothic crit_chance to clear");
                instance.setBaseValue(0.0);
                helper.assertValueEqual(instance.getValue(), 0.0, "the test attacker's crit chance");
            });
    }

    private static void assertDamageTaken(
        GameTestHelper helper,
        LivingEntity victim,
        DamageSource source,
        float dealt,
        double expectedTaken,
        String scenario
    ) {
        float before = victim.getHealth();
        helper.assertTrue(victim.hurtServer(helper.getLevel(), source, dealt), scenario + " did not land");
        double taken = before - victim.getHealth();
        helper.assertTrue(Math.abs(taken - expectedTaken) < 1e-3,
            "damage taken from " + scenario + ": expected " + expectedTaken + ", got " + taken);
    }

    /** Saves the creeper and loads a copy from the saved data, optionally without the explosion key. */
    private static Creeper reload(GameTestHelper helper, Creeper creeper, boolean withoutExplosionKey) {
        try (var reporter = new ProblemReporter.ScopedCollector(creeper.problemPath(), LevelledMobsModule.LOGGER)) {
            var output = TagValueOutput.createWithContext(reporter, helper.getLevel().registryAccess());
            creeper.saveWithoutId(output);
            CompoundTag saved = output.buildResult();
            if (withoutExplosionKey) {
                saved.remove("lampas:explosion_damage");
            }
            Creeper loaded = EntityTypes.CREEPER.create(helper.getLevel(), EntitySpawnReason.LOAD);
            helper.assertTrue(loaded != null, "the reloaded creeper could not be created");
            loaded.load(TagValueInput.create(reporter, helper.getLevel().registryAccess(), saved));
            withoutCrits(helper, loaded);
            return loaded;
        }
    }

    private static LivingEntity victimMob(GameTestHelper helper) {
        return withVictimHealth(createAt(helper, EntityTypes.PIG, new BlockPos(1, 2, 2)));
    }

    private static LivingEntity victimPlayer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.snapTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 2))));
        return withVictimHealth(player);
    }

    private static LivingEntity withVictimHealth(LivingEntity victim) {
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(VICTIM_HEALTH);
        victim.setHealth((float) VICTIM_HEALTH);
        return victim;
    }

    private static <E extends Entity> E createAt(GameTestHelper helper, EntityType<E> type, BlockPos relativePos) {
        E entity = type.create(helper.getLevel(), EntitySpawnReason.NATURAL);
        helper.assertTrue(entity != null, "the test " + type.getDescription().getString() + " could not be created");
        entity.snapTo(Vec3.atCenterOf(helper.absolutePos(relativePos)));
        return entity;
    }
}
