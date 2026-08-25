package lampas.levelledmobs.apotheosis;

import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import lampas.levelledmobs.rules.IntRange;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable mapping from Apotheosis world tiers to LevelledMobs level bands.
 */
public final class TierScaleProfile {
    public static final String RANDOM_PARTITION = "random_partition";
    public static final String RULE_MIN = "rule_min";

    private final IntRange ruleRange;
    private final Map<WorldTier, IntRange> bands;
    private final String distribution;
    private final String noPlayerBehavior;

    private TierScaleProfile(
        IntRange ruleRange,
        Map<WorldTier, IntRange> bands,
        String distribution,
        String noPlayerBehavior
    ) {
        this.ruleRange = Objects.requireNonNull(ruleRange, "ruleRange");
        this.bands = Collections.unmodifiableMap(new EnumMap<>(bands));
        this.distribution = Objects.requireNonNull(distribution, "distribution");
        this.noPlayerBehavior = Objects.requireNonNull(noPlayerBehavior, "noPlayerBehavior");
    }

    /**
     * Builds five evenly distributed bands. Remainders are assigned from Haven upward.
     * If a rule contains fewer than five levels, later tiers clamp to the rule maximum.
     */
    public static TierScaleProfile automatic(IntRange ruleRange) {
        return automatic(ruleRange, RANDOM_PARTITION, RULE_MIN);
    }

    private static TierScaleProfile automatic(
        IntRange ruleRange,
        String distribution,
        String noPlayerBehavior
    ) {
        Objects.requireNonNull(ruleRange, "ruleRange");

        EnumMap<WorldTier, IntRange> bands = new EnumMap<>(WorldTier.class);
        long size = (long) ruleRange.max() - ruleRange.min() + 1L;
        long baseSize = size / WorldTier.values().length;
        int remainder = (int) (size % WorldTier.values().length);
        long cursor = ruleRange.min();

        for (int index = 0; index < WorldTier.values().length; index++) {
            long bandSize = baseSize + (index < remainder ? 1L : 0L);
            if (bandSize == 0L) {
                bands.put(WorldTier.values()[index], IntRange.single(ruleRange.max()));
                continue;
            }

            long end = cursor + bandSize - 1L;
            bands.put(
                WorldTier.values()[index],
                IntRange.of(Math.toIntExact(cursor), Math.toIntExact(end))
            );
            cursor = end + 1L;
        }

        return new TierScaleProfile(
            ruleRange,
            bands,
            validateDistribution(distribution),
            validateNoPlayerBehavior(noPlayerBehavior)
        );
    }

    /**
     * Parses the strategy-specific portion of a LevelledMobs rule.
     */
    public static TierScaleProfile fromConfig(IntRange ruleRange, Map<String, Object> config) {
        Objects.requireNonNull(ruleRange, "ruleRange");
        Map<String, Object> safeConfig = config != null ? config : Map.of();

        String distribution = getString(safeConfig, "distribution", RANDOM_PARTITION);
        String noPlayerBehavior = getString(safeConfig, "no_player", RULE_MIN);
        Object rawBands = safeConfig.get("tier_bands");
        if (rawBands == null) {
            return automatic(ruleRange, distribution, noPlayerBehavior);
        }
        if (!(rawBands instanceof Map<?, ?> bandMap)) {
            throw new IllegalArgumentException("tier_bands must be an object");
        }

        Set<String> expectedNames = new HashSet<>();
        for (WorldTier tier : WorldTier.values()) {
            expectedNames.add(tier.getSerializedName());
        }
        for (Object rawKey : bandMap.keySet()) {
            String key = String.valueOf(rawKey);
            if (!expectedNames.contains(key)) {
                throw new IllegalArgumentException("Unknown Apotheosis tier band: " + rawKey);
            }
        }

        EnumMap<WorldTier, IntRange> parsedBands = new EnumMap<>(WorldTier.class);
        for (WorldTier tier : WorldTier.values()) {
            Object rawBand = bandMap.get(tier.getSerializedName());
            if (!(rawBand instanceof Map<?, ?> values)) {
                throw new IllegalArgumentException(
                    "Missing or invalid tier band for " + tier.getSerializedName()
                );
            }

            int min = getRequiredInt(values, "min", tier);
            int max = getRequiredInt(values, "max", tier);
            if (min > max) {
                throw new IllegalArgumentException(
                    "Tier band for " + tier.getSerializedName() + " has min greater than max"
                );
            }
            parsedBands.put(tier, IntRange.of(min, max));
        }

        return explicit(ruleRange, parsedBands, distribution, noPlayerBehavior);
    }

