package lampas.levelledmobs.util;

import lampas.levelledmobs.data.SpawnReason;
import net.minecraft.resources.Identifier;

/**
 * Cache key for rule resolution. CompiledRules fills in only the context fields its enabled rules read and leaves the
 * others null or zero, so contexts that cannot resolve differently share one key. {@code altitude} holds an altitude
 * band index, or zero when no rule reads Y.
 */
public record RuleCacheKey(
    Identifier entityId,
    Identifier dimensionId,
    Identifier biomeId,
    SpawnReason spawnReason,
    int altitude
) {
}
