package lampas.levelledmobs.context;

import lampas.levelledmobs.data.SpawnReason;
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

import java.util.Optional;
import java.util.UUID;

/**
 * Platform-neutral context describing an entity, its location, world, and spawn conditions.
 */
public interface MobContext {
    LivingEntity entity();

    UUID uuid();

    EntityType<?> entityType();

    Identifier entityId();

    ServerLevel world();

    ResourceKey<Level> dimensionKey();

    default Identifier dimensionId() {
        return dimensionKey() != null ? dimensionKey().identifier() : null;
    }

    BlockPos blockPos();

    Vec3 position();

    Holder<Biome> biome();

    boolean isBaby();

    boolean isBoss();

    boolean isTamed();

    boolean hasCustomName();

    Optional<ServerPlayer> nearestPlayer();

    SpawnReason spawnReason();

    static MobContext of(LivingEntity entity, SpawnReason spawnReason) {
        return new DefaultMobContext(entity, spawnReason);
    }
}
