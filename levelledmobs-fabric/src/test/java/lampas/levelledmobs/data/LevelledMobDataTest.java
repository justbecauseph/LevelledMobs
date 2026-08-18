package lampas.levelledmobs.data;

import org.junit.jupiter.api.Test;

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
}
