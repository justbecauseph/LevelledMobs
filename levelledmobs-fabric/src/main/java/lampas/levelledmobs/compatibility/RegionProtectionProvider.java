package lampas.levelledmobs.compatibility;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * Service Provider Interface (SPI) for region and claim protection systems
 * (such as FLAN, GOML, Common Protection API, GriefDefender).
 */
@FunctionalInterface
public interface RegionProtectionProvider {
    /**
     * Returns true if LevelledMobs is permitted to level an entity at this location.
     */
    boolean canLevel(ServerLevel level, BlockPos pos, LivingEntity entity);
}
