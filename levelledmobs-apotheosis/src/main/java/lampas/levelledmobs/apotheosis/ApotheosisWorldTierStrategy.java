package lampas.levelledmobs.apotheosis;

import dev.shadowsoffire.apotheosis.tiers.TierContextBridge;
import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import lampas.levelledmobs.context.MobContext;
import lampas.levelledmobs.rules.EffectiveRule;
import lampas.levelledmobs.rules.IntRange;
import lampas.levelledmobs.rules.strategy.LevelStrategy;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Assigns a persistent LevelledMobs level from an immutable managed creation
 * context, with the standalone Apotheosis nearest-player behavior retained
 * when no external provider is installed.
 */
public final class ApotheosisWorldTierStrategy implements LevelStrategy {
    public static final ApotheosisWorldTierStrategy INSTANCE = new ApotheosisWorldTierStrategy();
    public static final String NAME = "APOTHEOSIS_WORLD_TIER";
    static final int MAX_PROFILE_CACHE_ENTRIES = 256;

    private final Map<ProfileKey, ProfileResult> profiles = new HashMap<>();
    private final Set<ProfileKey> warnedProfiles = new HashSet<>();

    private ApotheosisWorldTierStrategy() {}

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public synchronized void clearConfigurationCache() {
        profiles.clear();
        warnedProfiles.clear();
    }

    @Override
    public boolean shouldRetryDeferredLevel(MobContext context, EffectiveRule rule) {
        if (context == null || !(context.entity() instanceof Mob mob) ||
            !TierContextBridge.installed() || !TierContextBridge.managed(mob)) {
            return false;
        }

        // An absent creation context means the managed provider is still
        // bootstrapping. An explicitly UNKNOWN context is permanent for this
        // mob and must be quarantined without a nearest-player fallback.
        return TierContextBridge.creation(mob).isEmpty();
    }

    @Override
    public int constrainLevel(MobContext context, EffectiveRule rule, int level) {
        if (level <= 0 || rule == null || context == null ||
            !(context.entity() instanceof Mob mob) ||
            !TierContextBridge.installed() || !TierContextBridge.managed(mob)) {
            return level;
        }

        Optional<TierContextBridge.CreationContext> creation = TierContextBridge.creation(mob);
        if (creation.isEmpty() || creation.get().provenance() == null ||
            "UNKNOWN".equalsIgnoreCase(creation.get().provenance())) {
            return 0;
        }

        IntRange boundedBand = managedBand(rule, creation.get());
        return boundedBand != null && boundedBand.contains(level) ? level : 0;
    }

