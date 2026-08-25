package lampas.levelledmobs.apotheosis;

import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import lampas.levelledmobs.rules.IntRange;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TierScaleProfileTest {
    @Test
    void partitionsEvenRuleRangeAcrossEveryTier() {
        TierScaleProfile profile = TierScaleProfile.automatic(IntRange.of(1, 50));

        assertBand(profile, WorldTier.HAVEN, 1, 10);
        assertBand(profile, WorldTier.FRONTIER, 11, 20);
        assertBand(profile, WorldTier.ASCENT, 21, 30);
        assertBand(profile, WorldTier.SUMMIT, 31, 40);
        assertBand(profile, WorldTier.PINNACLE, 41, 50);
    }

    @Test
    void assignsUnevenRemainderWithoutGapsOrOverlap() {
        TierScaleProfile profile = TierScaleProfile.automatic(IntRange.of(10, 60));

        assertBand(profile, WorldTier.HAVEN, 10, 20);
        assertBand(profile, WorldTier.FRONTIER, 21, 30);
        assertBand(profile, WorldTier.ASCENT, 31, 40);
        assertBand(profile, WorldTier.SUMMIT, 41, 50);
        assertBand(profile, WorldTier.PINNACLE, 51, 60);
    }

    @Test
    void clampsLaterTiersWhenRuleRangeHasFewerThanFiveLevels() {
        TierScaleProfile profile = TierScaleProfile.automatic(IntRange.of(1, 3));

        assertBand(profile, WorldTier.HAVEN, 1, 1);
        assertBand(profile, WorldTier.FRONTIER, 2, 2);
        assertBand(profile, WorldTier.ASCENT, 3, 3);
        assertBand(profile, WorldTier.SUMMIT, 3, 3);
        assertBand(profile, WorldTier.PINNACLE, 3, 3);
    }

    @Test
    void parsesExplicitBandsFromGsonStyleNumbers() {
        Map<String, Object> config = Map.of(
            "name", ApotheosisWorldTierStrategy.NAME,
            "distribution", "random_partition",
            "no_player", "rule_min",
            "tier_bands", bands(
                band(1.0D, 5.0D),
                band(6.0D, 12.0D),
                band(13.0D, 22.0D),
                band(23.0D, 35.0D),
                band(36.0D, 50.0D)
            )
        );

        TierScaleProfile profile = TierScaleProfile.fromConfig(IntRange.of(1, 50), config);

        assertBand(profile, WorldTier.HAVEN, 1, 5);
        assertBand(profile, WorldTier.PINNACLE, 36, 50);
        assertEquals("random_partition", profile.distribution());
        assertEquals("rule_min", profile.noPlayerBehavior());
    }

    @Test
    void rejectsMissingOverlappingAndOutOfRangeBands() {
        Map<String, Object> missing = new LinkedHashMap<>(bands(
            band(1, 5), band(6, 10), band(11, 15), band(16, 20), band(21, 25)
        ));
        missing.remove("summit");
        assertThrows(IllegalArgumentException.class, () -> TierScaleProfile.fromConfig(
            IntRange.of(1, 25), Map.of("tier_bands", missing)
        ));

        Map<String, Object> overlapping = bands(
            band(1, 5), band(5, 10), band(11, 15), band(16, 20), band(21, 25)
        );
        assertThrows(IllegalArgumentException.class, () -> TierScaleProfile.fromConfig(
            IntRange.of(1, 25), Map.of("tier_bands", overlapping)
        ));

        Map<String, Object> outside = bands(
            band(0, 5), band(6, 10), band(11, 15), band(16, 20), band(21, 25)
        );
        assertThrows(IllegalArgumentException.class, () -> TierScaleProfile.fromConfig(
            IntRange.of(1, 25), Map.of("tier_bands", outside)
        ));

        Map<String, Object> inverted = bands(
            band(5, 1), band(6, 10), band(11, 15), band(16, 20), band(21, 25)
        );
        assertThrows(IllegalArgumentException.class, () -> TierScaleProfile.fromConfig(
            IntRange.of(1, 25), Map.of("tier_bands", inverted)
        ));

        Map<String, Object> unknown = new LinkedHashMap<>(bands(
            band(1, 5), band(6, 10), band(11, 15), band(16, 20), band(21, 25)
        ));
        unknown.put("apotheosis", band(26, 30));
        assertThrows(IllegalArgumentException.class, () -> TierScaleProfile.fromConfig(
            IntRange.of(1, 30), Map.of("tier_bands", unknown)
        ));
    }

    @Test
    void rejectsFractionalValuesAndUnsupportedOptions() {
        Map<String, Object> fractional = bands(
            band(1.5D, 5), band(6, 10), band(11, 15), band(16, 20), band(21, 25)
        );
        assertThrows(IllegalArgumentException.class, () -> TierScaleProfile.fromConfig(
            IntRange.of(1, 25), Map.of("tier_bands", fractional)
        ));
        assertThrows(IllegalArgumentException.class, () -> TierScaleProfile.fromConfig(
            IntRange.of(1, 25), Map.of("distribution", "fixed")
        ));
        assertThrows(IllegalArgumentException.class, () -> TierScaleProfile.fromConfig(
            IntRange.of(1, 25), Map.of("no_player", "defer")
        ));
    }

    @Test
    void selectedLevelsStayInsideInclusiveBand() {
        IntRange band = IntRange.of(11, 20);
        RandomSource random = RandomSource.create(12345L);

        for (int i = 0; i < 1_000; i++) {
            assertTrue(band.contains(ApotheosisWorldTierStrategy.chooseLevel(random, band)));
        }
        assertEquals(7, ApotheosisWorldTierStrategy.chooseLevel(random, IntRange.single(7)));
    }

    private static void assertBand(TierScaleProfile profile, WorldTier tier, int min, int max) {
        assertEquals(IntRange.of(min, max), profile.band(tier));
    }

    private static Map<String, Object> bands(
        Map<String, Object> haven,
        Map<String, Object> frontier,
        Map<String, Object> ascent,
        Map<String, Object> summit,
        Map<String, Object> pinnacle
    ) {
        Map<String, Object> bands = new LinkedHashMap<>();
        bands.put("haven", haven);
        bands.put("frontier", frontier);
        bands.put("ascent", ascent);
        bands.put("summit", summit);
        bands.put("pinnacle", pinnacle);
        return bands;
    }

    private static Map<String, Object> band(Number min, Number max) {
        return Map.of("min", min, "max", max);
    }
}
