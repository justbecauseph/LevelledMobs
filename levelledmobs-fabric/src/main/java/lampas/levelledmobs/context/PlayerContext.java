package lampas.levelledmobs.context;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Context abstraction for player presence and statistics.
 */
public interface PlayerContext {
    ServerPlayer player();

    UUID uuid();

    String name();

    BlockPos blockPos();

    Vec3 position();

    int experienceLevel();
}
