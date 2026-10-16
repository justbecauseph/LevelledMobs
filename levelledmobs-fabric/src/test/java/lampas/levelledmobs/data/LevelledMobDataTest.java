package lampas.levelledmobs.data;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LevelledMobDataTest {

    @Test
    public void testEmptyData() {
        LevelledMobData empty = LevelledMobData.EMPTY;
        assertNotNull(empty);
        assertEquals(0, empty.level());
        assertFalse(empty.levelled());
        assertEquals("none", empty.ruleSet());
        assertEquals(0L, empty.generatedAt());
    }

    @Test
    public void testOfFactory() {
        long before = System.currentTimeMillis();
        LevelledMobData data = LevelledMobData.of(5, "overworld_hostiles");
        long after = System.currentTimeMillis();

        assertEquals(5, data.level());
        assertTrue(data.levelled());
        assertEquals("overworld_hostiles", data.ruleSet());
        assertTrue(data.generatedAt() >= before && data.generatedAt() <= after);
    }

    @Test
    public void testHolderDefaultMethods() {
        LevelledMobHolder holderWithNull = new LevelledMobHolder() {
            @Override
            public LevelledMobData lampas$getLevelData() {
                return null;
            }

            @Override
            public void lampas$setLevelData(LevelledMobData data) {}
        };

        assertFalse(holderWithNull.lampas$isLevelled());
        assertEquals(0, holderWithNull.lampas$getLevel());

        LevelledMobHolder holderWithData = new LevelledMobHolder() {
            private LevelledMobData data = LevelledMobData.of(7, "custom");

            @Override
            public LevelledMobData lampas$getLevelData() {
                return data;
            }

            @Override
            public void lampas$setLevelData(LevelledMobData data) {
                this.data = data;
            }
        };

        assertTrue(holderWithData.lampas$isLevelled());
        assertEquals(7, holderWithData.lampas$getLevel());
    }

    @Test
    public void testEffectiveModifierRetentionIsVersionedAndImmutable() {
        List<LevelledMobModifier> modifiers = List.of(
            new LevelledMobModifier("max_health", "lampas:levelled/max_health", 45.0, "add_value")
        );
        LevelledMobData data = LevelledMobData.of(6, "custom_rule").withEffectiveModifiers(modifiers);

        assertEquals(LevelledMobData.CURRENT_RETENTION_VERSION, data.retentionVersion());
        assertTrue(data.hasPersistedModifiers());
        assertThrows(UnsupportedOperationException.class, () -> data.effectiveModifiers().add(
            new LevelledMobModifier("armor", "lampas:levelled/armor", 2.5, "add_value")
        ));
    }

    @Test
    public void testEffectiveModifierRetentionHasHardBound() {
        List<LevelledMobModifier> oversized = new ArrayList<>();
        for (int i = 0; i < LevelledMobData.MAX_PERSISTED_MODIFIERS + 10; i++) {
            oversized.add(new LevelledMobModifier("unknown_" + i, "lampas:unknown/" + i, i, "add_value"));
        }

        LevelledMobData data = new LevelledMobData(5, true, "legacy", 1L,
            LevelledMobData.CURRENT_RETENTION_VERSION, oversized);
        assertEquals(LevelledMobData.MAX_PERSISTED_MODIFIERS, data.effectiveModifiers().size());
    }

    @Test
    public void groupFactorDefaultsToOneAndSurvivesEveryCopy() {
        assertEquals(1.0, LevelledMobData.EMPTY.groupFactor());
        assertEquals(1.0, LevelledMobData.of(5, "rule").groupFactor());
        assertEquals(1.0, new LevelledMobData(5, true, "rule", 1L, 0, List.of(), false).groupFactor());

        LevelledMobData boss = LevelledMobData.of(20, "boss").withGroupFactor(1.5);
        List<LevelledMobModifier> plan = List.of(new LevelledMobModifier("max_health", "lampas:levelled/max_health", 1.0, "add_value"));
        assertEquals(1.5, boss.groupFactor());
        assertEquals(1.5, boss.withEffectiveModifiers(plan).groupFactor());
        assertEquals(1.5, boss.withRelevel(30, plan).groupFactor());
        assertEquals(1.5, boss.withQuarantined(true).groupFactor());
    }

    @Test
    public void malformedGroupFactorFallsBackToOne() {
        assertEquals(1.0, LevelledMobData.of(5, "rule").withGroupFactor(Double.NaN).groupFactor());
        assertEquals(1.0, LevelledMobData.of(5, "rule").withGroupFactor(Double.POSITIVE_INFINITY).groupFactor());
        assertEquals(1.0, LevelledMobData.of(5, "rule").withGroupFactor(0.5).groupFactor());
        assertEquals(1.0, LevelledMobData.of(5, "rule").withGroupFactor(-2.0).groupFactor());
    }

    @Test
    public void testLegacyMetadataDoesNotClaimEffectivePlan() {
        LevelledMobData legacy = new LevelledMobData(27, true, "old_rule", 1L);

        assertFalse(legacy.hasPersistedModifiers());
        assertEquals(0, legacy.retentionVersion());
        assertTrue(legacy.effectiveModifiers().isEmpty());
    }

    @Test
    public void damagePerLevelDefaultsToNoOverrideAndSurvivesEveryCopy() {
        assertTrue(Double.isNaN(LevelledMobData.EMPTY.projectileDamagePerLevel()));
        assertTrue(Double.isNaN(LevelledMobData.of(5, "rule").explosionDamagePerLevel()));

        // Zero is a real override that turns the damage off, so it must not read as "no override".
        LevelledMobData set = LevelledMobData.of(20, "rule").withDamagePerLevel(0.05, 0.0);
        assertEquals(0.05, set.projectileDamagePerLevel());
        assertEquals(0.0, set.explosionDamagePerLevel());

        List<LevelledMobModifier> plan = List.of(new LevelledMobModifier("max_health", "lampas:levelled/max_health", 1.0, "add_value"));
        assertEquals(0.05, set.withEffectiveModifiers(plan).projectileDamagePerLevel());
        assertEquals(0.0, set.withRelevel(30, plan).explosionDamagePerLevel());
        assertEquals(0.05, set.withQuarantined(true).projectileDamagePerLevel());
        assertEquals(0.0, set.withGroupFactor(1.5).explosionDamagePerLevel());
        assertEquals(1.5, set.withGroupFactor(1.5).groupFactor());
    }

    @Test
    public void malformedDamagePerLevelFallsBackToNoOverride() {
        LevelledMobData data = LevelledMobData.of(5, "rule").withDamagePerLevel(-0.01, Double.POSITIVE_INFINITY);
        assertTrue(Double.isNaN(data.projectileDamagePerLevel()));
        assertTrue(Double.isNaN(data.explosionDamagePerLevel()));

        LevelledMobData nan = LevelledMobData.of(5, "rule").withDamagePerLevel(Double.NaN, 0.02);
        assertTrue(Double.isNaN(nan.projectileDamagePerLevel()));
        assertEquals(0.02, nan.explosionDamagePerLevel());
    }

    @Test
    public void legacyDataWithoutDamageFieldsHasNoOverride() {
        // Saved before the damage fields existed: the 8-argument constructor carries only the group factor.
        LevelledMobData legacy = new LevelledMobData(200, true, "old_rule", 1L, 1, List.of(), false, 1.5);

        assertTrue(Double.isNaN(legacy.projectileDamagePerLevel()));
        assertTrue(Double.isNaN(legacy.explosionDamagePerLevel()));
        assertEquals(1.5, legacy.groupFactor());
    }
}
