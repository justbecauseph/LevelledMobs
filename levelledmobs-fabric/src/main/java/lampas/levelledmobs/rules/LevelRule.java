package lampas.levelledmobs.rules;

import lampas.levelledmobs.context.MobContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a single configured levelling rule with conditions, strategy, and multipliers.
 */
public class LevelRule implements Comparable<LevelRule> {
    private final String id;
    private final int priority;
    private final boolean enabled;
    private final RulePredicate predicate;
    private final IntRange levelRange;
    private final String strategyName;
    private final Map<String, Object> strategyConfig;
    private final Map<String, Object> attributeSettings;
    private final Map<String, Object> dropSettings;
    private final Map<String, Object> xpSettings;
    private final Map<String, Object> nametagSettings;

    public LevelRule(
        String id,
        int priority,
        boolean enabled,
        RulePredicate predicate,
        IntRange levelRange,
        String strategyName,
        Map<String, Object> strategyConfig,
        Map<String, Object> attributeSettings,
        Map<String, Object> dropSettings,
        Map<String, Object> xpSettings,
        Map<String, Object> nametagSettings
    ) {
        this.id = id != null ? id : "unnamed";
        this.priority = priority;
        this.enabled = enabled;
        this.predicate = predicate != null ? predicate : RulePredicate.ALWAYS_TRUE;
        this.levelRange = levelRange != null ? levelRange : IntRange.DEFAULT;
        this.strategyName = strategyName != null ? strategyName : "RANDOM";
        this.strategyConfig = strategyConfig != null ? Collections.unmodifiableMap(new HashMap<>(strategyConfig)) : Collections.emptyMap();
        this.attributeSettings = attributeSettings != null ? Collections.unmodifiableMap(new HashMap<>(attributeSettings)) : Collections.emptyMap();
        this.dropSettings = dropSettings != null ? Collections.unmodifiableMap(new HashMap<>(dropSettings)) : Collections.emptyMap();
        this.xpSettings = xpSettings != null ? Collections.unmodifiableMap(new HashMap<>(xpSettings)) : Collections.emptyMap();
        this.nametagSettings = nametagSettings != null ? Collections.unmodifiableMap(new HashMap<>(nametagSettings)) : Collections.emptyMap();
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public boolean matches(MobContext context) {
        return enabled && predicate.test(context);
    }

    public String id() {
        return id;
    }

    public int priority() {
        return priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public RulePredicate predicate() {
        return predicate;
    }

    public IntRange levelRange() {
        return levelRange;
    }

    public String strategyName() {
        return strategyName;
    }

    public Map<String, Object> strategyConfig() {
        return strategyConfig;
    }

    public Map<String, Object> attributeSettings() {
        return attributeSettings;
    }

    public Map<String, Object> dropSettings() {
        return dropSettings;
    }

    public Map<String, Object> xpSettings() {
        return xpSettings;
    }

    public Map<String, Object> nametagSettings() {
        return nametagSettings;
    }

    @Override
    public int compareTo(LevelRule other) {
        // Higher priority first
        int cmp = Integer.compare(other.priority, this.priority);
        if (cmp != 0) {
            return cmp;
        }
        return this.id.compareTo(other.id);
    }

    public static class Builder {
        private final String id;
        private int priority = 0;
        private boolean enabled = true;
        private RulePredicate predicate = RulePredicate.ALWAYS_TRUE;
        private IntRange levelRange = IntRange.DEFAULT;
        private String strategyName = "RANDOM";
        private Map<String, Object> strategyConfig = new HashMap<>();
        private Map<String, Object> attributeSettings = new HashMap<>();
        private Map<String, Object> dropSettings = new HashMap<>();
        private Map<String, Object> xpSettings = new HashMap<>();
        private Map<String, Object> nametagSettings = new HashMap<>();

        public Builder(String id) {
            this.id = id;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder predicate(RulePredicate predicate) {
            this.predicate = predicate;
            return this;
        }

        public Builder condition(lampas.levelledmobs.rules.conditions.RuleCondition condition) {
            if (condition != null) {
                java.util.List<lampas.levelledmobs.rules.conditions.RuleCondition> current = new java.util.ArrayList<>(this.predicate.conditions());
                current.add(condition);
                this.predicate = new RulePredicate(current);
            }
            return this;
        }

        public Builder levelRange(IntRange range) {
            this.levelRange = range;
            return this;
        }

        public Builder strategy(String name, Map<String, Object> config) {
            this.strategyName = name;
            if (config != null) {
                this.strategyConfig = new HashMap<>(config);
            }
            return this;
        }

        public Builder attributeSettings(Map<String, Object> settings) {
            if (settings != null) {
                this.attributeSettings = new HashMap<>(settings);
            }
            return this;
        }

        public Builder dropSettings(Map<String, Object> settings) {
            if (settings != null) {
                this.dropSettings = new HashMap<>(settings);
            }
            return this;
        }

        public Builder xpSettings(Map<String, Object> settings) {
            if (settings != null) {
                this.xpSettings = new HashMap<>(settings);
            }
            return this;
        }

        public Builder nametagSettings(Map<String, Object> settings) {
            if (settings != null) {
                this.nametagSettings = new HashMap<>(settings);
            }
            return this;
        }

        public LevelRule build() {
            return new LevelRule(
                id, priority, enabled, predicate, levelRange,
                strategyName, strategyConfig, attributeSettings,
                dropSettings, xpSettings, nametagSettings
            );
        }
    }
}
