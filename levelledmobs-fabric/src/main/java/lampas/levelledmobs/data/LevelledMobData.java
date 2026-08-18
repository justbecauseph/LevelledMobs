package lampas.levelledmobs.data;

/**
 * Immutable record holding LevelledMobs metadata attached to an entity.
 */
public record LevelledMobData(
    int level,
    boolean levelled,
    String ruleSet,
    long generatedAt
) {
    public static final LevelledMobData EMPTY = new LevelledMobData(0, false, "none", 0L);

    public static LevelledMobData of(int level, String ruleSet) {
        return new LevelledMobData(level, true, ruleSet, System.currentTimeMillis());
    }
}
