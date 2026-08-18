package lampas.levelledmobs.config;

import lampas.levelledmobs.nametag.NametagVisibility;

/**
 * Main global settings configuration record.
 */
public record LevelledMobsConfig(
    int maxMobsPerTick,
    String nametagTemplate,
    NametagVisibility nametagVisibility,
    boolean allowBossLeveling,
    boolean debug,
    double defaultXpMultiplier,
    double defaultDropMultiplier
) {
    public static final LevelledMobsConfig DEFAULT = new LevelledMobsConfig(
        50,
        "<gray>Lv. <yellow><level></yellow> <white><mob_name></white>",
        NametagVisibility.HOVER_ONLY,
        false,
        false,
        0.10,
        0.05
    );

    public boolean nametagVisible() {
        return nametagVisibility == NametagVisibility.ALWAYS;
    }
}