    @Override
    public int calculateLevel(MobContext context, EffectiveRule rule) {
        if (rule == null) {
            return 1;
        }
        IntRange ruleRange = rule.levelRange();

        // A managed mob gets one immutable creation context from Yggdrasil.
        // Never replace it with an unlimited nearest-player query: that would
        // let movement or a later cap change reroll an existing encounter.
        if (context != null && context.entity() instanceof Mob mob &&
            TierContextBridge.installed() && TierContextBridge.managed(mob)) {
            Optional<TierContextBridge.CreationContext> creation = TierContextBridge.creation(mob);
            if (creation.isEmpty()) {
                // Zero is the LevelledMobs strategy defer sentinel. The core
                // service leaves the entity unlevelled so Apotheosis can retry
                // after its bounded managed-content deferral completes.
                ApotheosisLevelledMobsAddon.LOGGER.debug(
                    "Managed mob {} has no Yggdrasil creation context; deferring level resolution",
                    context.uuid()
                );
                return 0;
            }
            if (creation.get().provenance() == null ||
                "UNKNOWN".equalsIgnoreCase(creation.get().provenance())) {
                // The provider deliberately cannot establish trusted origin
                // for legacy/unknown mobs. Preserve their existing vanilla
                // stats and deny a newly synthesized LevelledMobs modifier.
                ApotheosisLevelledMobsAddon.LOGGER.debug(
                    "Managed mob {} has unknown Yggdrasil provenance; leaving it unlevelled",
                    context.uuid()
                );
                return 0;
            }
            return calculateFromCreation(context, rule, creation.get(), ruleRange);
        }

        if (context == null || context.world() == null || context.entity() == null) {
            return ruleRange.min();
        }

        // Match Apotheosis' monster augment source selection exactly. MobContext's generic
        // nearest-player accessor is intentionally not used because it is capped at 64 blocks.
        Player player = context.world().getNearestPlayer(context.entity(), -1.0D);
        if (player == null) {
            ApotheosisLevelledMobsAddon.LOGGER.debug(
                "No player available for mob {}; using rule '{}' minimum level {}",
                context.uuid(), rule.primaryRuleId(), ruleRange.min()
            );
            return ruleRange.min();
        }

        ProfileKey key = profileKey(rule, false);
        ProfileResult result = profileFor(key, rule, false);
        if (result.profile() == null) {
            if (markProfileWarned(key)) {
                ApotheosisLevelledMobsAddon.LOGGER.warn(
                    "Invalid Apotheosis World Tier strategy configuration for rule '{}': {}. " +
                        "Using minimum level {}.",
                    rule.primaryRuleId(), result.error(), ruleRange.min()
                );
            }
            return ruleRange.min();
        }

        WorldTier tier = WorldTier.getTier(player);
        IntRange band = result.profile().band(tier);
        int selectedLevel = chooseLevel(context.entity().getRandom(), band);
        int clampedLevel = ruleRange.clamp(selectedLevel);

        ApotheosisLevelledMobsAddon.LOGGER.debug(
            "Resolved mob {} from player {} at World Tier '{}' to band {}-{} and level {} for rule '{}'",
            context.uuid(), player.getUUID(), tier.getSerializedName(), band.min(), band.max(),
            clampedLevel, rule.primaryRuleId()
        );
        return clampedLevel;
    }

    private int calculateFromCreation(
        MobContext context,
        EffectiveRule rule,
        TierContextBridge.CreationContext creation,
        IntRange ruleRange
    ) {
        if (creation.tier() == null || creation.minimumLevel() < 1 ||
            creation.maximumLevel() < creation.minimumLevel()) {
            ApotheosisLevelledMobsAddon.LOGGER.warn(
                "Invalid Yggdrasil creation bounds for managed mob {}; deferring level resolution",
                context.uuid()
            );
            return 0;
        }

        IntRange creationRange = intersect(ruleRange,
            IntRange.of(creation.minimumLevel(), creation.maximumLevel()));
        if (creationRange == null) {
            ApotheosisLevelledMobsAddon.LOGGER.warn(
                "Yggdrasil creation bounds do not intersect LevelledMobs rule '{}' for mob {}; deferring",
                rule.primaryRuleId(), context.uuid()
            );
            return 0;
        }

        ProfileKey key = profileKey(rule, true);
        ProfileResult result = profileFor(key, rule, true);
        if (result.profile() == null) {
            if (markProfileWarned(key)) {
                ApotheosisLevelledMobsAddon.LOGGER.warn(
                    "Invalid Apotheosis World Tier strategy configuration for rule '{}': {}. Deferring managed mob {}.",
                    rule.primaryRuleId(), result.error(), context.uuid()
                );
            }
            return 0;
        }

        IntRange tierBand = result.profile().band(creation.tier());
        IntRange boundedBand = intersect(tierBand, creationRange);
        if (boundedBand == null) {
            // A fixed authored/configured band must not be silently rewritten
            // when it conflicts with the authoritative creation bounds.
            ApotheosisLevelledMobsAddon.LOGGER.warn(
                "Yggdrasil bounds conflict with Apotheosis tier '{}' band for rule '{}' and mob {}; deferring",
                creation.tier().getSerializedName(), rule.primaryRuleId(), context.uuid()
            );
            return 0;
        }

        int selectedLevel = chooseLevel(context.entity().getRandom(), boundedBand);
        ApotheosisLevelledMobsAddon.LOGGER.debug(
            "Resolved managed mob {} from Yggdrasil tier '{}' bounds {}-{} to level {} for rule '{}' (revision '{}')",
            context.uuid(), creation.tier().getSerializedName(), boundedBand.min(), boundedBand.max(),
            selectedLevel, rule.primaryRuleId(), creation.contentRevision()
        );
        return selectedLevel;
    }

