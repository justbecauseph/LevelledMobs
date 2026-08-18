package lampas.levelledmobs.drops;

import lampas.levelledmobs.drops.custom.CustomDropItem;
import lampas.levelledmobs.drops.custom.CustomDropsParser;
import lampas.levelledmobs.drops.custom.SlidingChance;
import lampas.levelledmobs.rules.strategy.math.MinAndMax;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DropAndXpScalingUnitTest {

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void testXpScalingCalculations() {
        // Base XP 5, default multiplier 0.10 (+10% per level above 1)
        int level1Xp = XpScalingService.calculateXp(5, 1);
        assertEquals(5, level1Xp);

        // Level 10: 5 * (1 + 9 * 0.10) = 5 * 1.9 = 9.5 -> 10
        int level10Xp = XpScalingService.calculateXp(5, 10);
        assertEquals(10, level10Xp);

        // Custom config: 25% multiplier + 2 flat XP per level
        Map<String, Object> customConfig = Map.of(
            "xp_multiplier", 0.25,
            "xp_additive", 2.0,
            "max_xp", 1000
        );

        // Level 5: 10 * (1 + 4 * 0.25) + (4 * 2) = 10 * 2.0 + 8 = 28
        int customLevel5Xp = XpScalingService.calculateXp(10, 5, customConfig);
        assertEquals(28, customLevel5Xp);
    }

    @Test
    public void testDropScalingService() {
        // Level 1: original count
        assertEquals(2, DropScalingService.calculateDropAmount(2, 1));

        // Level 21: 2 * (1 + 20 * 0.05) = 2 * 2.0 = 4
        assertEquals(4, DropScalingService.calculateDropAmount(2, 21));
    }

    @Test
    public void testSlidingChanceCalculations() {
        SlidingChance sc = SlidingChance.scaling(0.10, 0.02); // 10% base + 2% per level

        assertEquals(0.10, sc.getChance(1), 0.001);
        assertEquals(0.18, sc.getChance(5), 0.001); // 0.10 + 4 * 0.02 = 0.18
        assertEquals(0.28, sc.getChance(10), 0.001); // 0.10 + 9 * 0.02 = 0.28

        // Tier table
        SlidingChance tierSc = new SlidingChance(0.0);
        tierSc.addTier(MinAndMax.of(1, 10), 0.05);
        tierSc.addTier(MinAndMax.of(11, 50), 0.25);

        assertEquals(0.05, tierSc.getChance(5), 0.001);
        assertEquals(0.25, tierSc.getChance(25), 0.001);
    }

    @Test
    public void testCustomDropsParserAndModdedItemSupport() {
        Map<String, Object> dropsMap = Map.of(
            "diamond_drop", Map.of(
                "item", "minecraft:diamond",
                "chance", 0.05,
                "min-level", 25,
                "amount", "1-3"
            ),
            "modded_drop", Map.of(
                "item", "betterend:crystal_shards",
                "chance", 0.50,
                "min-level", 10,
                "amount", "2-5"
            )
        );

        List<CustomDropItem> parsed = CustomDropsParser.parseDrops(dropsMap);
        assertEquals(2, parsed.size());

        CustomDropItem diamond = parsed.stream()
            .filter(d -> d.itemId().equals(Identifier.fromNamespaceAndPath("minecraft", "diamond")))
            .findFirst()
            .orElse(null);

        assertNotNull(diamond);
        assertFalse(diamond.levelRange().contains(20));
        assertTrue(diamond.levelRange().contains(25));
        assertEquals(1, diamond.amountRange().minAsInt());
        assertEquals(3, diamond.amountRange().maxAsInt());

        CustomDropItem modded = parsed.stream()
            .filter(d -> d.itemId().equals(Identifier.fromNamespaceAndPath("betterend", "crystal_shards")))
            .findFirst()
            .orElse(null);

        assertNotNull(modded);
        assertEquals("betterend", modded.itemId().getNamespace());
        assertEquals("crystal_shards", modded.itemId().getPath());
        assertEquals(2, modded.amountRange().minAsInt());
        assertEquals(5, modded.amountRange().maxAsInt());
    }
}
