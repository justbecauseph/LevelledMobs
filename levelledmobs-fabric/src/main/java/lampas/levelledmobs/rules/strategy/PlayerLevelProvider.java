package lampas.levelledmobs.rules.strategy;

import net.minecraft.server.level.ServerPlayer;

/**
 * SPI for querying player levels or RPG stats for PlayerLevellingStrategy.
 */
public interface PlayerLevelProvider {
    /**
     * Resolves the numeric level for a given player and variable name.
     *
     * @param player   The player entity
     * @param variable The variable key (e.g. "%level%", "%exp%", "%stats_total%")
     * @return The numeric value
     */
    float getPlayerLevel(ServerPlayer player, String variable);

    /**
     * Default vanilla provider using player experience level.
     */
    PlayerLevelProvider VANILLA = (player, variable) -> {
        if (player == null) return 1f;
        if (variable != null && (variable.equalsIgnoreCase("%exp%") || variable.equalsIgnoreCase("%experience%"))) {
            return (float) player.totalExperience;
        }
        return (float) player.experienceLevel;
    };
}
