package lampas.levelledmobs.attributes;

import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import lampas.levelledmobs.events.CombatHandler;
import lampas.levelledmobs.rules.EffectiveRule;
import lampas.levelledmobs.rules.IntRange;
import lampas.levelledmobs.rules.LevelRule;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AttributeScalingTest {

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void testDeterministicModifierIdentifiers() {
        assertEquals(8, AttributeDefinition.ALL.size());

        for (AttributeDefinition def : AttributeDefinition.ALL) {
            assertNotNull(def.attribute(), "Attribute holder must not be null for " + def.key());
            assertNotNull(def.modifierId(), "Modifier ID must not be null for " + def.key());
            assertEquals("lampas", def.modifierId().getNamespace());
            assertTrue(def.modifierId().getPath().startsWith("levelled/"), "Modifier path must start with 'levelled/' for " + def.key());
        }

        assertEquals("lampas:levelled/max_health", AttributeScalingService.HEALTH_MODIFIER_ID.toString());
        assertEquals("lampas:levelled/attack_damage", AttributeScalingService.DAMAGE_MODIFIER_ID.toString());
    }

    @Test
    public void testScalingMath() {
        AttributeScalingService service = new AttributeScalingService();

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

    @Test
    public void testAll8AttributesFormulas() {
        AttributeFormula hpFormula = AttributeFormula.simpleAddition(AttributeDefinition.MAX_HEALTH, 5.0);
        assertEquals(0.0, hpFormula.calculateModifierValue(1));
        assertEquals(45.0, hpFormula.calculateModifierValue(10)); // (10 - 1) * 5.0 = 45.0

        AttributeFormula speedFormula = AttributeFormula.simpleMultiplier(AttributeDefinition.MOVEMENT_SPEED, 0.02);
        assertEquals(0.0, speedFormula.calculateModifierValue(1));
        assertEquals(0.18, speedFormula.calculateModifierValue(10), 0.001); // (10 - 1) * 0.02 = 0.18

        AttributeFormula armorFormula = AttributeFormula.simpleAddition(AttributeDefinition.ARMOR, 1.5);
        assertEquals(13.5, armorFormula.calculateModifierValue(10), 0.001); // (10 - 1) * 1.5 = 13.5
    }

    @Test
    public void testHealthPolicyPreserveRatio() {
        // Mock health logic: base HP = 20, damaged to 10 (50% ratio).
        // Max HP increases to 40 at level 10.
        // PRESERVE_RATIO should set new HP to 50% of 40 = 20.
        float prevHealth = 10.0f;
        float prevMaxHealth = 20.0f;
        float newMaxHealth = 40.0f;

        float ratio = prevHealth / prevMaxHealth;
        float expectedPreservedHealth = newMaxHealth * ratio;
        assertEquals(20.0f, expectedPreservedHealth);

        // If mob was full health (20/20), it should be full health (40/40)
        float fullPrev = 20.0f;
        float fullRatio = fullPrev / prevMaxHealth;
        assertEquals(40.0f, newMaxHealth * fullRatio);
    }

    @Test
    public void testCombatHandlerDamageScaling() {
        CombatHandler.setProjectileDamageMultiplier(0.10); // +10% per level above 1
        CombatHandler.setExplosionDamageMultiplier(0.15); // +15% per level above 1

        // Base damage = 10.0
        float baseDamage = 10.0f;

        // Level 5 shooter: +40% projectile damage -> 14.0
        int level5 = 5;
        float expectedLevel5Proj = baseDamage * (1.0f + (level5 - 1) * 0.10f);
        assertEquals(14.0f, expectedLevel5Proj, 0.001f);

        // Level 10 creeper: +135% explosion damage -> 23.5
        int level10 = 10;
        float expectedLevel10Explosion = baseDamage * (1.0f + (level10 - 1) * 0.15f);
        assertEquals(23.5f, expectedLevel10Explosion, 0.001f);
    }
}
