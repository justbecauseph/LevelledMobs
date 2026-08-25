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
    int altitude
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
        int altitude = context.blockPos() != null ? context.blockPos().getY() : 0;

        return new RuleCacheKey(entityId, dimId, biomeId, reason, altitude);
    }
}
