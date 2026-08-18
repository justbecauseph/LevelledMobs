package lampas.levelledmobs.data;

/**
 * Duck-typing interface injected into LivingEntity to store and retrieve LevelledMobData.
 */
public interface LevelledMobHolder {
    LevelledMobData lampas$getLevelData();

    void lampas$setLevelData(LevelledMobData data);

    default boolean lampas$isLevelled() {
        LevelledMobData data = lampas$getLevelData();
        return data != null && data.levelled();
    }

    default int lampas$getLevel() {
        LevelledMobData data = lampas$getLevelData();
        return data != null ? data.level() : 0;
    }
}
