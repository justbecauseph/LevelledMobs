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
    double groupFactor
) {
    /** Version of the effective modifier representation written to entity data. */
    public static final int CURRENT_RETENTION_VERSION = 1;

    /** Hard bound for data read from an entity, including malformed external data. */
    public static final int MAX_PERSISTED_MODIFIERS = 16;

    /** Group factor of a mob that is not a multi-party boss: its max health is not multiplied. */
    public static final double NO_GROUP_FACTOR = 1.0;

    public static final LevelledMobData EMPTY = new LevelledMobData(0, false, "none", 0L, 0, List.of(), false);

    public LevelledMobData {
        ruleSet = ruleSet != null ? ruleSet : "none";

        List<LevelledMobModifier> bounded = effectiveModifiers == null
            ? new ArrayList<>()
            : new ArrayList<>(effectiveModifiers.subList(0, Math.min(effectiveModifiers.size(), MAX_PERSISTED_MODIFIERS)));
        effectiveModifiers = Collections.unmodifiableList(bounded);
        // Malformed external data must never shrink or blow up a mob's health.
        groupFactor = (Double.isFinite(groupFactor) && groupFactor >= NO_GROUP_FACTOR) ? groupFactor : NO_GROUP_FACTOR;
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
            groupFactor
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
            groupFactor
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
            groupFactor
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
            factor
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
