package lampas.levelledmobs.apotheosis.gametest;

import dev.shadowsoffire.apotheosis.mobs.ApothMobEvents;
import lampas.levelledmobs.LevelledMobsModule;
import lampas.levelledmobs.attributes.AttributeScalingService;
import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import lampas.levelledmobs.data.SpawnReason;
import lampas.levelledmobs.level.BossClassifier;
import lampas.levelledmobs.level.BossScaling;
import lampas.levelledmobs.level.MobLevelingService;
import lampas.levelledmobs.level.MobProcessingQueue;
import lampas.levelledmobs.rules.LevelRule;
import lampas.levelledmobs.rules.RuleManager;
import lampas.levelledmobs.rules.RuleParser;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Live coverage for the combat balance rollout's mob selection and multi-party bosses: which mobs LevelledMobs
 * levels, bosses under a boss rule, the group factor on boss health and the multiplier on boss damage that does not
 * come from attack_damage.
 *
 * <p>Each test swaps the shared rule set and the boss levelling switch and restores both before returning. Bosses
 * sit at a point far from the GameTest grid, so mock players other tests leave in the level are never within the
 * 128-block group radius. Without AttributeFix, vanilla caps max health at 1024, so the health tests use level 2.</p>
 */
public final class BossLevellingGameTests {
    private static final String TIER_RULE = "gametest-boss-tier";
    /** The boss rule's max health per level, as the pack's boss_scaling rule sets it. */
    private static final double BOSS_HEALTH_PER_LEVEL = 290.0;
    private static final List<String> BOSS_IDS = List.of(
        "minecraft:ender_dragon", "minecraft:wither", "minecraft:warden", "minecraft:elder_guardian");
    /** Highest level whose boss health stays under vanilla's 1024 cap with a 1.5 group factor. */
    private static final int HEALTH_LEVEL = 2;
    private static final int DAMAGE_LEVEL = 200;
    /** Vanilla wither skull damage to a living target. */
    private static final float WITHER_SKULL_DAMAGE = 8.0f;
    /** Vanilla Ender Dragon head damage. */
    private static final float DRAGON_HEAD_DAMAGE = 10.0f;
    /** Vanilla Warden sonic boom damage. */
    private static final float SONIC_BOOM_DAMAGE = 10.0f;
    /** Vanilla Elder Guardian beam damage below Hard difficulty: 1, plus 2 for the elder. */
    private static final float ELDER_BEAM_DAMAGE = 3.0f;
    private static final double VICTIM_HEALTH = 1000.0;

