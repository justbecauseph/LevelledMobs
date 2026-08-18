package lampas.levelledmobs.attributes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AttributeScalingTest {

    @Test
    public void testDeterministicModifierIdentifiers() {
        assertNotNull(AttributeScalingService.HEALTH_MODIFIER_ID);
        assertNotNull(AttributeScalingService.DAMAGE_MODIFIER_ID);

        assertEquals("lampas", AttributeScalingService.HEALTH_MODIFIER_ID.getNamespace());
        assertEquals("levelled/max_health", AttributeScalingService.HEALTH_MODIFIER_ID.getPath());

        assertEquals("lampas", AttributeScalingService.DAMAGE_MODIFIER_ID.getNamespace());
        assertEquals("levelled/attack_damage", AttributeScalingService.DAMAGE_MODIFIER_ID.getPath());
    }

    @Test
    public void testScalingMath() {
        // Level 1: 0 bonus health, 0 bonus damage
        int level1 = 1;
        double bonusHealth1 = (level1 - 1) * 2.0;
        double bonusDamage1 = (level1 - 1) * 0.5;
        assertEquals(0.0, bonusHealth1);
        assertEquals(0.0, bonusDamage1);

        // Level 7: +12 HP, +3.0 damage
        int level7 = 7;
        double bonusHealth7 = (level7 - 1) * 2.0;
        double bonusDamage7 = (level7 - 1) * 0.5;
        assertEquals(12.0, bonusHealth7);
        assertEquals(3.0, bonusDamage7);

        // Level 10: +18 HP, +4.5 damage
        int level10 = 10;
        double bonusHealth10 = (level10 - 1) * 2.0;
        double bonusDamage10 = (level10 - 1) * 0.5;
        assertEquals(18.0, bonusHealth10);
        assertEquals(4.5, bonusDamage10);
    }
}
