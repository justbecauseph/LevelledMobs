package lampas.levelledmobs.config;

import lampas.levelledmobs.nametag.NametagVisibility;

/**
 * Main global settings configuration record.
 */
public record LevelledMobsConfig(
    int maxMobsPerTick,
    int maxProcessTimeMs,
    String nametagTemplate,
    NametagVisibility nametagVisibility,
    boolean allowBossLeveling,
    boolean debug,
    double defaultXpMultiplier,
    double defaultDropMultiplier,
    double defaultProjectileDamageMultiplier,
    double defaultExplosionDamageMultiplier
) {
    public static final int DEFAULT_MAX_PROCESS_TIME_MS = 2;

    /** Fraction per level above 1 that a levelled mob's projectiles add to their damage, unless its rule sets one. */
    public static final double DEFAULT_PROJECTILE_DAMAGE_MULTIPLIER = 0.05;

    /** Fraction per level above 1 that a levelled mob's own blast adds to its explosion damage, unless its rule sets one. */
    public static final double DEFAULT_EXPLOSION_DAMAGE_MULTIPLIER = 0.01;

    public LevelledMobsConfig(
        int maxMobsPerTick,
        int maxProcessTimeMs,
        String nametagTemplate,
        NametagVisibility nametagVisibility,
        boolean allowBossLeveling,
        boolean debug,
        double defaultXpMultiplier,
        double defaultDropMultiplier
    ) {
        this(
            maxMobsPerTick,
            maxProcessTimeMs,
            nametagTemplate,
            nametagVisibility,
            allowBossLeveling,
            debug,
            defaultXpMultiplier,
            defaultDropMultiplier,
            DEFAULT_PROJECTILE_DAMAGE_MULTIPLIER,
            DEFAULT_EXPLOSION_DAMAGE_MULTIPLIER
        );
    }

    public LevelledMobsConfig(
        int maxMobsPerTick,
        String nametagTemplate,
        NametagVisibility nametagVisibility,
        boolean allowBossLeveling,
        boolean debug,
        double defaultXpMultiplier,
        double defaultDropMultiplier
    ) {
        this(
            maxMobsPerTick,
            DEFAULT_MAX_PROCESS_TIME_MS,
            nametagTemplate,
            nametagVisibility,
            allowBossLeveling,
            debug,
            defaultXpMultiplier,
            defaultDropMultiplier
        );
    }

    public static final LevelledMobsConfig DEFAULT = new LevelledMobsConfig(
        50,
        DEFAULT_MAX_PROCESS_TIME_MS,
        "<gray>Lv. <yellow><level></yellow> <white><mob_name></white>",
        NametagVisibility.HOVER_ONLY,
        false,
        false,
        0.10,
        0.05,
        DEFAULT_PROJECTILE_DAMAGE_MULTIPLIER,
        DEFAULT_EXPLOSION_DAMAGE_MULTIPLIER
    );

    public int effectiveMaxProcessTimeMs() {
        return maxProcessTimeMs > 0 ? maxProcessTimeMs : DEFAULT_MAX_PROCESS_TIME_MS;
    }

    public boolean nametagVisible() {
        return nametagVisibility == NametagVisibility.ALWAYS;
    }
}
