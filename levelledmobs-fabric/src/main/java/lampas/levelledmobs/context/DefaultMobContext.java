package lampas.levelledmobs.context;

import lampas.levelledmobs.data.SpawnReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

public class DefaultMobContext implements MobContext {
    private final LivingEntity entity;
    private final SpawnReason spawnReason;
    private final ServerLevel world;
    private final BlockPos blockPos;
    private final Vec3 position;
    private final Identifier entityId;
    private final Holder<Biome> biome;
    private final boolean isBoss;
    private final boolean isTamed;

    public DefaultMobContext(LivingEntity entity, SpawnReason spawnReason) {
        this.entity = entity;
        this.spawnReason = spawnReason != null ? spawnReason : SpawnReason.UNKNOWN;
        this.world = (entity.level() instanceof ServerLevel sl) ? sl : null;
        this.blockPos = entity.blockPosition();
        this.position = entity.position();
        this.entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        this.biome = this.world != null ? this.world.getBiome(this.blockPos) : null;
        this.isBoss = (entity instanceof WitherBoss || entity instanceof EnderDragon);
        this.isTamed = (entity instanceof TamableAnimal tamable && tamable.isTame());
    }

    @Override
    public LivingEntity entity() {
        return entity;
    }

    @Override
    public UUID uuid() {
        return entity.getUUID();
    }

    @Override
    public EntityType<?> entityType() {
        return entity.getType();
    }

    @Override
    public Identifier entityId() {
        return entityId;
    }

    @Override
    public ServerLevel world() {
        return world;
    }

    @Override
    public ResourceKey<Level> dimensionKey() {
        return world != null ? world.dimension() : Level.OVERWORLD;
    }

    @Override
    public BlockPos blockPos() {
        return blockPos;
    }

    @Override
    public Vec3 position() {
        return position;
    }

    @Override
    public Holder<Biome> biome() {
        return biome;
    }

    @Override
    public boolean isBaby() {
        return entity.isBaby();
    }

    @Override
    public boolean isBoss() {
        return isBoss;
    }

    @Override
    public boolean isTamed() {
        return isTamed;
    }

    @Override
    public boolean hasCustomName() {
        return entity.hasCustomName();
    }

    @Override
    public Optional<ServerPlayer> nearestPlayer() {
        if (world == null) {
            return Optional.empty();
        }
        var player = world.getNearestPlayer(entity, 64.0);
        return (player instanceof ServerPlayer sp) ? Optional.of(sp) : Optional.empty();
    }

    @Override
    public SpawnReason spawnReason() {
        return spawnReason;
    }
}