    @GameTest(maxTicks = 20)
    public void enemyMobsAreLevelledAndCubeMobsAreNot(GameTestHelper helper) {
        withRules(helper, List.of(tierRule(5)), false, () -> {
            MobProcessingQueue queue = LevelledMobsModule.getProcessingQueue();
            int previousBudget = queue.maxProcessTimeMs();
            List<Entity> added = new ArrayList<>();
            try {
                queue.clear();
                Phantom phantom = addMob(helper, EntityTypes.PHANTOM, new BlockPos(1, 3, 1), added);
                Hoglin hoglin = addMob(helper, EntityTypes.HOGLIN, new BlockPos(3, 2, 1), added);
                Ghast ghast = addMob(helper, EntityTypes.GHAST, new BlockPos(5, 3, 5), added);
                Slime slime = addMob(helper, EntityTypes.SLIME, new BlockPos(1, 2, 5), added);
                MagmaCube magmaCube = addMob(helper, EntityTypes.MAGMA_CUBE, new BlockPos(3, 2, 5), added);

                helper.assertValueEqual(queue.size(), 3,
                    "entity-load callbacks enqueued only the phantom, hoglin and ghast");
                queue.setMaxProcessTimeMs(50);
                helper.assertTrue(queue.processTick() >= 3, "LevelledMobs did not process the queued Enemy mobs");

                assertLevel(helper, phantom, 5, "the phantom");
                assertLevel(helper, hoglin, 5, "the hoglin");
                assertLevel(helper, ghast, 5, "the ghast");

                // The levelling service refuses cube mobs even when asked directly.
                levelingService().level(slime, SpawnReason.NATURAL);
                levelingService().level(magmaCube, SpawnReason.NATURAL);
                assertUnlevelled(helper, slime, "the slime");
                assertUnlevelled(helper, magmaCube, "the magma cube");
            } finally {
                queue.setMaxProcessTimeMs(previousBudget);
                added.forEach(Entity::discard);
            }
        });
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void bossesAreLevelledUnderTheBossRule(GameTestHelper helper) {
        BossClassifier classifier = LevelledMobsModule.getBossClassifier();
        helper.assertTrue(classifier != null && classifier == levelingService().getBossClassifier(),
            "the levelling service does not use the module's boss classifier");
        helper.assertValueEqual(classifier.isBossLevelingAllowed(),
            LevelledMobsModule.getConfigLoader().getCurrentConfig().allowBossLeveling(),
            "the boss classifier's switch after the config load");

        withRules(helper, bossRules(HEALTH_LEVEL), false, () -> {
            WitherBoss wither = createAt(helper, EntityTypes.WITHER, remotePoint(helper));
            levelingService().level(wither, SpawnReason.NATURAL);
            assertUnlevelled(helper, wither, "a Wither while allowBossLeveling is off");
        });

        withRules(helper, bossRules(HEALTH_LEVEL), true, () -> {
            WitherBoss wither = createAt(helper, EntityTypes.WITHER, remotePoint(helper));
            Warden warden = createAt(helper, EntityTypes.WARDEN, remotePoint(helper).add(4, 0, 0));
            assertNoPlayersNear(helper, wither);

            levelingService().level(wither, SpawnReason.NATURAL);
            levelingService().level(warden, SpawnReason.NATURAL);

            assertBossHealth(helper, wither, HEALTH_LEVEL, 1.0, "the Wither");
            assertBossHealth(helper, warden, HEALTH_LEVEL, 1.0, "the Warden");
        });
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void groupFactorScalesBossHealthWithNearbyPlayers(GameTestHelper helper) {
        withRules(helper, bossRules(HEALTH_LEVEL), true, () -> {
            levelWitherWithPlayers(helper, 1, 1.0);
            levelWitherWithPlayers(helper, 10, 1.0);
            WitherBoss wither = levelWitherWithPlayers(helper, 15, 1.5);

            // The factor survives an NBT round trip, and a restore re-applies it with no players nearby.
            WitherBoss reloaded = reload(helper, wither);
            LevelledMobData data = ((LevelledMobHolder) wither).lampas$getLevelData();
            helper.assertTrue(data.equals(((LevelledMobHolder) reloaded).lampas$getLevelData()),
                "the Wither's level data, group factor included, did not survive an NBT round trip");
            reloaded.getAttribute(Attributes.MAX_HEALTH).removeModifier(AttributeScalingService.GROUP_FACTOR_MODIFIER_ID);
            levelingService().onEntityLoad(reloaded, SpawnReason.NATURAL);
            assertBossHealth(helper, reloaded, HEALTH_LEVEL, 1.5, "the reloaded Wither");
        });
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void levelledBossFixedDamageIsMultipliedAndAttackDamageHitsAreNot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WitherBoss wither = createAt(helper, EntityTypes.WITHER, remotePoint(helper));
        EnderDragon dragon = createAt(helper, EntityTypes.ENDER_DRAGON, remotePoint(helper).add(0, 0, 8));
        Warden warden = createAt(helper, EntityTypes.WARDEN, remotePoint(helper).add(4, 0, 0));
        ElderGuardian elderGuardian = createAt(helper, EntityTypes.ELDER_GUARDIAN, remotePoint(helper).add(8, 0, 0));
        List.of(wither, dragon, warden, elderGuardian).forEach(boss -> withoutCrits(helper, boss));
        withRules(helper, bossRules(DAMAGE_LEVEL), true, () -> {
            levelingService().level(wither, SpawnReason.NATURAL);
            levelingService().level(dragon, SpawnReason.NATURAL);
            levelingService().level(warden, SpawnReason.NATURAL);
            levelingService().level(elderGuardian, SpawnReason.NATURAL);
        });
        assertLevel(helper, wither, DAMAGE_LEVEL, "the damage-test Wither");
        assertLevel(helper, dragon, DAMAGE_LEVEL, "the damage-test Ender Dragon");
        assertLevel(helper, warden, DAMAGE_LEVEL, "the damage-test Warden");
        assertLevel(helper, elderGuardian, DAMAGE_LEVEL, "the damage-test Elder Guardian");

        // 1 + 0.025 * (200 - 1) = 5.975
        double multiplier = 1.0 + 0.025 * (DAMAGE_LEVEL - 1);
        DamageSource skull = level.damageSources().witherSkull(new WitherSkull(level, wither, Vec3.ZERO), wither);
        DamageSource sonicBoom = level.damageSources().sonicBoom(warden);
        DamageSource elderBeam = level.damageSources().indirectMagic(elderGuardian, elderGuardian);
        DamageSource wardenMelee = level.damageSources().mobAttack(warden);
        DamageSource elderMelee = level.damageSources().mobAttack(elderGuardian);
        float wardenMeleeDamage = scaledAttackDamage(helper, warden, "Warden");
        float elderMeleeDamage = scaledAttackDamage(helper, elderGuardian, "Elder Guardian");

        // Fixed-amount boss damage is multiplied.
        assertDamageTaken(helper, victimMob(helper), skull, WITHER_SKULL_DAMAGE,
            WITHER_SKULL_DAMAGE * multiplier, "a levelled Wither's skull hitting a mob");
        assertDamageTaken(helper, victimMob(helper), level.damageSources().mobAttack(dragon), DRAGON_HEAD_DAMAGE,
            DRAGON_HEAD_DAMAGE * multiplier, "a levelled Ender Dragon's head hitting a mob");
        assertDamageTaken(helper, victimMob(helper), sonicBoom, SONIC_BOOM_DAMAGE,
            SONIC_BOOM_DAMAGE * multiplier, "a levelled Warden's sonic boom hitting a mob");
        assertDamageTaken(helper, victimMob(helper), elderBeam, ELDER_BEAM_DAMAGE,
            ELDER_BEAM_DAMAGE * multiplier, "a levelled Elder Guardian's beam hitting a mob");
        // Melee hits already carry the levelled attack_damage and are not multiplied again.
        assertDamageTaken(helper, victimMob(helper), wardenMelee, wardenMeleeDamage, wardenMeleeDamage,
            "a levelled Warden's melee hit on a mob");
        assertDamageTaken(helper, victimMob(helper), elderMelee, elderMeleeDamage, elderMeleeDamage,
            "a levelled Elder Guardian's melee hit on a mob");

        // Players override actuallyHurt, so they need their own coverage. Normal difficulty leaves mob damage as is.
        Difficulty previousDifficulty = level.getDifficulty();
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
        try {
            assertDamageTaken(helper, victimPlayer(helper), skull, WITHER_SKULL_DAMAGE,
                WITHER_SKULL_DAMAGE * multiplier, "a levelled Wither's skull hitting a player");
            assertDamageTaken(helper, victimPlayer(helper), sonicBoom, SONIC_BOOM_DAMAGE,
                SONIC_BOOM_DAMAGE * multiplier, "a levelled Warden's sonic boom hitting a player");
            assertDamageTaken(helper, victimPlayer(helper), wardenMelee, wardenMeleeDamage, wardenMeleeDamage,
                "a levelled Warden's melee hit on a player");
        } finally {
            level.getServer().setDifficulty(previousDifficulty, true);
        }
        helper.succeed();
    }

    /**
     * Apothic Attributes gives every mob a chance to critically strike any hit it causes, which would multiply the
     * measured damage at random.
     */
    private static void withoutCrits(GameTestHelper helper, LivingEntity attacker) {
        BuiltInRegistries.ATTRIBUTE.get(Identifier.fromNamespaceAndPath("apothic_attributes", "crit_chance"))
            .ifPresent(critChance -> {
                var instance = attacker.getAttribute(critChance);
                helper.assertTrue(instance != null, "the test boss has no Apothic crit_chance to clear");
                instance.setBaseValue(0.0);
                helper.assertValueEqual(instance.getValue(), 0.0, "the test boss's crit chance");
            });
    }

    /** The boss's attack_damage, which must already include its level's modifier. */
    private static float scaledAttackDamage(GameTestHelper helper, LivingEntity boss, String name) {
        float damage = (float) boss.getAttributeValue(Attributes.ATTACK_DAMAGE);
        helper.assertTrue(damage > boss.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue(),
            "the levelled " + name + "'s attack_damage did not scale with its level");
        return damage;
    }

    @SuppressWarnings("removal")
    private static WitherBoss levelWitherWithPlayers(GameTestHelper helper, int nearbyPlayers, double expectedFactor) {
        Vec3 remote = remotePoint(helper);
        WitherBoss wither = createAt(helper, EntityTypes.WITHER, remote);
        // Admin-set, as /lm summon does: the first level counts the same players.
        WitherBoss summoned = createAt(helper, EntityTypes.WITHER, remote.add(0, 0, 4));
        assertNoPlayersNear(helper, wither);

        List<ServerPlayer> players = new ArrayList<>();
        try {
            for (int i = 0; i < nearbyPlayers; i++) {
                ServerPlayer player = helper.makeMockServerPlayerInLevel();
                player.setPos(remote.add(3 * (i % 5), 0, 3 * (i / 5)));
                players.add(player);
            }
            // Just outside the 128-block radius: never counted.
            ServerPlayer outside = helper.makeMockServerPlayerInLevel();
            outside.setPos(remote.add(130, 0, 0));
            players.add(outside);

            levelingService().level(wither, SpawnReason.NATURAL);
            levelingService().setLevel(summoned, HEALTH_LEVEL, "custom");
        } finally {
            players.forEach(player -> helper.getLevel().getServer().getPlayerList().remove(player));
        }
        assertBossHealth(helper, wither, HEALTH_LEVEL, expectedFactor,
            "a Wither levelled with " + nearbyPlayers + " nearby players");
        double summonedFactor = ((LevelledMobHolder) summoned).lampas$getLevelData().groupFactor();
        helper.assertTrue(Math.abs(summonedFactor - expectedFactor) < 1e-9, "the group factor of an admin-set Wither with "
            + nearbyPlayers + " nearby players: expected " + expectedFactor + ", got " + summonedFactor);
        helper.assertTrue(Math.abs(summoned.getHealth() - summoned.getMaxHealth()) < 1e-3,
            "the admin-set Wither is not at full health");
        return wither;
    }

    private static void assertBossHealth(
        GameTestHelper helper,
        LivingEntity boss,
        int level,
        double expectedFactor,
        String scenario
    ) {
        assertLevel(helper, boss, level, scenario);
        LevelledMobData data = ((LevelledMobHolder) boss).lampas$getLevelData();
        helper.assertValueEqual(data.ruleSet(), TIER_RULE, "the rule id of " + scenario);
        helper.assertTrue(Math.abs(data.groupFactor() - expectedFactor) < 1e-9,
            "the group factor of " + scenario + ": expected " + expectedFactor + ", got " + data.groupFactor());

        double base = boss.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
        double expected = (base + BOSS_HEALTH_PER_LEVEL * (level - 1)) * expectedFactor;
        helper.assertTrue(Math.abs(boss.getMaxHealth() - expected) < 1e-3,
            "the max health of " + scenario + ": expected " + expected + ", got " + boss.getMaxHealth());
        helper.assertTrue(Math.abs(boss.getHealth() - boss.getMaxHealth()) < 1e-3,
            scenario + " is not at full health: " + boss.getHealth() + " / " + boss.getMaxHealth());
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
        LivingEntity pig = createAt(helper, EntityTypes.PIG, Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        return withVictimHealth(pig);
    }

    private static LivingEntity victimPlayer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.snapTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        return withVictimHealth(player);
    }

    private static LivingEntity withVictimHealth(LivingEntity victim) {
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(VICTIM_HEALTH);
        victim.setHealth((float) VICTIM_HEALTH);
        return victim;
    }

    private static WitherBoss reload(GameTestHelper helper, WitherBoss wither) {
        try (var reporter = new ProblemReporter.ScopedCollector(wither.problemPath(), LevelledMobsModule.LOGGER)) {
            var output = TagValueOutput.createWithContext(reporter, helper.getLevel().registryAccess());
            wither.saveWithoutId(output);
            WitherBoss loaded = EntityTypes.WITHER.create(helper.getLevel(), EntitySpawnReason.LOAD);
            helper.assertTrue(loaded != null, "the reloaded Wither could not be created");
            loaded.load(TagValueInput.create(reporter, helper.getLevel().registryAccess(), output.buildResult()));
            return loaded;
        }
    }

    private static void assertNoPlayersNear(GameTestHelper helper, LivingEntity boss) {
        helper.assertValueEqual(BossScaling.countNearbyPlayers(boss), 0,
            "players left near the remote boss point by another test");
    }

    private static <E extends Mob> E addMob(GameTestHelper helper, EntityType<E> type, BlockPos relativePos, List<Entity> added) {
        E mob = createAt(helper, type, Vec3.atCenterOf(helper.absolutePos(relativePos)));
        mob.setBaby(false);
        mob.addTag(ApothMobEvents.NO_RANDOM_PROCESSING);
        helper.assertTrue(helper.getLevel().addFreshEntity(mob), "the test " + type.getDescription().getString()
            + " could not be added to the level");
        added.add(mob);
        return mob;
    }

    private static <E extends Entity> E createAt(GameTestHelper helper, EntityType<E> type, Vec3 position) {
        E entity = type.create(helper.getLevel(), EntitySpawnReason.NATURAL);
        helper.assertTrue(entity != null, "the test " + type.getDescription().getString() + " could not be created");
        entity.snapTo(position);
        return entity;
    }

    /** A point far enough from the GameTest grid that no other test's mock players are within 128 blocks. */
    private static Vec3 remotePoint(GameTestHelper helper) {
        return Vec3.atCenterOf(helper.absolutePos(new BlockPos(0, 2, 0))).add(512, 0, 512);
    }

    private static void assertLevel(GameTestHelper helper, LivingEntity mob, int expectedLevel, String scenario) {
        helper.assertTrue(mob instanceof LevelledMobHolder, "the LevelledMobs data-holder mixin was not applied to " + scenario);
        LevelledMobData data = ((LevelledMobHolder) mob).lampas$getLevelData();
        helper.assertTrue(data != null && data.levelled(), scenario + " was not levelled");
        helper.assertValueEqual(data.level(), expectedLevel, "the LevelledMobs level of " + scenario);
    }

    private static void assertUnlevelled(GameTestHelper helper, LivingEntity mob, String scenario) {
        LevelledMobData data = ((LevelledMobHolder) mob).lampas$getLevelData();
        helper.assertTrue(data == null || !data.levelled(), scenario + " was levelled");
    }

    private static MobLevelingService levelingService() {
        return LevelledMobsModule.getMobLevelingService();
    }

    /** A rule with no conditions that fixes the level, as the pack's tier rule sets the band. */
    private static LevelRule tierRule(int level) {
        return RuleParser.parseRule(TIER_RULE, Map.of(
            "priority", 100,
            "min_level", level,
            "max_level", level
        ));
    }

    /** The tier rule and the pack's boss_scaling rule: priority 50, the four vanilla bosses, 290 max health per level. */
    private static List<LevelRule> bossRules(int level) {
        return List.of(
            tierRule(level),
            RuleParser.parseRule("gametest-boss-scaling", Map.of(
                "priority", 50,
                "conditions", Map.of("entities", Map.of("include", BOSS_IDS)),
                "attributes", Map.of("max_health", BOSS_HEALTH_PER_LEVEL)
            ))
        );
    }

    private static void withRules(GameTestHelper helper, List<LevelRule> rules, boolean allowBossLeveling, Runnable body) {
        RuleManager ruleManager = LevelledMobsModule.getRuleManager();
        BossClassifier classifier = LevelledMobsModule.getBossClassifier();
        helper.assertTrue(ruleManager != null && classifier != null && levelingService() != null
                && LevelledMobsModule.getProcessingQueue() != null && LevelledMobsModule.getConfigLoader() != null,
            "LevelledMobs did not initialize its live services");
        List<LevelRule> previousRules = List.copyOf(ruleManager.getRules());
        boolean previousAllow = classifier.isBossLevelingAllowed();
        ruleManager.setRules(rules);
        classifier.setAllowBossLeveling(allowBossLeveling);
        try {
            body.run();
        } finally {
            classifier.setAllowBossLeveling(previousAllow);
            ruleManager.setRules(previousRules);
        }
    }
}
