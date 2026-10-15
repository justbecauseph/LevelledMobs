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
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/**
 * Live coverage for LevelledMobs' projectile and explosion multipliers, +5% per level above 1. Player overrides
 * actuallyHurt without calling super, so players need their own coverage, and a mob victim must take each multiplier
 * once. Only the mob's own blast is multiplied, not TNT it sets off, and melee hits are left alone.
 */
public final class RangedDamageGameTests {
    private static final String RULE_ID = "gametest-ranged-damage";
    private static final int LEVEL = 21;
    /** 1 + 0.05 * (21 - 1) */
    private static final double MULTIPLIER = 2.0;
    private static final float ARROW_DAMAGE = 6.0f;
    private static final float EXPLOSION_DAMAGE = 20.0f;
    private static final double VICTIM_HEALTH = 1000.0;

    @GameTest(maxTicks = 20)
    public void levelledMobArrowsAndExplosionsAreMultipliedOnceForPlayersAndMobs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Skeleton skeleton = levelledAttacker(helper, EntityTypes.SKELETON, new BlockPos(4, 2, 1));
        Creeper creeper = levelledAttacker(helper, EntityTypes.CREEPER, new BlockPos(4, 2, 4));

        DamageSource arrow = level.damageSources().arrow(
            createAt(helper, EntityTypes.ARROW, new BlockPos(4, 3, 1)), skeleton);
        // The source vanilla gives a creeper's blast: it is both the direct and the causing entity.
        DamageSource blast = level.damageSources().explosion(creeper, creeper);
        // TNT the creeper set off, and a melee hit, which grows through attack_damage instead.
        DamageSource chainedTnt = level.damageSources().explosion(
            createAt(helper, EntityTypes.TNT, new BlockPos(4, 2, 6)), creeper);
        DamageSource melee = level.damageSources().mobAttack(skeleton);

        // Normal difficulty leaves mob damage to players as is.
        Difficulty previousDifficulty = level.getDifficulty();
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
        try {
            assertDamageTaken(helper, victimPlayer(helper), arrow, ARROW_DAMAGE, ARROW_DAMAGE * MULTIPLIER,
                "a levelled skeleton's arrow hitting a player");
            assertDamageTaken(helper, victimPlayer(helper), blast, EXPLOSION_DAMAGE, EXPLOSION_DAMAGE * MULTIPLIER,
                "a levelled creeper's explosion hitting a player");
            assertDamageTaken(helper, victimPlayer(helper), chainedTnt, EXPLOSION_DAMAGE, EXPLOSION_DAMAGE,
                "TNT a levelled creeper set off hitting a player");
            assertDamageTaken(helper, victimPlayer(helper), melee, ARROW_DAMAGE, ARROW_DAMAGE,
                "a levelled skeleton's melee hit on a player");
        } finally {
            level.getServer().setDifficulty(previousDifficulty, true);
        }

        assertDamageTaken(helper, victimMob(helper), arrow, ARROW_DAMAGE, ARROW_DAMAGE * MULTIPLIER,
            "a levelled skeleton's arrow hitting a mob");
        assertDamageTaken(helper, victimMob(helper), blast, EXPLOSION_DAMAGE, EXPLOSION_DAMAGE * MULTIPLIER,
            "a levelled creeper's explosion hitting a mob");
        helper.succeed();
    }

    /** An attacker levelled to {@link #LEVEL} by a fixed-level rule, with Apothic Attributes' random crits cleared. */
    private static <E extends Mob> E levelledAttacker(GameTestHelper helper, EntityType<E> type, BlockPos relativePos) {
        E mob = createAt(helper, type, relativePos);
        withoutCrits(helper, mob);

        RuleManager ruleManager = LevelledMobsModule.getRuleManager();
        MobLevelingService levelingService = LevelledMobsModule.getMobLevelingService();
        helper.assertTrue(ruleManager != null && levelingService != null,
            "LevelledMobs did not initialize its live services");
        List<LevelRule> previousRules = List.copyOf(ruleManager.getRules());
        ruleManager.setRules(List.of(RuleParser.parseRule(RULE_ID, Map.of(
            "priority", 100,
            "min_level", LEVEL,
            "max_level", LEVEL
        ))));
        try {
            levelingService.level(mob, SpawnReason.NATURAL);
        } finally {
            ruleManager.setRules(previousRules);
        }

        String name = "the test " + type.getDescription().getString();
        helper.assertTrue(mob instanceof LevelledMobHolder,
            "the LevelledMobs data-holder mixin was not applied to " + name);
        LevelledMobData data = ((LevelledMobHolder) mob).lampas$getLevelData();
        helper.assertTrue(data != null && data.levelled(), name + " was not levelled");
        helper.assertValueEqual(data.level(), LEVEL, "the LevelledMobs level of " + name);
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
