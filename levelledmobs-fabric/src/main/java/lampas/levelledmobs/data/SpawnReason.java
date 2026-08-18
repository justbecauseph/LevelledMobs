package lampas.levelledmobs.data;

/**
 * Platform-neutral classification of entity spawn origins.
 */
public enum SpawnReason {
    NATURAL,
    CHUNK_GENERATION,
    SPAWNER,
    SPAWN_EGG,
    COMMAND,
    STRUCTURE,
    BREEDING,
    CONVERSION,
    REINFORCEMENT,
    PATROL,
    RAID,
    TRIAL_SPAWNER,
    PORTAL,
    CUSTOM,
    UNKNOWN
}
