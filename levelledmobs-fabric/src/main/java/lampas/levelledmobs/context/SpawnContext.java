package lampas.levelledmobs.context;

import lampas.levelledmobs.data.SpawnReason;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Context abstraction for spawn provenance and coordinate details.
 */
public interface SpawnContext {
    SpawnReason spawnReason();

    BlockPos spawnPos();

    Vec3 spawnVec();
}
