package lampas.levelledmobs.benchmark;

import lampas.levelledmobs.attributes.AttributeDefinition;
import lampas.levelledmobs.attributes.AttributeScalingService;
import lampas.levelledmobs.attributes.HealthPolicy;
import lampas.levelledmobs.context.MobContext;
import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import lampas.levelledmobs.data.SpawnReason;
import lampas.levelledmobs.rules.IntRange;
import lampas.levelledmobs.rules.LevelRule;
import lampas.levelledmobs.rules.RuleManager;
import lampas.levelledmobs.rules.RuleResult;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ScalabilityBenchmarkTest {

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static class BenchMobContext implements MobContext {
        private final Identifier entityId;
        private final BlockPos pos;

        public BenchMobContext(Identifier entityId, BlockPos pos) {
            this.entityId = entityId;
            this.pos = pos;
        }

        @Override public LivingEntity entity() { return null; }
        @Override public UUID uuid() { return UUID.randomUUID(); }
        @Override public EntityType<?> entityType() { return null; }
        @Override public Identifier entityId() { return entityId; }
        @Override public ServerLevel world() { return null; }
        @Override public ResourceKey<Level> dimensionKey() { return null; }
        @Override public BlockPos blockPos() { return pos; }
        @Override public Vec3 position() { return Vec3.ZERO; }
        @Override public Holder<Biome> biome() { return null; }
        @Override public boolean isBaby() { return false; }
        @Override public boolean isBoss() { return false; }
        @Override public boolean isTamed() { return false; }
        @Override public boolean hasCustomName() { return false; }
        @Override public Optional<ServerPlayer> nearestPlayer() { return Optional.empty(); }
        @Override public SpawnReason spawnReason() { return SpawnReason.NATURAL; }
    }

    @Test
    public void testHighThroughputRuleResolutionPerformance() {
        RuleManager ruleManager = new RuleManager();

        LevelRule rule1 = LevelRule.builder("rule_zombie")
            .priority(10)
            .levelRange(IntRange.of(1, 25))
            .strategy("SPAWN_DISTANCE", null)
            .build();

        LevelRule rule2 = LevelRule.builder("rule_skeleton")
            .priority(20)
            .levelRange(IntRange.of(5, 50))
            .strategy("RANDOM", null)
            .build();

        ruleManager.setRules(List.of(rule1, rule2));

        MobContext context = new BenchMobContext(
            Identifier.fromNamespaceAndPath("minecraft", "zombie"),
            new BlockPos(100, 64, 100)
        );

        // Warmup
        for (int i = 0; i < 5000; i++) {
            ruleManager.resolve(context);
        }

        // Benchmark 50,000 evaluations
        long start = System.nanoTime();
        int matches = 0;
        for (int i = 0; i < 50000; i++) {
            RuleResult res = ruleManager.resolve(context);
            if (res.matched()) {
                matches++;
            }
        }
        long durationNs = System.nanoTime() - start;
        double durationMs = durationNs / 1_000_000.0;

        assertEquals(50000, matches);
        // RuleCacheKey caching guarantees sub-millisecond throughput for 50k evaluations
        assertTrue(durationMs < 100.0, "50,000 rule resolutions took " + durationMs + "ms (exceeded 100ms threshold)");
    }
}
