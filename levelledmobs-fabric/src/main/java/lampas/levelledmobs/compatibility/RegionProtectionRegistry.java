package lampas.levelledmobs.compatibility;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry coordinating active region and claims protection providers.
 */
public class RegionProtectionRegistry {
    private static final List<RegionProtectionProvider> providers = new ArrayList<>();

    public static void register(RegionProtectionProvider provider) {
        if (provider != null) {
            providers.add(provider);
        }
    }

    public static void clear() {
        providers.clear();
    }

    public static boolean isAllowed(ServerLevel level, BlockPos pos, LivingEntity entity) {
        for (RegionProtectionProvider provider : providers) {
            if (!provider.canLevel(level, pos, entity)) {
                return false;
            }
        }
        return true;
    }
}
