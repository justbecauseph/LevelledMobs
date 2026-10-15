package lampas.levelledmobs.rules;

import com.google.gson.Gson;
import lampas.levelledmobs.context.MobContext;
import lampas.levelledmobs.data.SpawnReason;
import lampas.levelledmobs.rules.conditions.AltitudeCondition;
import lampas.levelledmobs.rules.conditions.BiomeCondition;
import lampas.levelledmobs.rules.conditions.DimensionCondition;
import lampas.levelledmobs.rules.conditions.SpawnReasonCondition;
import lampas.levelledmobs.rules.strategy.StrategyRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bounds and correctness of the rule-resolution cache in {@link CompiledRules}. Expected matches come from the rule
 * definitions, or from testing every rule against the context directly, never from the cache.
 *
 * <p>{@code pack-rules.json} is a copy of lampas-pipeline's {@code pack/server/config/lampas/levelledmobs/rules.json}
 * taken on 2026-10-16.
 */
public class RuleCacheTest {
    private static final ResourceKey<Level> OVERWORLD = dimension("minecraft", "overworld");
    private static final ResourceKey<Level> THE_NETHER = dimension("minecraft", "the_nether");
    private static final ResourceKey<Level> THE_END = dimension("minecraft", "the_end");
    private static final ResourceKey<Level> MODDED_DIMENSION = dimension("lampas", "mining");

    private static final Identifier ZOMBIE = Identifier.fromNamespaceAndPath("minecraft", "zombie");
    private static final Identifier SKELETON = Identifier.fromNamespaceAndPath("minecraft", "skeleton");
    private static final Identifier CREEPER = Identifier.fromNamespaceAndPath("minecraft", "creeper");
    private static final Identifier WITHER = Identifier.fromNamespaceAndPath("minecraft", "wither");
    private static final Identifier WARDEN = Identifier.fromNamespaceAndPath("minecraft", "warden");
    private static final Identifier END_SLIME = Identifier.fromNamespaceAndPath("betterend", "end_slime");

    private static final HolderOwner<Biome> BIOME_OWNER = new HolderOwner<>() {};
    private static final ResourceKey<Biome> DESERT = biomeKey("desert");

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private record TestMobContext(
        Identifier entityId,
        ResourceKey<Level> dimensionKey,
        Holder<Biome> biome,
        BlockPos blockPos,
        SpawnReason spawnReason
    ) implements MobContext {
        @Override public LivingEntity entity() { return null; }
        @Override public UUID uuid() { return UUID.randomUUID(); }
        // Null keeps EntityCondition off entity-type tags, which need a bound registry.
        @Override public EntityType<?> entityType() { return null; }
        @Override public ServerLevel world() { return null; }
        @Override public Vec3 position() { return blockPos != null ? Vec3.atCenterOf(blockPos) : null; }
        @Override public boolean isBaby() { return false; }
        @Override public boolean isBoss() { return false; }
        @Override public boolean isTamed() { return false; }
        @Override public boolean hasCustomName() { return false; }
        @Override public Optional<ServerPlayer> nearestPlayer() { return Optional.empty(); }
    }

    @Test
    public void packRulesShareOneEntryPerEntityAndDimensionAcrossBiomeYAndReason() throws IOException {
        CompiledRules compiled = new CompiledRules(loadPackRules());

        List<Identifier> entities = List.of(ZOMBIE, SKELETON, CREEPER, WITHER, WARDEN, END_SLIME);
        List<ResourceKey<Level>> dimensions = List.of(OVERWORLD, THE_NETHER, THE_END, MODDED_DIMENSION);
        List<SpawnReason> reasons = List.of(SpawnReason.NATURAL, SpawnReason.SPAWNER, SpawnReason.PATROL);
        List<Holder<Biome>> biomes = new ArrayList<>();
        for (String path : List.of("plains", "desert", "deep_dark", "nether_wastes", "basalt_deltas", "the_end", "end_highlands")) {
            biomes.add(biome(path));
        }
        biomes.add(null);

        int combinations = entities.size() * dimensions.size() * reasons.size();
        Set<RuleResult> distinctResults = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < 10_000; i++) {
            int combination = i % combinations;
            Identifier entity = entities.get(combination % entities.size());
            ResourceKey<Level> dimension = dimensions.get((combination / entities.size()) % dimensions.size());
            SpawnReason reason = reasons.get(combination / (entities.size() * dimensions.size()));
            Holder<Biome> biome = biomes.get((i / combinations) % biomes.size());
            BlockPos pos = i % 101 == 0 ? null : new BlockPos(i, -64 + Math.floorMod(i * 37, 384), -i);
            MobContext context = new TestMobContext(entity, dimension, biome, pos, reason);

            RuleResult result = compiled.evaluate(context);

            assertTrue(result.matched());
            assertEquals(expectedPackRuleIds(entity, dimension), result.effectiveRule().matchedRuleIds(), context.toString());
            assertEquals(Map.of("multiplier", expectedPackXpMultiplier(dimension)), result.effectiveRule().xpSettings(), context.toString());
            assertEquals(directlyMatchedIds(compiled, context), result.effectiveRule().matchedRuleIds(), context.toString());
            distinctResults.add(result);
        }

