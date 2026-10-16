package lampas.levelledmobs.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable record holding LevelledMobs metadata attached to an entity.
 */
public record LevelledMobData(
    int level,
    boolean levelled,
    String ruleSet,
    long generatedAt,
    int retentionVersion,
    List<LevelledMobModifier> effectiveModifiers,
    boolean quarantined,
    double groupFactor,
    double projectileDamagePerLevel,
    double explosionDamagePerLevel
) {
    /** Version of the effective modifier representation written to entity data. */
    public static final int CURRENT_RETENTION_VERSION = 1;

    /** Hard bound for data read from an entity, including malformed external data. */
    public static final int MAX_PERSISTED_MODIFIERS = 16;

    /** Group factor of a mob that is not a multi-party boss: its max health is not multiplied. */
    public static final double NO_GROUP_FACTOR = 1.0;

    /**
     * Per-level damage fraction for a levelled mob that stores none: its projectile and explosion damage then follow
     * the global settings default. Legacy data and rules without the attribute carry this value.
     */
    public static final double NO_DAMAGE_OVERRIDE = Double.NaN;

    public static final LevelledMobData EMPTY = new LevelledMobData(0, false, "none", 0L, 0, List.of(), false);

    public LevelledMobData {
        ruleSet = ruleSet != null ? ruleSet : "none";

        List<LevelledMobModifier> bounded = effectiveModifiers == null
            ? new ArrayList<>()
            : new ArrayList<>(effectiveModifiers.subList(0, Math.min(effectiveModifiers.size(), MAX_PERSISTED_MODIFIERS)));
        effectiveModifiers = Collections.unmodifiableList(bounded);
        // Malformed external data must never shrink or blow up a mob's health.
        groupFactor = (Double.isFinite(groupFactor) && groupFactor >= NO_GROUP_FACTOR) ? groupFactor : NO_GROUP_FACTOR;
        projectileDamagePerLevel = sanitizeDamagePerLevel(projectileDamagePerLevel);
        explosionDamagePerLevel = sanitizeDamagePerLevel(explosionDamagePerLevel);
    }

    /** Keeps a finite, non-negative fraction. Anything else, including NaN, means no override. */
    private static double sanitizeDamagePerLevel(double fraction) {
        return (Double.isFinite(fraction) && fraction >= 0.0) ? fraction : NO_DAMAGE_OVERRIDE;
    }

    /** Backward-compatible constructor for callers that only have legacy metadata. */
    public LevelledMobData(int level, boolean levelled, String ruleSet, long generatedAt) {
        this(level, levelled, ruleSet, generatedAt, 0, List.of(), false);
    }

    /** Backward-compatible constructor for callers that include retention data. */
    public LevelledMobData(
        int level,
        boolean levelled,
        String ruleSet,
        long generatedAt,
        int retentionVersion,
        List<LevelledMobModifier> effectiveModifiers
    ) {
        this(level, levelled, ruleSet, generatedAt, retentionVersion, effectiveModifiers, false);
    }

    /** Backward-compatible constructor for callers that predate the boss group factor. */
    public LevelledMobData(
        int level,
        boolean levelled,
        String ruleSet,
        long generatedAt,
        int retentionVersion,
        List<LevelledMobModifier> effectiveModifiers,
        boolean quarantined
    ) {
        this(level, levelled, ruleSet, generatedAt, retentionVersion, effectiveModifiers, quarantined, NO_GROUP_FACTOR);
    }

    /** Backward-compatible constructor for callers that predate the projectile and explosion damage overrides. */
    public LevelledMobData(
        int level,
        boolean levelled,
        String ruleSet,
        long generatedAt,
        int retentionVersion,
        List<LevelledMobModifier> effectiveModifiers,
        boolean quarantined,
        double groupFactor
    ) {
        this(level, levelled, ruleSet, generatedAt, retentionVersion, effectiveModifiers, quarantined, groupFactor,
            NO_DAMAGE_OVERRIDE, NO_DAMAGE_OVERRIDE);
    }

    public static LevelledMobData of(int level, String ruleSet) {
        // The effective plan is attached by MobLevelingService after the rule is resolved.
        return new LevelledMobData(level, true, ruleSet, System.currentTimeMillis(), 0, List.of(), false);
    }

    public LevelledMobData withEffectiveModifiers(List<LevelledMobModifier> modifiers) {
        return new LevelledMobData(
            level,
            levelled,
            ruleSet,
            generatedAt,
            CURRENT_RETENTION_VERSION,
            modifiers,
            quarantined,
            groupFactor,
            projectileDamagePerLevel,
            explosionDamagePerLevel
        );
    }

    /**
     * Replaces the level and its persisted plan for an already levelled mob. The stored rule id, generation time and
     * quarantine state are kept. The plan is always stored in the current retention format.
     */
    public LevelledMobData withRelevel(int newLevel, List<LevelledMobModifier> modifiers) {
        return new LevelledMobData(
            newLevel,
            levelled,
            ruleSet,
            generatedAt,
            CURRENT_RETENTION_VERSION,
            modifiers,
            quarantined,
            groupFactor,
            projectileDamagePerLevel,
            explosionDamagePerLevel
        );
    }

    public LevelledMobData withQuarantined(boolean quarantined) {
        return new LevelledMobData(
            level,
            levelled,
            ruleSet,
            generatedAt,
            retentionVersion,
            effectiveModifiers,
            quarantined,
            groupFactor,
            projectileDamagePerLevel,
            explosionDamagePerLevel
        );
    }

    /**
     * Sets the multi-party boss factor on max health. It is fixed when the boss is first levelled and kept by
     * restores and relevels.
     */
    public LevelledMobData withGroupFactor(double factor) {
        return new LevelledMobData(
            level,
            levelled,
            ruleSet,
            generatedAt,
            retentionVersion,
            effectiveModifiers,
            quarantined,
            factor,
            projectileDamagePerLevel,
            explosionDamagePerLevel
        );
    }

    /**
     * Sets the per-level fractions that the mob's projectiles and its own blast add to its damage. They are fixed when
     * the mob is levelled, relevelled or admin-set, from the rule that matches it. NaN means no override, so the mob
     * uses the global settings default. A negative or infinite fraction is treated as NaN.
     */
    public LevelledMobData withDamagePerLevel(double projectile, double explosion) {
        return new LevelledMobData(
            level,
            levelled,
            ruleSet,
            generatedAt,
            retentionVersion,
            effectiveModifiers,
            quarantined,
            groupFactor,
            projectile,
            explosion
        );
    }

    /**
     * True when a current-format plan is present. The attribute service still
     * validates the complete plan and every stable identity before applying it.
     */
    public boolean hasPersistedModifiers() {
        return retentionVersion == CURRENT_RETENTION_VERSION && !effectiveModifiers.isEmpty();
    }
}
