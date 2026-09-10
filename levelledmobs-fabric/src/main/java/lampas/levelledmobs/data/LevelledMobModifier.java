package lampas.levelledmobs.data;

/**
 * Stable, serialized outcome of one LevelledMobs attribute formula.
 *
 * <p>The amount is the effective modifier amount for the mob's assigned
 * level. It is deliberately not a per-level formula value: a config or rule
 * reload must not change an already-created mob.</p>
 */
public record LevelledMobModifier(
    String attributeKey,
    String modifierId,
    double amount,
    String operation
) {
    public LevelledMobModifier {
        attributeKey = attributeKey != null ? attributeKey : "";
        modifierId = modifierId != null ? modifierId : "";
        operation = operation != null ? operation : "";
    }
}