        // The pack's rules read only entity and dimension, so spawn reason drops out of the key too.
        assertEquals(entities.size() * dimensions.size(), compiled.cacheSize());
        // One shared result per matched-rule list: 4 dimension outcomes x boss or not.
        assertEquals(8, distinctResults.size());
        assertSame(
            compiled.evaluate(new TestMobContext(ZOMBIE, OVERWORLD, null, BlockPos.ZERO, SpawnReason.NATURAL)),
            compiled.evaluate(new TestMobContext(SKELETON, OVERWORLD, null, BlockPos.ZERO, SpawnReason.NATURAL)));
    }

    @Test
    public void altitudeConditionKeysOnBandsWithInclusiveBounds() {
        CompiledRules compiled = new CompiledRules(List.of(
            LevelRule.builder("surface").priority(10).condition(AltitudeCondition.between(0, 63)).build(),
            LevelRule.builder("fallback").build()));

        for (int y = -64; y <= 319; y++) {
            compiled.evaluate(atY(y));
        }
        assertEquals(3, compiled.cacheSize());

        assertEquals(List.of("fallback"), compiled.evaluate(atY(-1)).effectiveRule().matchedRuleIds());
        assertEquals(List.of("surface", "fallback"), compiled.evaluate(atY(0)).effectiveRule().matchedRuleIds());
        assertEquals(List.of("surface", "fallback"), compiled.evaluate(atY(63)).effectiveRule().matchedRuleIds());
        assertEquals(List.of("fallback"), compiled.evaluate(atY(64)).effectiveRule().matchedRuleIds());
        assertSame(compiled.evaluate(atY(-1)), compiled.evaluate(atY(64)));

        // AltitudeCondition matches a context without a position.
        MobContext noPosition = new TestMobContext(ZOMBIE, OVERWORLD, null, null, SpawnReason.NATURAL);
        assertEquals(List.of("surface", "fallback"), compiled.evaluate(noPosition).effectiveRule().matchedRuleIds());
        assertEquals(4, compiled.cacheSize());
    }

    @Test
    public void overlappingAltitudeConditionsMatchDirectEvaluationAtEveryY() {
        CompiledRules compiled = new CompiledRules(List.of(
            LevelRule.builder("band").priority(30).condition(AltitudeCondition.between(0, 63)).build(),
            LevelRule.builder("high").priority(20).condition(AltitudeCondition.above(32)).build(),
            LevelRule.builder("deep").priority(10).condition(AltitudeCondition.below(-17)).build(),
            LevelRule.builder("fallback").build()));

        for (int y = -64; y <= 319; y++) {
            MobContext context = atY(y);
            assertEquals(directlyMatchedIds(compiled, context), compiled.evaluate(context).effectiveRule().matchedRuleIds(), "y=" + y);
        }
        // Bands start at -16, 0, 32 and 64: y < -16, [-16, 0), [0, 32), [32, 64), y >= 64.
        assertEquals(5, compiled.cacheSize());
    }

    @Test
    public void biomeConditionStillSeparatesBiomes() {
        CompiledRules compiled = new CompiledRules(List.of(
            LevelRule.builder("desert_only").priority(10)
                .condition(new BiomeCondition(Set.of(DESERT), Set.of(), Set.of(), Set.of())).build(),
            LevelRule.builder("fallback").build()));

        List<Holder<Biome>> biomes = new ArrayList<>(List.of(biome("plains"), biome("desert"), biome("badlands")));
        biomes.add(null);
        for (Holder<Biome> biome : biomes) {
            for (int y = -64; y <= 319; y += 16) {
                MobContext context = new TestMobContext(ZOMBIE, OVERWORLD, biome, new BlockPos(0, y, 0), SpawnReason.NATURAL);
                List<String> expected = biome != null && biome.is(DESERT)
                    ? List.of("desert_only", "fallback")
                    : List.of("fallback");
                assertEquals(expected, compiled.evaluate(context).effectiveRule().matchedRuleIds(), context.toString());
            }
        }
        assertEquals(biomes.size(), compiled.cacheSize());
    }

    @Test
    public void spawnReasonConditionKeepsReasonInKey() {
        CompiledRules compiled = new CompiledRules(List.of(
            LevelRule.builder("spawner_only").priority(10)
                .condition(new SpawnReasonCondition(Set.of(SpawnReason.SPAWNER), Set.of())).build(),
            LevelRule.builder("fallback").build()));

        for (int y = -64; y <= 319; y += 16) {
            MobContext natural = new TestMobContext(ZOMBIE, OVERWORLD, biome("plains"), new BlockPos(0, y, 0), SpawnReason.NATURAL);
            MobContext spawner = new TestMobContext(ZOMBIE, OVERWORLD, biome("desert"), new BlockPos(0, y, 0), SpawnReason.SPAWNER);
            assertEquals(List.of("fallback"), compiled.evaluate(natural).effectiveRule().matchedRuleIds());
            assertEquals(List.of("spawner_only", "fallback"), compiled.evaluate(spawner).effectiveRule().matchedRuleIds());
        }
        assertEquals(2, compiled.cacheSize());
    }

    @Test
    public void unknownConditionOrPredicateSkipsCache() {
        // Reads X, which no key holds.
        CompiledRules lambdaCondition = new CompiledRules(List.of(
            LevelRule.builder("even_x").priority(10)
                .condition(context -> context.blockPos() != null && context.blockPos().getX() % 2 == 0).build(),
            LevelRule.builder("fallback").build()));
        CompiledRules predicateSubclass = new CompiledRules(List.of(
            LevelRule.builder("even_x").priority(10)
                .predicate(new RulePredicate(List.of()) {
                    @Override
                    public boolean test(MobContext context) {
                        return context.blockPos() != null && context.blockPos().getX() % 2 == 0;
                    }
                }).build(),
            LevelRule.builder("fallback").build()));

        for (CompiledRules compiled : List.of(lambdaCondition, predicateSubclass)) {
            for (int x = 0; x < 20; x++) {
                MobContext context = new TestMobContext(ZOMBIE, OVERWORLD, biome("plains"), new BlockPos(x, 64, 0), SpawnReason.NATURAL);
                List<String> expected = x % 2 == 0 ? List.of("even_x", "fallback") : List.of("fallback");
                assertEquals(expected, compiled.evaluate(context).effectiveRule().matchedRuleIds(), "x=" + x);
            }
            assertEquals(0, compiled.cacheSize());
        }
    }

    @Test
    public void distanceStrategiesReadPositionFromContextNotCachedRule() {
        RuleManager manager = new RuleManager();
        manager.setRules(List.of(
            LevelRule.builder("nether_depth").priority(10)
                .condition(new DimensionCondition(Set.of(THE_NETHER), Set.of()))
                .levelRange(IntRange.of(1, 50))
                .strategy("Y_DISTANCE", null)
                .build(),
            LevelRule.builder("distance")
                .levelRange(IntRange.of(1, 50))
                .strategy("SPAWN_DISTANCE", null)
                .build()));
        StrategyRegistry strategies = new StrategyRegistry();

        // Y_DISTANCE defaults: start y 64, end y -64, scaling downward across the level range.
        Map<Integer, Integer> levelByY = Map.of(64, 1, 0, 25, -64, 50);
        EffectiveRule netherRule = manager.resolve(netherAt(0)).effectiveRule();
        for (Map.Entry<Integer, Integer> expected : levelByY.entrySet()) {
            MobContext context = netherAt(expected.getKey());
            EffectiveRule rule = manager.resolve(context).effectiveRule();
            assertSame(netherRule, rule);
            assertEquals(expected.getValue(),
                strategies.resolveStrategy(rule.strategyName(), context, rule).calculateLevel(context, rule), "y=" + expected.getKey());
        }

        // SPAWN_DISTANCE defaults: origin 0,0, one level per 100 blocks above the range minimum.
        Map<Integer, Integer> levelByZ = Map.of(0, 1, 1000, 11, 2500, 26);
        EffectiveRule overworldRule = manager.resolve(overworldAt(0)).effectiveRule();
        for (Map.Entry<Integer, Integer> expected : levelByZ.entrySet()) {
            MobContext context = overworldAt(expected.getKey());
            EffectiveRule rule = manager.resolve(context).effectiveRule();
            assertSame(overworldRule, rule);
            assertEquals(expected.getValue(),
                strategies.resolveStrategy(rule.strategyName(), context, rule).calculateLevel(context, rule), "z=" + expected.getKey());
        }

        assertEquals(2, manager.getCompiledRules().cacheSize());
    }

    // Pack rules by priority: apotheosis_scaled_mobs (100, no conditions), boss_scaling (50, entity ids),
    // the_end_scaling (20), nether_scaling (10), default_overworld (1), default_fallback (0, no conditions).
    private static List<String> expectedPackRuleIds(Identifier entity, ResourceKey<Level> dimension) {
        List<String> ids = new ArrayList<>();
        ids.add("apotheosis_scaled_mobs");
        if (entity.equals(WITHER) || entity.equals(WARDEN)) {
            ids.add("boss_scaling");
        }
        if (dimension == THE_END) {
            ids.add("the_end_scaling");
        } else if (dimension == THE_NETHER) {
            ids.add("nether_scaling");
        } else if (dimension == OVERWORLD) {
            ids.add("default_overworld");
        }
        ids.add("default_fallback");
        return ids;
    }

    // Only the dimension rules and default_fallback set xp.
    private static double expectedPackXpMultiplier(ResourceKey<Level> dimension) {
        if (dimension == THE_END) {
            return 0.1;
        }
        if (dimension == THE_NETHER) {
            return 0.08;
        }
        return 0.05;
    }

    private static List<String> directlyMatchedIds(CompiledRules compiled, MobContext context) {
        List<String> ids = compiled.rules().stream().filter(rule -> rule.matches(context)).map(LevelRule::id).toList();
        return ids.isEmpty() ? EffectiveRule.DEFAULT.matchedRuleIds() : ids;
    }

    @SuppressWarnings("unchecked")
    private static List<LevelRule> loadPackRules() throws IOException {
        try (Reader reader = new InputStreamReader(
            Objects.requireNonNull(RuleCacheTest.class.getResourceAsStream("pack-rules.json")), StandardCharsets.UTF_8)) {
            // Parsed the way ConfigLoader.loadRules reads rules.json.
            Map<String, Object> root = new Gson().fromJson(reader, Map.class);
            List<LevelRule> rules = new ArrayList<>();
            for (Map.Entry<String, Object> entry : root.entrySet()) {
                rules.add(RuleParser.parseRule(entry.getKey(), (Map<String, Object>) entry.getValue()));
            }
            assertEquals(6, rules.size());
            return rules;
        }
    }

    private static MobContext atY(int y) {
        return new TestMobContext(ZOMBIE, OVERWORLD, biome("plains"), new BlockPos(y * 7, y, -y), SpawnReason.NATURAL);
    }

    private static MobContext netherAt(int y) {
        return new TestMobContext(ZOMBIE, THE_NETHER, biome("nether_wastes"), new BlockPos(0, y, 0), SpawnReason.NATURAL);
    }

    private static MobContext overworldAt(int z) {
        return new TestMobContext(ZOMBIE, OVERWORLD, biome("plains"), new BlockPos(0, 64, z), SpawnReason.NATURAL);
    }

    private static ResourceKey<Level> dimension(String namespace, String path) {
        return ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(namespace, path));
    }

    private static ResourceKey<Biome> biomeKey(String path) {
        return ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("minecraft", path));
    }

    private static Holder<Biome> biome(String path) {
        return Holder.Reference.createStandAlone(BIOME_OWNER, biomeKey(path));
    }
}
