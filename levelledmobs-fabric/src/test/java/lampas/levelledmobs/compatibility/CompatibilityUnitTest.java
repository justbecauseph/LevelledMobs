package lampas.levelledmobs.compatibility;

import lampas.levelledmobs.context.MobContext;
import lampas.levelledmobs.data.SpawnReason;
import lampas.levelledmobs.rules.conditions.EntityCondition;
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

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class CompatibilityUnitTest {

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static class DummyContext implements MobContext {
        private final Identifier id;

        DummyContext(Identifier id) {
            this.id = id;
        }

        @Override public LivingEntity entity() { return null; }
        @Override public UUID uuid() { return UUID.randomUUID(); }
        @Override public EntityType<?> entityType() { return null; }
        @Override public Identifier entityId() { return id; }
        @Override public ServerLevel world() { return null; }
        @Override public ResourceKey<Level> dimensionKey() { return null; }
        @Override public BlockPos blockPos() { return BlockPos.ZERO; }
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
    public void testRegionProtectionRegistry() {
        RegionProtectionRegistry.clear();
        assertTrue(RegionProtectionRegistry.isAllowed(null, BlockPos.ZERO, null));

        // Add a claim provider that protects x=100
        RegionProtectionRegistry.register((level, pos, entity) -> {
            return pos.getX() != 100;
        });

        assertTrue(RegionProtectionRegistry.isAllowed(null, new BlockPos(0, 64, 0), null));
        assertFalse(RegionProtectionRegistry.isAllowed(null, new BlockPos(100, 64, 0), null));

        RegionProtectionRegistry.clear();
    }

    @Test
    public void testCustomEntityProviderBypassAndPredefinedLevel() {
        ModdedMobHandler.clearCustomEntityProviders();

        CustomEntityProvider mockProvider = new CustomEntityProvider() {
            @Override
            public boolean isCustomEntity(LivingEntity entity) {
                return true;
            }

            @Override
            public OptionalInt getPredefinedLevel(LivingEntity entity) {
                return OptionalInt.of(99);
            }

            @Override
            public boolean shouldBypassLeveling(LivingEntity entity) {
                return false;
            }
        };

        ModdedMobHandler.registerCustomEntityProvider(mockProvider);
        assertFalse(ModdedMobHandler.shouldBypass(null));
        assertEquals(99, ModdedMobHandler.getPredefinedLevel(null).orElse(0));

        ModdedMobHandler.clearCustomEntityProviders();
    }

    @Test
    public void testModdedEntityConditionMatching() {
        EntityCondition condition = new EntityCondition(
            Set.of(
                Identifier.fromNamespaceAndPath("betternether", "naga"),
                Identifier.fromNamespaceAndPath("betterend", "end_slime")
            ),
            Set.of(),
            Set.of(),
            Set.of()
        );

        MobContext nagaContext = new DummyContext(Identifier.fromNamespaceAndPath("betternether", "naga"));
        MobContext zombieContext = new DummyContext(Identifier.fromNamespaceAndPath("minecraft", "zombie"));

        assertTrue(condition.matches(nagaContext));
        assertFalse(condition.matches(zombieContext));
    }
}
