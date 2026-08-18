package lampas.levelledmobs.compatibility;

import net.minecraft.world.entity.LivingEntity;

import java.util.OptionalInt;

/**
 * SPI for custom/scripted mob frameworks (e.g. MythicMobs equivalents on Fabric).
 */
public interface CustomEntityProvider {
    /**
     * Returns true if this provider recognizes and controls this entity.
     */
    boolean isCustomEntity(LivingEntity entity);

    /**
     * Returns the predefined custom level if applicable.
     */
    default OptionalInt getPredefinedLevel(LivingEntity entity) {
        return OptionalInt.empty();
    }

    /**
     * Returns true if LevelledMobs should completely skip leveling this entity.
     */
    default boolean shouldBypassLeveling(LivingEntity entity) {
        return false;
    }
}
