package lampas.levelledmobs.level;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;

import java.util.HashSet;
import java.util.Set;

/**
 * Classifies entities as bosses to exclude them from standard mob leveling unless explicitly enabled.
 */
public class BossClassifier {
    private static final TagKey<EntityType<?>> COMMON_BOSS_TAG = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("c", "bosses"));
    private static final TagKey<EntityType<?>> FABRIC_BOSS_TAG = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("fabric", "bosses"));

    private final Set<Identifier> explicitBosses = new HashSet<>();
    private boolean allowBossLeveling = false;

    public BossClassifier() {
        explicitBosses.add(Identifier.fromNamespaceAndPath("minecraft", "ender_dragon"));
        explicitBosses.add(Identifier.fromNamespaceAndPath("minecraft", "wither"));
        explicitBosses.add(Identifier.fromNamespaceAndPath("minecraft", "elder_guardian"));
        explicitBosses.add(Identifier.fromNamespaceAndPath("minecraft", "warden"));
    }

    public boolean isBoss(LivingEntity entity) {
        if (entity == null) return false;
        if (entity instanceof EnderDragon || entity instanceof WitherBoss) {
            return true;
        }

        EntityType<?> type = entity.getType();
        if (type != null) {
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (id != null && explicitBosses.contains(id)) {
                return true;
            }
            Holder<EntityType<?>> holder = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type);
            if (holder.is(COMMON_BOSS_TAG) || holder.is(FABRIC_BOSS_TAG)) {
                return true;
            }
        }

        return false;
    }

    public boolean isBossType(Identifier entityId) {
        return entityId != null && explicitBosses.contains(entityId);
    }

    public boolean shouldLevel(LivingEntity entity) {
        if (allowBossLeveling) {
            return true;
        }
        return !isBoss(entity);
    }

    public void setAllowBossLeveling(boolean allow) {
        this.allowBossLeveling = allow;
    }

    public void addBoss(Identifier entityId) {
        if (entityId != null) {
            explicitBosses.add(entityId);
        }
    }
}
