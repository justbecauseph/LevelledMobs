package lampas.levelledmobs.rules;

import lampas.levelledmobs.context.MobContext;
import lampas.levelledmobs.rules.conditions.RuleCondition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Composite predicate evaluating a set of RuleConditions.
 */
public class RulePredicate {
    public static final RulePredicate ALWAYS_TRUE = new RulePredicate(Collections.emptyList());

    private final List<RuleCondition> conditions;

    public RulePredicate(List<RuleCondition> conditions) {
        this.conditions = (conditions != null) ? List.copyOf(conditions) : Collections.emptyList();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean test(MobContext context) {
        for (RuleCondition condition : conditions) {
            if (!condition.matches(context)) {
                return false;
            }
        }
        return true;
    }

    public List<RuleCondition> conditions() {
        return conditions;
    }

    public static class Builder {
        private final List<RuleCondition> conditions = new ArrayList<>();

        public Builder add(RuleCondition condition) {
            if (condition != null) {
                this.conditions.add(condition);
            }
            return this;
        }

        public RulePredicate build() {
            return new RulePredicate(conditions);
        }
    }
}
