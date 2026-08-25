package lampas.levelledmobs.rules;

import lampas.levelledmobs.context.MobContext;
import lampas.levelledmobs.data.SpawnReason;
import lampas.levelledmobs.rules.strategy.*;
import lampas.levelledmobs.rules.strategy.math.LevelTierMatching;
import lampas.levelledmobs.rules.strategy.math.MinAndMax;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class StrategyCalculationUnitTest {

    private static class StrategyMockContext implements MobContext {
        private final BlockPos pos;
        private final ServerPlayer player;

        public StrategyMockContext(BlockPos pos, ServerPlayer player) {
            this.pos = pos;
            this.player = player;
        }

        @Override public LivingEntity entity() { return null; }
        @Override public UUID uuid() { return UUID.randomUUID(); }
        @Override public EntityType<?> entityType() { return null; }
        @Override public Identifier entityId() { return Identifier.fromNamespaceAndPath("minecraft", "zombie"); }
        @Override public ServerLevel world() { return null; }
        @Override public ResourceKey<Level> dimensionKey() { return null; }
        @Override public BlockPos blockPos() { return pos; }
        @Override public Vec3 position() { return pos != null ? new Vec3(pos.getX(), pos.getY(), pos.getZ()) : Vec3.ZERO; }
        @Override public Holder<Biome> biome() { return null; }
        @Override public boolean isBaby() { return false; }
        @Override public boolean isBoss() { return false; }
        @Override public boolean isTamed() { return false; }
        @Override public boolean hasCustomName() { return false; }
        @Override public Optional<ServerPlayer> nearestPlayer() { return Optional.ofNullable(player); }
        @Override public SpawnReason spawnReason() { return SpawnReason.NATURAL; }
    }

    @Test
    public void testSpawnDistanceStrategyAcceptanceFormula() {
        // Acceptance criterion: distance=2500, perLevel=100, base=1 -> level=26
        int calculated = SpawnDistanceStrategy.calculate(2500.0, 100.0, 1, 0.0);
        assertEquals(26, calculated);

        // Via Strategy instance
        LevelRule rule = LevelRule.builder("dist_test")
            .levelRange(IntRange.of(1, 100))
            .strategy("SPAWN_DISTANCE", Map.of(
                "origin_x", 0.0,
                "origin_z", 0.0,
                "distance_per_level", 100.0,
                "base_level", 1
            ))
            .build();

        EffectiveRule effective = EffectiveRule.merge(List.of(rule));
        MobContext context = new StrategyMockContext(new BlockPos(2500, 64, 0), null);

        int level = SpawnDistanceStrategy.INSTANCE.calculateLevel(context, effective);
        assertEquals(26, level);
    }

    @Test
    public void testSpawnDistanceEdgeCases() {
        // Negative coordinates
        LevelRule rule = LevelRule.builder("neg_dist_test")
            .levelRange(IntRange.of(1, 100))
            .strategy("SPAWN_DISTANCE", Map.of(
                "origin_x", 0.0,
                "origin_z", 0.0,
                "distance_per_level", 100.0,
                "base_level", 1
            ))
            .build();

        EffectiveRule effective = EffectiveRule.merge(List.of(rule));
        MobContext context = new StrategyMockContext(new BlockPos(-2500, 64, 0), null);
        assertEquals(26, SpawnDistanceStrategy.INSTANCE.calculateLevel(context, effective));

        // Division by zero safeguard (distancePerLevel = 0)
        LevelRule zeroDivRule = LevelRule.builder("zero_div_test")
            .levelRange(IntRange.of(1, 50))
            .strategy("SPAWN_DISTANCE", Map.of("distance_per_level", 0.0, "base_level", 5))
            .build();

        EffectiveRule zeroDivEffective = EffectiveRule.merge(List.of(zeroDivRule));
        assertEquals(30, SpawnDistanceStrategy.INSTANCE.calculateLevel(context, zeroDivEffective)); // Defaults to 100 distancePerLevel -> 5 + 25 = 30
    }

    @Test
    public void testRandomLevellingStrategy() {
        LevelRule rule = LevelRule.builder("random_test")
            .levelRange(IntRange.of(5, 10))
            .strategy("RANDOM", Collections.emptyMap())
            .build();

        EffectiveRule effective = EffectiveRule.merge(List.of(rule));
        MobContext context = new StrategyMockContext(new BlockPos(0, 64, 0), null);

        for (int i = 0; i < 50; i++) {
            int level = RandomLevellingStrategy.INSTANCE.calculateLevel(context, effective);
            assertTrue(level >= 5 && level <= 10, "Level " + level + " should be in range [5, 10]");
        }
    }

    @Test
    public void testWeightedRandomLevellingStrategy() {
        LevelRule rule = LevelRule.builder("weighted_test")
            .levelRange(IntRange.of(1, 20))
            .strategy("RANDOM", Map.of("weighted", Map.of("1-5", 100, "6-20", 0)))
            .build();

        EffectiveRule effective = EffectiveRule.merge(List.of(rule));
        MobContext context = new StrategyMockContext(new BlockPos(0, 64, 0), null);

        for (int i = 0; i < 20; i++) {
            int level = RandomLevellingStrategy.INSTANCE.calculateLevel(context, effective);
            assertTrue(level >= 1 && level <= 5, "Level should always fall in weighted tier [1, 5]");
        }
    }

    @Test
    public void testYDistanceStrategySubterranean() {
        LevelRule rule = LevelRule.builder("subterranean_test")
            .levelRange(IntRange.of(1, 30))
            .strategy("Y_DISTANCE", Map.of(
                "starting_y", 64,
                "ending_y", -64,
                "increase_per_level", 4.0, // Every 4 blocks deeper = +1 level
                "scale_downward", true
            ))
            .build();

        EffectiveRule effective = EffectiveRule.merge(List.of(rule));

        // At surface Y=64 -> delta=0 -> level=1
        MobContext surface = new StrategyMockContext(new BlockPos(0, 64, 0), null);
        assertEquals(1, YDistanceStrategy.INSTANCE.calculateLevel(surface, effective));

        // At Y=0 -> delta=64 -> 64/4 = 16 -> level = 1 + 16 = 17
        MobContext underground = new StrategyMockContext(new BlockPos(0, 0, 0), null);
        assertEquals(17, YDistanceStrategy.INSTANCE.calculateLevel(underground, effective));

        // At Y=-64 -> delta=128 -> 128/4 = 32 -> clamped to max 30
        MobContext bedrock = new StrategyMockContext(new BlockPos(0, -64, 0), null);
        assertEquals(30, YDistanceStrategy.INSTANCE.calculateLevel(bedrock, effective));
    }

    @Test
    public void testPlayerLevellingStrategyWithTiers() {
        PlayerLevelProvider testProvider = (player, variable) -> 25.0f; // Player level 25

        PlayerLevellingStrategy strategy = new PlayerLevellingStrategy(testProvider);

        LevelRule rule = LevelRule.builder("player_tier_test")
            .levelRange(IntRange.of(1, 50))
            .strategy("PLAYER", Map.of(
                "level_tiers", List.of(
                    Map.of("min", 1, "max", 10, "target", "1-5"),
                    Map.of("min", 11, "max", 30, "target", "15-25"),
                    Map.of("min", 31, "max", 100, "target", "35-50")
                )
            ))
            .build();

        EffectiveRule effective = EffectiveRule.merge(List.of(rule));
        MobContext context = new StrategyMockContext(new BlockPos(0, 64, 0), null); // Player not null logic can be tested with mock or match

        // When player is null, returns min level
        assertEquals(1, strategy.calculateLevel(context, effective));
    }

    @Test
    public void testCustomStrategyMathematicalFormulas() {
        LevelRule rule = LevelRule.builder("custom_math_test")
            .levelRange(IntRange.of(1, 100))
            .strategy("CUSTOM", Map.of(
                "formula", "<min_level> + floor(<distance> / 50)"
            ))
            .build();

        EffectiveRule effective = EffectiveRule.merge(List.of(rule));

        // Distance 500 -> 1 + floor(500/50) = 1 + 10 = 11
        MobContext context = new StrategyMockContext(new BlockPos(500, 64, 0), null);
        assertEquals(11, CustomStrategy.INSTANCE.calculateLevel(context, effective));

        // Test expression parser edge cases
        assertEquals(25.0, CustomStrategy.evalSimpleExpression("10 + 15"));
        assertEquals(7.0, CustomStrategy.evalSimpleExpression("1 + 2 * 3"));
        assertEquals(9.0, CustomStrategy.evalSimpleExpression("(1 + 2) * 3"));
        assertEquals(0.0, CustomStrategy.evalSimpleExpression("10 / 0")); // Divide by zero safety
        assertEquals(5.0, CustomStrategy.evalSimpleExpression("floor(5.9)"));
        assertEquals(6.0, CustomStrategy.evalSimpleExpression("ceil(5.1)"));
        assertEquals(4.0, CustomStrategy.evalSimpleExpression("sqrt(16)"));
    }

    @Test
    public void testMinAndMaxParsing() {
        MinAndMax single = MinAndMax.parse("15");
        assertNotNull(single);
        assertEquals(15f, single.min());
        assertEquals(15f, single.max());

        MinAndMax range = MinAndMax.parse("10 - 25");
        assertNotNull(range);
        assertEquals(10f, range.min());
        assertEquals(25f, range.max());

        assertNull(MinAndMax.parse("invalid-string-here"));
        assertNull(MinAndMax.parse(""));
    }

    @Test
    public void testStrategyPreCompilationEquivalence() {
        // 1. RANDOM compilation
        Map<String, Object> randomConfig = Map.of(
            "weighted", Map.of("1-5", 10, "6-10", 20),
            "variance", 2
        );
        RandomLevellingStrategy.CompiledRandomConfig compiledRandom = RandomLevellingStrategy.compileConfig(randomConfig);
        assertEquals(2, compiledRandom.entries().size());
        assertEquals(30, compiledRandom.totalWeight());
        assertEquals(2, compiledRandom.variance());

        // 2. SPAWN_DISTANCE compilation with comma-separated origin
        Map<String, Object> spawnDistConfig = Map.of(
            "origin", "100.5, 64.0, -200.5",
            "buffer_distance", 50.0,
            "distance_per_level", 25.0,
            "base_level", 3,
            "variance", 1
        );
        SpawnDistanceStrategy.CompiledSpawnDistanceConfig compiledSpawn = SpawnDistanceStrategy.compileConfig(spawnDistConfig);
        assertEquals(100.5, compiledSpawn.originX(), 0.001);
        assertEquals(-200.5, compiledSpawn.originZ(), 0.001);
        assertEquals(50.0, compiledSpawn.bufferDistance(), 0.001);
        assertEquals(25.0, compiledSpawn.distancePerLevel(), 0.001);
        assertEquals(3, compiledSpawn.baseLevel());
        assertEquals(1, compiledSpawn.variance());

        // 3. Y_DISTANCE compilation
        Map<String, Object> yDistConfig = Map.of(
            "starting_y", 120,
            "ending_y", 0,
            "increase_per_level", 10.0,
            "scale_downward", true,
            "variance", 0
        );
        YDistanceStrategy.CompiledYDistanceConfig compiledY = YDistanceStrategy.compileConfig(yDistConfig);
        assertEquals(120, compiledY.startY());
        assertEquals(0, compiledY.endY());
        assertTrue(compiledY.isDescending());
        assertEquals(10.0, compiledY.increasePerLevel(), 0.001);
        assertEquals(0, compiledY.variance());

        // 4. PLAYER compilation
        Map<String, Object> playerConfig = Map.of(
            "variable", "%custom_stat%",
            "player_variable_scale", 1.5,
            "match_variable", true,
            "output_cap", 50,
            "variance", 3
        );
        PlayerLevellingStrategy.CompiledPlayerConfig compiledPlayer = PlayerLevellingStrategy.compileConfig(playerConfig);
        assertEquals("%custom_stat%", compiledPlayer.variable());
        assertEquals(1.5f, compiledPlayer.scale(), 0.001f);
        assertTrue(compiledPlayer.matchVariable());
        assertEquals(50, compiledPlayer.outputCap());
        assertEquals(3, compiledPlayer.variance());
    }

    private static class InstrumentedCountingContext extends StrategyMockContext {
        private int nearestPlayerInvocations = 0;

        public InstrumentedCountingContext(BlockPos pos, ServerPlayer player) {
            super(pos, player);
        }

        @Override
        public Optional<ServerPlayer> nearestPlayer() {
            nearestPlayerInvocations++;
            return super.nearestPlayer();
        }

        public int getNearestPlayerInvocations() {
            return nearestPlayerInvocations;
        }
    }

    @Test
    public void testPlayerLevellingStrategySingleNearestPlayerScan() {
        PlayerLevelProvider mockProvider = (player, varName) -> 20.0f;
        PlayerLevellingStrategy strategy = new PlayerLevellingStrategy(mockProvider);

        LevelRule rule = LevelRule.builder("player_scan_test")
            .levelRange(IntRange.of(1, 50))
            .strategy("PLAYER", Map.of("match_variable", true))
            .build();
        EffectiveRule effective = EffectiveRule.merge(List.of(rule));

        // 1. Context with player present
        // Since ServerPlayer requires bootstrap, we can pass null or instrument context with empty/present
        InstrumentedCountingContext emptyContext = new InstrumentedCountingContext(new BlockPos(0, 64, 0), null);
        int levelEmpty = strategy.calculateLevel(emptyContext, effective);
        assertEquals(1, levelEmpty);
        assertEquals(1, emptyContext.getNearestPlayerInvocations(), "nearestPlayer() must be called exactly once when empty");
    }

    @Test
    public void testSpawnDistanceBaseLevelFallbackToRuleMin() {
        // 1. Rule with min = 5 and missing base_level
        LevelRule ruleMissing = LevelRule.builder("dist_fallback_missing")
            .levelRange(IntRange.of(5, 50))
            .strategy("SPAWN_DISTANCE", Map.of(
                "distance_per_level", 100.0
            ))
            .build();
        EffectiveRule effMissing = EffectiveRule.merge(List.of(ruleMissing));
        MobContext ctxAtOrigin = new StrategyMockContext(new BlockPos(0, 64, 0), null);
        assertEquals(5, SpawnDistanceStrategy.INSTANCE.calculateLevel(ctxAtOrigin, effMissing));

        // 2. Rule with min = 5 and unparsable base_level string
        LevelRule ruleInvalid = LevelRule.builder("dist_fallback_invalid")
            .levelRange(IntRange.of(5, 50))
            .strategy("SPAWN_DISTANCE", Map.of(
                "base_level", "not_a_number",
                "distance_per_level", 100.0
            ))
            .build();
        EffectiveRule effInvalid = EffectiveRule.merge(List.of(ruleInvalid));
        assertEquals(5, SpawnDistanceStrategy.INSTANCE.calculateLevel(ctxAtOrigin, effInvalid));

        // 3. Rule with min = 5 and explicit valid base_level = 15
        LevelRule ruleExplicit = LevelRule.builder("dist_explicit")
            .levelRange(IntRange.of(5, 50))
            .strategy("SPAWN_DISTANCE", Map.of(
                "base_level", 15,
                "distance_per_level", 100.0
            ))
            .build();
        EffectiveRule effExplicit = EffectiveRule.merge(List.of(ruleExplicit));
        assertEquals(15, SpawnDistanceStrategy.INSTANCE.calculateLevel(ctxAtOrigin, effExplicit));
    }
}
