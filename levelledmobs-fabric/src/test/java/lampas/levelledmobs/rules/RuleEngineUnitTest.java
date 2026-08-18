package lampas.levelledmobs.rules;

import lampas.levelledmobs.context.MobContext;
import lampas.levelledmobs.data.SpawnReason;
import lampas.levelledmobs.rules.conditions.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class RuleEngineUnitTest {
    private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("minecraft", "overworld"));
    private static final ResourceKey<Level> THE_NETHER = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("minecraft", "the_nether"));
    private static final ResourceKey<Level> THE_END = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("minecraft", "the_end"));

    private static class TestMobContext implements MobContext {
        private final Identifier entityId;
        private final ResourceKey<Level> dimensionKey;
        private final BlockPos blockPos;
        private final SpawnReason spawnReason;

        public TestMobContext(Identifier entityId, ResourceKey<Level> dimensionKey, BlockPos blockPos, SpawnReason spawnReason) {
            this.entityId = entityId;
            this.dimensionKey = dimensionKey;
            this.blockPos = blockPos;
            this.spawnReason = spawnReason;
        }

        @Override public LivingEntity entity() { return null; }
        @Override public UUID uuid() { return UUID.randomUUID(); }
        @Override public EntityType<?> entityType() { return null; }
        @Override public Identifier entityId() { return entityId; }
        @Override public ServerLevel world() { return null; }
        @Override public ResourceKey<Level> dimensionKey() { return dimensionKey; }
        @Override public BlockPos blockPos() { return blockPos; }
        @Override public Vec3 position() { return Vec3.ZERO; }
        @Override public Holder<Biome> biome() { return null; }
        @Override public boolean isBaby() { return false; }
        @Override public boolean isBoss() { return false; }
        @Override public boolean isTamed() { return false; }
        @Override public boolean hasCustomName() { return false; }
        @Override public Optional<ServerPlayer> nearestPlayer() { return Optional.empty(); }
        @Override public SpawnReason spawnReason() { return spawnReason; }
    }

    @Test
    public void testEntityConditionMatchingAndModdedSupport() {
        Identifier zombieId = Identifier.fromNamespaceAndPath("minecraft", "zombie");
        Identifier skeletonId = Identifier.fromNamespaceAndPath("minecraft", "skeleton");
        Identifier moddedId = Identifier.fromNamespaceAndPath("betterend", "end_slime");

        EntityCondition condition = new EntityCondition(
            Set.of(zombieId, moddedId),
            Set.of(skeletonId),
            Collections.emptySet(),
            Collections.emptySet()
        );

        MobContext zombieCtx = new TestMobContext(zombieId, OVERWORLD, new BlockPos(0, 64, 0), SpawnReason.NATURAL);
        MobContext skeletonCtx = new TestMobContext(skeletonId, OVERWORLD, new BlockPos(0, 64, 0), SpawnReason.NATURAL);
        MobContext moddedCtx = new TestMobContext(moddedId, THE_END, new BlockPos(0, 64, 0), SpawnReason.NATURAL);
        MobContext creeperCtx = new TestMobContext(Identifier.fromNamespaceAndPath("minecraft", "creeper"), OVERWORLD, new BlockPos(0, 64, 0), SpawnReason.NATURAL);

        assertTrue(condition.matches(zombieCtx));
        assertFalse(condition.matches(skeletonCtx));
        assertTrue(condition.matches(moddedCtx)); // Modded mob supported
        assertFalse(condition.matches(creeperCtx));
    }

    @Test
    public void testDimensionConditionMatching() {
        DimensionCondition condition = new DimensionCondition(
            Set.of(THE_NETHER, THE_END),
            Collections.emptySet()
        );

        MobContext overworldCtx = new TestMobContext(Identifier.fromNamespaceAndPath("minecraft", "zombie"), OVERWORLD, new BlockPos(0, 64, 0), SpawnReason.NATURAL);
        MobContext netherCtx = new TestMobContext(Identifier.fromNamespaceAndPath("minecraft", "zombie"), THE_NETHER, new BlockPos(0, 64, 0), SpawnReason.NATURAL);

        assertFalse(condition.matches(overworldCtx));
        assertTrue(condition.matches(netherCtx));
    }

    @Test
    public void testAltitudeConditionMatching() {
        AltitudeCondition underground = AltitudeCondition.below(0);
        AltitudeCondition sky = AltitudeCondition.above(128);
        AltitudeCondition surface = AltitudeCondition.between(60, 100);

        MobContext deepMob = new TestMobContext(Identifier.fromNamespaceAndPath("minecraft", "zombie"), OVERWORLD, new BlockPos(0, -32, 0), SpawnReason.NATURAL);
        MobContext surfaceMob = new TestMobContext(Identifier.fromNamespaceAndPath("minecraft", "zombie"), OVERWORLD, new BlockPos(0, 70, 0), SpawnReason.NATURAL);
        MobContext skyMob = new TestMobContext(Identifier.fromNamespaceAndPath("minecraft", "zombie"), OVERWORLD, new BlockPos(0, 200, 0), SpawnReason.NATURAL);

        assertTrue(underground.matches(deepMob));
        assertFalse(underground.matches(surfaceMob));

        assertTrue(surface.matches(surfaceMob));
        assertFalse(surface.matches(deepMob));

        assertTrue(sky.matches(skyMob));
        assertFalse(sky.matches(surfaceMob));
    }

    @Test
    public void testSpawnReasonConditionMatching() {
        SpawnReasonCondition naturalOnly = new SpawnReasonCondition(
            Set.of(SpawnReason.NATURAL, SpawnReason.PATROL),
            Set.of(SpawnReason.SPAWNER)
        );

        MobContext natural = new TestMobContext(Identifier.fromNamespaceAndPath("minecraft", "zombie"), OVERWORLD, new BlockPos(0, 64, 0), SpawnReason.NATURAL);
        MobContext spawner = new TestMobContext(Identifier.fromNamespaceAndPath("minecraft", "zombie"), OVERWORLD, new BlockPos(0, 64, 0), SpawnReason.SPAWNER);
        MobContext breeding = new TestMobContext(Identifier.fromNamespaceAndPath("minecraft", "zombie"), OVERWORLD, new BlockPos(0, 64, 0), SpawnReason.BREEDING);

        assertTrue(naturalOnly.matches(natural));
        assertFalse(naturalOnly.matches(spawner));
        assertFalse(naturalOnly.matches(breeding));
    }

    @Test
    public void testRuleMergingAndPriorityResolution() {
        LevelRule baseRule = LevelRule.builder("base_hostiles")
            .priority(10)
            .levelRange(IntRange.of(1, 10))
            .strategy("RANDOM", Map.of("variance", 1.0))
            .attributeSettings(Map.of("health_mod", 2.0, "speed_mod", 1.1))
            .build();

        LevelRule deepDarkRule = LevelRule.builder("deep_dark_override")
            .priority(50)
            .levelRange(IntRange.of(20, 50))
            .strategy("SPAWN_DISTANCE", Map.of("distance_per_level", 100))
            .attributeSettings(Map.of("health_mod", 5.0, "damage_mod", 3.0))
            .build();

        List<LevelRule> matched = List.of(deepDarkRule, baseRule);
        EffectiveRule effective = EffectiveRule.merge(matched);

        assertEquals("deep_dark_override", effective.primaryRuleId());
        assertEquals(2, effective.matchedRuleIds().size());
        assertEquals(20, effective.levelRange().min());
        assertEquals(50, effective.levelRange().max());
        assertEquals("SPAWN_DISTANCE", effective.strategyName());

        // Attributes should merge, with deep_dark_override taking precedence for overlapping keys
        assertEquals(5.0, effective.attributeSettings().get("health_mod"));
        assertEquals(3.0, effective.attributeSettings().get("damage_mod"));
        assertEquals(1.1, effective.attributeSettings().get("speed_mod")); // inherited from base_hostiles
    }

    @Test
    public void testRuleManagerAtomicSwappingAndResolution() {
        RuleManager manager = new RuleManager();

        Identifier zombieId = Identifier.fromNamespaceAndPath("minecraft", "zombie");
        MobContext context = new TestMobContext(zombieId, OVERWORLD, new BlockPos(0, 64, 0), SpawnReason.NATURAL);

        // Initial default rule
        RuleResult initialResult = manager.resolve(context);
        assertTrue(initialResult.matched());
        assertEquals("default", initialResult.effectiveRule().primaryRuleId());

        // Swap rules atomically
        LevelRule customRule = LevelRule.builder("custom_zombies")
            .priority(100)
            .levelRange(IntRange.of(5, 15))
            .predicate(RulePredicate.builder().add(new EntityCondition(Set.of(zombieId), Collections.emptySet(), Collections.emptySet(), Collections.emptySet())).build())
            .build();

        manager.setRules(List.of(customRule));

        RuleResult swappedResult = manager.resolve(context);
        assertTrue(swappedResult.matched());
        assertEquals("custom_zombies", swappedResult.effectiveRule().primaryRuleId());
        assertEquals(5, swappedResult.effectiveRule().levelRange().min());
        assertEquals(15, swappedResult.effectiveRule().levelRange().max());
    }

    @Test
    public void testRuleParserFromConfigMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("priority", 25);
        map.put("enabled", true);
        map.put("min-level", 10);
        map.put("max-level", 40);
        map.put("strategy", Map.of("name", "SPAWN_DISTANCE", "origin", "0,64,0"));

        Map<String, Object> conditions = new HashMap<>();
        conditions.put("entities", Map.of("include", List.of("minecraft:zombie", "betterend:end_slime"), "exclude", List.of("minecraft:wither")));
        conditions.put("altitude", Map.of("min", -64, "max", 320));
        conditions.put("spawn-reasons", List.of("NATURAL", "PATROL"));
        map.put("conditions", conditions);

        map.put("attributes", Map.of("max_health", 2.5));

        LevelRule rule = RuleParser.parseRule("parsed_test_rule", map);

        assertNotNull(rule);
        assertEquals("parsed_test_rule", rule.id());
        assertEquals(25, rule.priority());
        assertTrue(rule.isEnabled());
        assertEquals(10, rule.levelRange().min());
        assertEquals(40, rule.levelRange().max());
        assertEquals("SPAWN_DISTANCE", rule.strategyName());

        MobContext matching = new TestMobContext(Identifier.fromNamespaceAndPath("betterend", "end_slime"), OVERWORLD, new BlockPos(0, 10, 0), SpawnReason.NATURAL);
        assertTrue(rule.matches(matching));

        MobContext nonMatching = new TestMobContext(Identifier.fromNamespaceAndPath("minecraft", "wither"), OVERWORLD, new BlockPos(0, 10, 0), SpawnReason.NATURAL);
        assertFalse(rule.matches(nonMatching));
    }
}
