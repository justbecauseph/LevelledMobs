package lampas.levelledmobs.rules.conditions;

import lampas.levelledmobs.context.MobContext;
import lampas.levelledmobs.data.SpawnReason;

import java.util.Collections;
import java.util.Set;

/**
 * Evaluates spawn provenance reasons (e.g. NATURAL, SPAWNER, SPAWN_EGG, TRIAL_SPAWNER).
 */
public class SpawnReasonCondition implements RuleCondition {
    private final Set<SpawnReason> includeReasons;
    private final Set<SpawnReason> excludeReasons;

    public SpawnReasonCondition(Set<SpawnReason> includeReasons, Set<SpawnReason> excludeReasons) {
        this.includeReasons = includeReasons != null ? includeReasons : Collections.emptySet();
        this.excludeReasons = excludeReasons != null ? excludeReasons : Collections.emptySet();
    }

    @Override
    public boolean matches(MobContext context) {
        SpawnReason reason = context.spawnReason();

        if (excludeReasons.contains(reason)) {
            return false;
        }

        if (includeReasons.isEmpty()) {
            return true;
        }

        return includeReasons.contains(reason);
    }

    public Set<SpawnReason> includeReasons() {
        return includeReasons;
    }

    public Set<SpawnReason> excludeReasons() {
        return excludeReasons;
    }
}
