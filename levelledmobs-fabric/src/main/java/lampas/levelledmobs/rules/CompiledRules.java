package lampas.levelledmobs.rules;

import lampas.levelledmobs.context.MobContext;
import lampas.levelledmobs.data.SpawnReason;
import lampas.levelledmobs.rules.conditions.AltitudeCondition;
import lampas.levelledmobs.rules.conditions.BiomeCondition;
import lampas.levelledmobs.rules.conditions.DimensionCondition;
import lampas.levelledmobs.rules.conditions.EntityCondition;
import lampas.levelledmobs.rules.conditions.RuleCondition;
import lampas.levelledmobs.rules.conditions.SpawnReasonCondition;
import lampas.levelledmobs.util.RuleCacheKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Immutable compiled rule set with internal thread-safe caching.
 *
 * <p>The cache key holds only the context fields that the enabled rules' conditions read, and Y is reduced to the
 * altitude band it falls in, so the cache stays bounded by entity types, dimensions and the fields the rules use.
 * Keys that match the same rules share one {@link RuleResult}. A rule set whose conditions may read other fields is
 * evaluated on every call instead of cached.
 */
public class CompiledRules {
    public static final CompiledRules EMPTY = new CompiledRules(Collections.emptyList());

    // Key altitude for a context without a block position, where every AltitudeCondition matches. Band indices are never
    // negative.
    private static final int NO_POSITION = -1;

    private final List<LevelRule> rules;
    private final boolean cacheable;
    private final boolean keyBiome;
    private final boolean keySpawnReason;
    // Sorted Y values at which an AltitudeCondition changes result; band i covers [starts[i - 1], starts[i]).
    private final int[] altitudeBandStarts;
    private final Map<RuleCacheKey, RuleResult> cache = new ConcurrentHashMap<>();
    private final Map<List<LevelRule>, RuleResult> resultsByMatchedRules = new ConcurrentHashMap<>();

    public CompiledRules(List<LevelRule> rules) {
        if (rules != null) {
            List<LevelRule> sorted = new ArrayList<>(rules);
            Collections.sort(sorted);
            this.rules = List.copyOf(sorted);
        } else {
            this.rules = Collections.emptyList();
        }

        boolean cacheable = true;
        boolean biome = false;
        boolean spawnReason = false;
        TreeSet<Integer> bandStarts = new TreeSet<>();
        for (LevelRule rule : this.rules) {
            // Exact classes throughout: a subclass or lambda may read fields the key does not hold.
            if (rule.getClass() != LevelRule.class || rule.predicate().getClass() != RulePredicate.class) {
                cacheable = false;
                break;
            }
            if (!rule.isEnabled()) {
                continue;
            }
            for (RuleCondition condition : rule.predicate().conditions()) {
                Class<?> type = condition.getClass();
                if (type == EntityCondition.class || type == DimensionCondition.class) {
                    // Entity id and dimension are always in the key.
                } else if (type == BiomeCondition.class) {
                    biome = true;
                } else if (type == SpawnReasonCondition.class) {
                    spawnReason = true;
                } else if (type == AltitudeCondition.class) {
                    AltitudeCondition altitude = (AltitudeCondition) condition;
                    // matches() holds for minY <= y <= maxY, so its result changes at minY and at maxY + 1.
                    if (altitude.minY() != Integer.MIN_VALUE) {
                        bandStarts.add(altitude.minY());
                    }
                    if (altitude.maxY() != Integer.MAX_VALUE) {
                        bandStarts.add(altitude.maxY() + 1);
                    }
                } else {
                    cacheable = false;
                }
            }
        }
        this.cacheable = cacheable;
        this.keyBiome = biome;
        this.keySpawnReason = spawnReason;
        this.altitudeBandStarts = bandStarts.stream().mapToInt(Integer::intValue).toArray();
    }

    public RuleResult evaluate(MobContext context) {
        if (context == null) {
            return RuleResult.UNMATCHED;
        }
        if (!cacheable) {
            return computeRule(context);
        }

        RuleCacheKey cacheKey = cacheKey(context);
        return cache.computeIfAbsent(cacheKey, k -> computeRule(context));
    }

    private RuleCacheKey cacheKey(MobContext context) {
        Identifier biomeId = null;
        if (keyBiome && context.biome() != null) {
            biomeId = context.biome().unwrapKey()
                .map(ResourceKey::identifier)
                .orElse(null);
        }
        SpawnReason reason = keySpawnReason ? context.spawnReason() : null;
        return new RuleCacheKey(context.entityId(), context.dimensionId(), biomeId, reason, altitudeKey(context.blockPos()));
    }

    private int altitudeKey(BlockPos pos) {
        if (altitudeBandStarts.length == 0) {
            return 0;
        }
        if (pos == null) {
            return NO_POSITION;
        }
        int index = Arrays.binarySearch(altitudeBandStarts, pos.getY());
        return index >= 0 ? index + 1 : -(index + 1);
    }

    private RuleResult computeRule(MobContext context) {
        List<LevelRule> matching = new ArrayList<>();
        for (LevelRule rule : rules) {
            if (rule.matches(context)) {
                matching.add(rule);
            }
        }

        if (matching.isEmpty()) {
            return RuleResult.UNMATCHED;
        }

        // Keyed on the matched rule instances, which equals the matched-rule-id list when ids are unique.
        return resultsByMatchedRules.computeIfAbsent(List.copyOf(matching),
            matched -> RuleResult.matched(EffectiveRule.merge(matched)));
    }

    public List<LevelRule> rules() {
        return rules;
    }

    public int ruleCount() {
        return rules.size();
    }

    int cacheSize() {
        return cache.size();
    }
}
