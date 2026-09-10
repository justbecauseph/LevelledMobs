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
    public void testLegacyMetadataDoesNotClaimEffectivePlan() {
        LevelledMobData legacy = new LevelledMobData(27, true, "old_rule", 1L);

        assertFalse(legacy.hasPersistedModifiers());
        assertEquals(0, legacy.retentionVersion());
        assertTrue(legacy.effectiveModifiers().isEmpty());
    }
}