    private synchronized ProfileResult profileFor(ProfileKey key, EffectiveRule rule, boolean managed) {
        ProfileResult cached = profiles.get(key);
        if (cached != null) {
            return cached;
        }
        if (profiles.size() >= MAX_PROFILE_CACHE_ENTRIES) {
            profiles.clear();
            warnedProfiles.clear();
        }
        ProfileResult result = managed ? parseManagedProfile(rule) : parseProfile(rule);
        profiles.put(key, result);
        return result;
    }

    private synchronized boolean markProfileWarned(ProfileKey key) {
        return warnedProfiles.add(key);
    }

    /**
     * Managed rules may retain an unrelated LM strategy configuration. Only a
     * supplied tier_bands object is interpreted as an Apotheosis profile;
     * otherwise derive the normal five-tier partition from the effective LM
     * range and then intersect it with the authoritative creation bounds.
     */
    private static ProfileResult parseManagedProfile(EffectiveRule rule) {
        Map<String, Object> config = rule.strategyConfig();
        if (config == null || config.get("tier_bands") == null) {
            return new ProfileResult(TierScaleProfile.automatic(rule.levelRange()), null);
        }
        return parseProfile(rule);
    }

    private IntRange managedBand(EffectiveRule rule, TierContextBridge.CreationContext creation) {
        if (creation.tier() == null || creation.minimumLevel() < 1 ||
            creation.maximumLevel() < creation.minimumLevel()) {
            return null;
        }

        IntRange creationRange = intersect(rule.levelRange(),
            IntRange.of(creation.minimumLevel(), creation.maximumLevel()));
        if (creationRange == null) {
            return null;
        }

        ProfileKey key = profileKey(rule, true);
        ProfileResult result = profileFor(key, rule, true);
        if (result.profile() == null) {
            return null;
        }
        return intersect(result.profile().band(creation.tier()), creationRange);
    }

    private static IntRange intersect(IntRange first, IntRange second) {
        if (first == null || second == null) return null;
        int min = Math.max(first.min(), second.min());
        int max = Math.min(first.max(), second.max());
        return min <= max ? IntRange.of(min, max) : null;
    }

    static int chooseLevel(RandomSource random, IntRange band) {
        long width = (long) band.max() - band.min() + 1L;
        if (width <= 1L) {
            return band.min();
        }
        if (width <= Integer.MAX_VALUE) {
            return band.min() + random.nextInt((int) width);
        }

        long offset = (long) (random.nextDouble() * width);
        return Math.toIntExact((long) band.min() + offset);
    }

    private static ProfileResult parseProfile(EffectiveRule rule) {
        try {
            return new ProfileResult(
                TierScaleProfile.fromConfig(rule.levelRange(), rule.strategyConfig()),
                null
            );
        }
        catch (RuntimeException ex) {
            String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            return new ProfileResult(null, message);
        }
    }

    private static ProfileKey profileKey(EffectiveRule rule, boolean managed) {
        return new ProfileKey(
            rule.primaryRuleId(),
            rule.levelRange(),
            freezeConfig(rule.strategyConfig()),
            managed
        );
    }

    private static Map<String, Object> freezeConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            copy.put(entry.getKey(), freezeValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Object freezeValue(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                copy.put(freezeValue(entry.getKey()), freezeValue(entry.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof java.util.List<?> rawList) {
            ArrayList<Object> copy = new ArrayList<>(rawList.size());
            for (Object element : rawList) {
                copy.add(freezeValue(element));
            }
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Set<?> rawSet) {
            Set<Object> copy = new HashSet<>();
            for (Object element : rawSet) {
                copy.add(freezeValue(element));
            }
            return Collections.unmodifiableSet(copy);
        }
        if (value != null && value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            ArrayList<Object> copy = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                copy.add(freezeValue(java.lang.reflect.Array.get(value, index)));
            }
            return Collections.unmodifiableList(copy);
        }
        return value;
    }

    private record ProfileKey(
        String ruleId,
        IntRange range,
        Map<String, Object> config,
        boolean managed
    ) {}

    private record ProfileResult(TierScaleProfile profile, String error) {}
}
