package lampas.levelledmobs.util;

import lampas.levelledmobs.context.MobContext;
import lampas.levelledmobs.data.SpawnReason;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Cache key representing the distinct dimensional, spatial, entity, and spawn characteristics of a MobContext.
 */
public record RuleCacheKey(
    Identifier entityId,
    Identifier dimensionId,
    Identifier biomeId,
    SpawnReason spawnReason,
    int altitudeBucket
) {
    public static RuleCacheKey from(MobContext context) {
        Identifier entityId = context.entityId();
        Identifier dimId = context.dimensionId();
        Identifier biomeId = null;
        if (context.biome() != null) {
            biomeId = context.biome().unwrapKey()
                .map(ResourceKey::identifier)
                .orElse(null);
        }
        SpawnReason reason = context.spawnReason();
        int yBucket = context.blockPos() != null ? (context.blockPos().getY() >> 4) : 0; // 16-block chunk vertical sub-slices

        return new RuleCacheKey(entityId, dimId, biomeId, reason, yBucket);
    }
}