    /**
     * Builds and validates an explicit five-tier profile.
     */
    public static TierScaleProfile explicit(
        IntRange ruleRange,
        Map<WorldTier, IntRange> bands,
        String distribution,
        String noPlayerBehavior
    ) {
        Objects.requireNonNull(ruleRange, "ruleRange");
        Objects.requireNonNull(bands, "bands");
        if (bands.size() != WorldTier.values().length) {
            throw new IllegalArgumentException("Exactly five Apotheosis tier bands are required");
        }

        EnumMap<WorldTier, IntRange> copy = new EnumMap<>(WorldTier.class);
        IntRange previous = null;
        for (WorldTier tier : WorldTier.values()) {
            IntRange band = bands.get(tier);
            if (band == null) {
                throw new IllegalArgumentException("Missing tier band for " + tier.getSerializedName());
            }
            if (!ruleRange.contains(band.min()) || !ruleRange.contains(band.max())) {
                throw new IllegalArgumentException(
                    "Tier band for " + tier.getSerializedName() + " is outside the effective rule range"
                );
            }
            if (previous != null && band.min() <= previous.max()) {
                throw new IllegalArgumentException("Tier bands must be ordered and non-overlapping");
            }
            copy.put(tier, band);
            previous = band;
        }

        return new TierScaleProfile(
            ruleRange,
            copy,
            validateDistribution(distribution),
            validateNoPlayerBehavior(noPlayerBehavior)
        );
    }

    private static String validateDistribution(String distribution) {
        String normalized = Objects.requireNonNull(distribution, "distribution")
            .toLowerCase(Locale.ROOT);
        if (!RANDOM_PARTITION.equals(normalized)) {
            throw new IllegalArgumentException("Unsupported distribution: " + distribution);
        }
        return normalized;
    }

    private static String validateNoPlayerBehavior(String behavior) {
        String normalized = Objects.requireNonNull(behavior, "noPlayerBehavior")
            .toLowerCase(Locale.ROOT);
        if (!RULE_MIN.equals(normalized)) {
            throw new IllegalArgumentException("Unsupported no_player behavior: " + behavior);
        }
        return normalized;
    }

    private static String getString(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof String stringValue)) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        return stringValue;
    }

    private static int getRequiredInt(Map<?, ?> values, String key, WorldTier tier) {
        Object value = values.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                "Tier band " + tier.getSerializedName() + "." + key + " must be an integer"
            );
        }

        double doubleValue = number.doubleValue();
        if (!Double.isFinite(doubleValue) || doubleValue != Math.rint(doubleValue) ||
            doubleValue < Integer.MIN_VALUE || doubleValue > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                "Tier band " + tier.getSerializedName() + "." + key + " must be an integer"
            );
        }
        return (int) doubleValue;
    }

    public IntRange ruleRange() {
        return ruleRange;
    }

    public IntRange band(WorldTier tier) {
        return bands.get(Objects.requireNonNull(tier, "tier"));
    }

    public Map<WorldTier, IntRange> bands() {
        return bands;
    }

    public String distribution() {
        return distribution;
    }

    public String noPlayerBehavior() {
        return noPlayerBehavior;
    }
}
