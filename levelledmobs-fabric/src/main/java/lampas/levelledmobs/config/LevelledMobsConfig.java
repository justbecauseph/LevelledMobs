package lampas.levelledmobs.config;

/**
 * Main global settings configuration record.
 */
public record LevelledMobsConfig(
    int maxMobsPerTick,
    String nametagTemplate,
    boolean nametagVisible,
    boolean allowBossLeveling,
    boolean debug,
    double defaultXpMultiplier,
    double defaultDropMultiplier
) {
    public static final LevelledMobsConfig DEFAULT = new LevelledMobsConfig(
        50,
        "<gray>Lv. <yellow><level></yellow> <white><mob_name></white>",
        true,
        false,
        false,
        0.10,
        0.05
    );
}
