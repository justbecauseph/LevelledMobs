package lampas.levelledmobs.compatibility;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Coordinates external mod compatibility checks (region claims, custom entity providers, and modded entities).
 */
public class ModdedMobHandler {
    private static final List<CustomEntityProvider> customEntityProviders = new ArrayList<>();

    public static void registerCustomEntityProvider(CustomEntityProvider provider) {
        if (provider != null) {
            customEntityProviders.add(provider);
        }
    }

    public static void clearCustomEntityProviders() {
        customEntityProviders.clear();
    }

    /**
     * Checks if region protection allows leveling at this location.
     */
    public static boolean canLevelAt(ServerLevel level, BlockPos pos, LivingEntity entity) {
        return RegionProtectionRegistry.isAllowed(level, pos, entity);
    }

    /**
     * Checks if any custom entity provider claims this entity or requests bypass.
     */
    public static boolean shouldBypass(LivingEntity entity) {
        for (CustomEntityProvider provider : customEntityProviders) {
            if (provider.isCustomEntity(entity) && provider.shouldBypassLeveling(entity)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Retrieves any predefined level from a custom entity provider.
     */
    public static OptionalInt getPredefinedLevel(LivingEntity entity) {
        for (CustomEntityProvider provider : customEntityProviders) {
            if (provider.isCustomEntity(entity)) {
                OptionalInt lvl = provider.getPredefinedLevel(entity);
                if (lvl.isPresent()) {
                    return lvl;
                }
            }
        }
        return OptionalInt.empty();
    }
}
