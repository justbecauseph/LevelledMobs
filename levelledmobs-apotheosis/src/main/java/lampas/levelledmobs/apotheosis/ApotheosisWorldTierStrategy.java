package lampas.levelledmobs.apotheosis;

import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import lampas.levelledmobs.context.MobContext;
import lampas.levelledmobs.rules.EffectiveRule;
import lampas.levelledmobs.rules.IntRange;
import lampas.levelledmobs.rules.strategy.LevelStrategy;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Assigns a persistent LevelledMobs level from the nearest player's Apotheosis World Tier.
 */
public final class ApotheosisWorldTierStrategy implements LevelStrategy {
    public static final ApotheosisWorldTierStrategy INSTANCE = new ApotheosisWorldTierStrategy();
    public static final String NAME = "APOTHEOSIS_WORLD_TIER";

    private final Map<ProfileKey, ProfileResult> profiles = new ConcurrentHashMap<>();
    private final Set<ProfileKey> warnedProfiles = ConcurrentHashMap.newKeySet();

    private ApotheosisWorldTierStrategy() {}

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int calculateLevel(MobContext context, EffectiveRule rule) {
        if (rule == null) {
            return 1;
        }

        IntRange ruleRange = rule.levelRange();
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

        ProfileKey key = new ProfileKey(
            rule.primaryRuleId(),
            ruleRange,
            rule.strategyConfig().hashCode()
        );
        ProfileResult result = profiles.computeIfAbsent(key, ignored -> parseProfile(rule));
        if (result.profile() == null) {
            if (warnedProfiles.add(key)) {
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

    private record ProfileKey(String ruleId, IntRange range, int configHash) {}

    private record ProfileResult(TierScaleProfile profile, String error) {}
}
