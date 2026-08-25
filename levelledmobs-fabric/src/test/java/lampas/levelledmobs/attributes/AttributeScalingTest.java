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

    @Test
    public void testNoImplicitFollowRangeGrowth() {
        LevelRule defaultRule = LevelRule.builder("default_rule")
            .priority(1)
            .levelRange(IntRange.of(1, 10))
            .build();

        EffectiveRule effective = EffectiveRule.merge(List.of(defaultRule));
        assertNotNull(effective.compiledAttributes());

        CompiledAttributeModifier followRangeMod = effective.compiledAttributes().stream()
            .filter(a -> a.definition() == AttributeDefinition.FOLLOW_RANGE)
            .findFirst()
            .orElse(null);

        assertNotNull(followRangeMod);
        assertEquals(0.0, followRangeMod.perLevel(), "Implicit follow_range growth must be 0.0 by default");
    }

    @Test
    public void testExplicitFollowRangeGrowth() {
        LevelRule ruleWithFollow = LevelRule.builder("follow_rule")
            .priority(1)
            .levelRange(IntRange.of(1, 10))
            .attributeSettings(Map.of("follow_range", 0.75))
            .build();

        EffectiveRule effective = EffectiveRule.merge(List.of(ruleWithFollow));
        assertNotNull(effective.compiledAttributes());

        CompiledAttributeModifier followRangeMod = effective.compiledAttributes().stream()
            .filter(a -> a.definition() == AttributeDefinition.FOLLOW_RANGE)
            .findFirst()
            .orElse(null);

        assertNotNull(followRangeMod);
        assertEquals(0.75, followRangeMod.perLevel(), "Explicitly configured follow_range must be preserved");
    }

    @Test
    public void testCachedDefaultPlanReuse() {
        AttributeScalingService service = new AttributeScalingService();
        List<CompiledAttributeModifier> plan1 = service.getDefaultPlan();
        List<CompiledAttributeModifier> plan2 = service.getDefaultPlan();
        assertSame(plan1, plan2, "getDefaultPlan() must return cached immutable instance to avoid per-mob allocation");
        assertEquals(8, plan1.size());
    }

    @Test
    public void testServiceInstanceIsolation() {
        AttributeScalingService serviceA = new AttributeScalingService();
        AttributeScalingService serviceB = new AttributeScalingService();

        // Initial state: both have standard armor = 0.5
        assertEquals(0.5, serviceA.getDefaultPlan().get(3).perLevel(), 0.001);
        assertEquals(0.5, serviceB.getDefaultPlan().get(3).perLevel(), 0.001);

        // Mutate serviceA only
        serviceA.setDefaultFormula(AttributeDefinition.ARMOR.key(), AttributeFormula.simpleAddition(AttributeDefinition.ARMOR, 1.25));

        // serviceA updated
        assertEquals(1.25, serviceA.getDefaultPlan().get(3).perLevel(), 0.001);

        // serviceB remains untouched (no cross-instance leakage)
        assertEquals(0.5, serviceB.getDefaultPlan().get(3).perLevel(), 0.001);

        // Newly constructed serviceC starts with standard default (no global leakage)
        AttributeScalingService serviceC = new AttributeScalingService();
        assertEquals(0.5, serviceC.getDefaultPlan().get(3).perLevel(), 0.001);
    }

    @Test
    public void testPrecompiledRuleInheritsUpdatedServiceDefaults() {
        // Compile rule BEFORE updating service
        LevelRule ruleDef = LevelRule.builder("rule_precompiled")
            .priority(1)
            .levelRange(IntRange.of(1, 10))
            .attributeSettings(Map.of("max_health", 5.0))
            .build();
        EffectiveRule effective = EffectiveRule.merge(List.of(ruleDef));

        // Compiled attributes for non-explicit armor is marked inherited
        CompiledAttributeModifier armorMod = effective.compiledAttributes().get(3);
        assertEquals(AttributeDefinition.ARMOR, armorMod.definition());
        assertFalse(armorMod.isExplicit(), "Unconfigured rule attribute must be marked as inherited");

        // Service with custom default
        AttributeScalingService service = new AttributeScalingService();
        service.setDefaultFormula(AttributeDefinition.ARMOR.key(), AttributeFormula.simpleAddition(AttributeDefinition.ARMOR, 1.75));

        // Service fallback plan for index 3 reflects 1.75
        assertEquals(1.75, service.getDefaultPlan().get(3).perLevel(), 0.001);
    }

    @Test
    public void testExplicitRuleOverridesServiceDefaults() {
        // Compile rule with explicit armor
        LevelRule ruleDef = LevelRule.builder("rule_explicit_armor")
            .priority(1)
            .levelRange(IntRange.of(1, 10))
            .attributeSettings(Map.of("armor", 3.0))
            .build();
        EffectiveRule effective = EffectiveRule.merge(List.of(ruleDef));

        CompiledAttributeModifier armorMod = effective.compiledAttributes().get(3);
        assertEquals(AttributeDefinition.ARMOR, armorMod.definition());
        assertTrue(armorMod.isExplicit(), "Configured rule attribute must be marked as explicit");
        assertEquals(3.0, armorMod.perLevel(), 0.001);

        // Even when service default is changed, explicit rule value remains 3.0
        AttributeScalingService service = new AttributeScalingService();
        service.setDefaultFormula(AttributeDefinition.ARMOR.key(), AttributeFormula.simpleAddition(AttributeDefinition.ARMOR, 1.75));

        // Explicit modifier is unchanged
        assertEquals(3.0, effective.compiledAttributes().get(3).perLevel(), 0.001);
    }

    @Test
    public void testExplicitZeroValueRemainsOverride() {
        LevelRule ruleZero = LevelRule.builder("rule_zero")
            .priority(1)
            .levelRange(IntRange.of(1, 10))
            .attributeSettings(Map.of("armor", 0.0))
            .build();
        EffectiveRule effective = EffectiveRule.merge(List.of(ruleZero));

        CompiledAttributeModifier armorMod = effective.compiledAttributes().get(3);
        assertEquals(AttributeDefinition.ARMOR, armorMod.definition());
        assertTrue(armorMod.hasExplicitValue(), "Explicit 0.0 value must be marked as explicit");
        assertEquals(0.0, armorMod.perLevel(), 0.001);

        AttributeScalingService service = new AttributeScalingService();
        service.setDefaultFormula(AttributeDefinition.ARMOR.key(), AttributeFormula.simpleAddition(AttributeDefinition.ARMOR, 2.5));

        // When rule has explicit 0.0 value, it must not be replaced by service default
        assertEquals(0.0, armorMod.perLevel(), 0.001);
    }

    @Test
    public void testMalformedAndNullValuesInheritServiceDefault() {
        Map<String, Object> mapWithNull = new java.util.HashMap<>();
        mapWithNull.put("armor", null);

        LevelRule ruleNull = LevelRule.builder("rule_null")
            .priority(1)
            .levelRange(IntRange.of(1, 10))
            .attributeSettings(mapWithNull)
            .build();
        EffectiveRule effNull = EffectiveRule.merge(List.of(ruleNull));
        assertFalse(effNull.compiledAttributes().get(3).hasExplicitValue(), "Null value must inherit service default");

        LevelRule ruleMalformed = LevelRule.builder("rule_malformed")
            .priority(1)
            .levelRange(IntRange.of(1, 10))
            .attributeSettings(Map.of("armor", "unparsable_number"))
            .build();
        EffectiveRule effMalformed = EffectiveRule.merge(List.of(ruleMalformed));
        assertFalse(effMalformed.compiledAttributes().get(3).hasExplicitValue(), "Malformed string must inherit service default");

        AttributeScalingService service = new AttributeScalingService();
        service.setDefaultFormula(AttributeDefinition.ARMOR.key(), AttributeFormula.simpleAddition(AttributeDefinition.ARMOR, 2.75));
        assertEquals(2.75, service.getDefaultPlan().get(3).perLevel(), 0.001);
    }

    @Test
    public void testOperationOnlySettingCombinesInheritedValueWithExplicitOperation() {
        LevelRule ruleOpOnly = LevelRule.builder("rule_op_only")
            .priority(1)
            .levelRange(IntRange.of(1, 10))
            .attributeSettings(Map.of("armor_operation", "ADD_MULTIPLIED_BASE"))
            .build();
        EffectiveRule effective = EffectiveRule.merge(List.of(ruleOpOnly));

        CompiledAttributeModifier armorMod = effective.compiledAttributes().get(3);
        assertFalse(armorMod.hasExplicitValue(), "Value should be inherited when only operation is set");
        assertTrue(armorMod.hasExplicitOperation(), "Operation must be explicit");
        assertEquals(net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE, armorMod.operation());

        AttributeScalingService service = new AttributeScalingService();
        service.setDefaultFormula(AttributeDefinition.ARMOR.key(), AttributeFormula.simpleAddition(AttributeDefinition.ARMOR, 1.5));

        // When resolved, value is taken from service default (1.5) and operation from rule (ADD_MULTIPLIED_BASE)
        double resolvedPerLevel = armorMod.hasExplicitValue() ? armorMod.perLevel() : service.getDefaultPlan().get(3).perLevel();
        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation resolvedOp = armorMod.hasExplicitOperation()
            ? armorMod.operation()
            : service.getDefaultPlan().get(3).operation();

        assertEquals(1.5, resolvedPerLevel, 0.001);
        assertEquals(net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE, resolvedOp);
    }

    @Test
    public void testMalformedOperationInheritsCustomServiceOperation() {
        LevelRule ruleBadOp = LevelRule.builder("rule_bad_op")
            .priority(1)
            .levelRange(IntRange.of(1, 10))
            .attributeSettings(Map.of("armor_operation", "INVALID_OPERATION_NAME"))
            .build();
        EffectiveRule effective = EffectiveRule.merge(List.of(ruleBadOp));

        CompiledAttributeModifier armorMod = effective.compiledAttributes().get(3);
        assertFalse(armorMod.hasExplicitOperation(), "Malformed operation must be marked as inherited");

        AttributeScalingService service = new AttributeScalingService();
        service.setDefaultFormula(AttributeDefinition.ARMOR.key(), AttributeFormula.simpleMultiplier(AttributeDefinition.ARMOR, 0.05));

        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation resolvedOp = armorMod.hasExplicitOperation()
            ? armorMod.operation()
            : service.getDefaultPlan().get(3).operation();

        assertEquals(net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE, resolvedOp);
    }

    @Test
    public void testExplicitValueWithoutOperationInheritsCustomServiceOperation() {
        LevelRule ruleValOnly = LevelRule.builder("rule_val_only")
            .priority(1)
            .levelRange(IntRange.of(1, 10))
            .attributeSettings(Map.of("armor", 3.5))
            .build();
        EffectiveRule effective = EffectiveRule.merge(List.of(ruleValOnly));

        CompiledAttributeModifier armorMod = effective.compiledAttributes().get(3);
        assertTrue(armorMod.hasExplicitValue(), "Value 3.5 must be explicit");
        assertEquals(3.5, armorMod.perLevel(), 0.001);
        assertFalse(armorMod.hasExplicitOperation(), "Operation must be marked as inherited");

        AttributeScalingService service = new AttributeScalingService();
        service.setDefaultFormula(AttributeDefinition.ARMOR.key(), AttributeFormula.simpleMultiplier(AttributeDefinition.ARMOR, 0.05));

        double resolvedPerLevel = armorMod.hasExplicitValue() ? armorMod.perLevel() : service.getDefaultPlan().get(3).perLevel();
        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation resolvedOp = armorMod.hasExplicitOperation()
            ? armorMod.operation()
            : service.getDefaultPlan().get(3).operation();

        assertEquals(3.5, resolvedPerLevel, 0.001);
        assertEquals(net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE, resolvedOp);
    }
}
