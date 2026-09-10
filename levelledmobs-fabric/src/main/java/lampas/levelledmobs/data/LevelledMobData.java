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
    boolean quarantined
) {
    /** Version of the effective modifier representation written to entity data. */
    public static final int CURRENT_RETENTION_VERSION = 1;

    /** Hard bound for data read from an entity, including malformed external data. */
    public static final int MAX_PERSISTED_MODIFIERS = 16;

    public static final LevelledMobData EMPTY = new LevelledMobData(0, false, "none", 0L, 0, List.of(), false);

    public LevelledMobData {
        ruleSet = ruleSet != null ? ruleSet : "none";

        List<LevelledMobModifier> bounded = effectiveModifiers == null
            ? new ArrayList<>()
            : new ArrayList<>(effectiveModifiers.subList(0, Math.min(effectiveModifiers.size(), MAX_PERSISTED_MODIFIERS)));
        effectiveModifiers = Collections.unmodifiableList(bounded);
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
            quarantined
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
            quarantined
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
