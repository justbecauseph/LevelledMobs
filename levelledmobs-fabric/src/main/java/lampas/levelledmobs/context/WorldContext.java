package lampas.levelledmobs.context;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Context abstraction for world and dimension metadata.
 */
public interface WorldContext {
    ServerLevel world();

    ResourceKey<Level> dimensionKey();

    default Identifier dimensionId() {
        return dimensionKey() != null ? dimensionKey().identifier() : null;
    }
}
