package lampas.levelledmobs.rules;

/**
 * Result of evaluating a MobContext against the rule database.
 */
public record RuleResult(
    boolean matched,
    EffectiveRule effectiveRule
) {
    public static final RuleResult UNMATCHED = new RuleResult(false, EffectiveRule.DEFAULT);

    public static RuleResult matched(EffectiveRule rule) {
        return new RuleResult(true, rule != null ? rule : EffectiveRule.DEFAULT);
    }
}
