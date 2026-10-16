package lampas.levelledmobs.events;

import lampas.levelledmobs.config.LevelledMobsConfig;
import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.rules.EffectiveRule;
import lampas.levelledmobs.rules.IntRange;
import lampas.levelledmobs.rules.LevelRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CombatMultiplierUnitTest {

    @BeforeEach
    public void settingsDefaults() {
        CombatHandler.setProjectileDamageMultiplier(LevelledMobsConfig.DEFAULT_PROJECTILE_DAMAGE_MULTIPLIER);
        CombatHandler.setExplosionDamageMultiplier(LevelledMobsConfig.DEFAULT_EXPLOSION_DAMAGE_MULTIPLIER);
    }

    @AfterEach
    public void restoreDefaults() {
        CombatHandler.setProjectileDamageMultiplier(LevelledMobsConfig.DEFAULT_PROJECTILE_DAMAGE_MULTIPLIER);
        CombatHandler.setExplosionDamageMultiplier(LevelledMobsConfig.DEFAULT_EXPLOSION_DAMAGE_MULTIPLIER);
    }

    @Test
    public void multiplierIsOnePlusFractionTimesLevelsAboveOne() {
        assertEquals(1.0f, CombatHandler.levelMultiplier(1, 0.05), 1e-6f);
        assertEquals(1.0f + 0.05f * 20, CombatHandler.levelMultiplier(21, 0.05), 1e-6f);
        assertEquals(2.0f, CombatHandler.levelMultiplier(21, 0.05), 1e-6f);
        assertEquals(1.0f + 0.01f * 199, CombatHandler.levelMultiplier(200, 0.01), 1e-5f);
        assertEquals(2.99f, CombatHandler.levelMultiplier(200, 0.01), 1e-5f);
        assertEquals(10.95f, CombatHandler.levelMultiplier(200, 0.05), 1e-5f);
    }

    @Test
    public void zeroFractionLeavesDamageUnchanged() {
        assertEquals(1.0f, CombatHandler.levelMultiplier(200, 0.0), 1e-6f);
    }

    @Test
    public void ruleValueOverridesSettingsDefault() {
        LevelledMobData data = LevelledMobData.of(200, "rule").withDamagePerLevel(0.02, 0.03);

        assertEquals(0.02, CombatHandler.projectileDamagePerLevel(data));
        assertEquals(0.03, CombatHandler.explosionDamagePerLevel(data));
    }

    @Test
    public void absentRuleValueFollowsTheCurrentSettingsDefault() {
        LevelledMobData data = LevelledMobData.of(200, "rule");

        assertEquals(LevelledMobsConfig.DEFAULT_PROJECTILE_DAMAGE_MULTIPLIER, CombatHandler.projectileDamagePerLevel(data));
        assertEquals(LevelledMobsConfig.DEFAULT_EXPLOSION_DAMAGE_MULTIPLIER, CombatHandler.explosionDamagePerLevel(data));

        // The same stored-nothing mob follows a changed settings default without being relevelled.
        CombatHandler.setExplosionDamageMultiplier(0.02);
        assertEquals(0.02, CombatHandler.explosionDamagePerLevel(data));
    }

    @Test
    public void explicitZeroOverrideDoesNotFallBackToTheDefault() {
        LevelledMobData data = LevelledMobData.of(200, "rule").withDamagePerLevel(0.0, 0.0);

        assertEquals(0.0, CombatHandler.explosionDamagePerLevel(data));
        assertEquals(1.0f, CombatHandler.levelMultiplier(200, CombatHandler.explosionDamagePerLevel(data)), 1e-6f);
    }

    @Test
    public void legacyDataWithoutDamageFieldsUsesTheSettingsDefaults() {
        LevelledMobData legacy = new LevelledMobData(200, true, "old_rule", 1L, 1, List.of(), false, 1.0);

        assertEquals(0.05, CombatHandler.projectileDamagePerLevel(legacy));
        assertEquals(0.01, CombatHandler.explosionDamagePerLevel(legacy));
    }

    @Test
    public void ruleAttributesCarryTheDamageFractionsWithoutBecomingEntityAttributes() {
        LevelRule rule = LevelRule.builder("pack_damage")
            .priority(10)
            .levelRange(IntRange.of(1, 200))
            .attributeSettings(Map.of("projectile_damage", 0.05, "explosion_damage", 0.01))
            .build();
        EffectiveRule effective = EffectiveRule.merge(List.of(rule));

        assertEquals(0.05, effective.projectileDamagePerLevel());
        assertEquals(0.01, effective.explosionDamagePerLevel());
        assertTrue(effective.compiledAttributes().stream()
                .map(compiled -> compiled.definition().key())
                .noneMatch(key -> key.equals("projectile_damage") || key.equals("explosion_damage")),
            "the damage fractions must not compile into entity attributes");
    }

    @Test
    public void higherPriorityRuleOverridesAndLowerRuleFillsTheGaps() {
        LevelRule high = LevelRule.builder("high")
            .priority(10)
            .levelRange(IntRange.of(1, 200))
            .attributeSettings(Map.of("explosion_damage", 0.0))
            .build();
        LevelRule low = LevelRule.builder("low")
            .priority(1)
            .levelRange(IntRange.of(1, 200))
            .attributeSettings(Map.of("projectile_damage", 0.03, "explosion_damage", 0.02))
            .build();
        EffectiveRule effective = EffectiveRule.merge(List.of(high, low));

        assertEquals(0.03, effective.projectileDamagePerLevel(), "the lower rule supplies the projectile fraction");
        assertEquals(0.0, effective.explosionDamagePerLevel(), "the higher rule overrides the explosion fraction");
    }

    @Test
    public void ruleWithoutDamageAttributesHasNoOverride() {
        LevelRule rule = LevelRule.builder("plain")
            .priority(1)
            .levelRange(IntRange.of(1, 10))
            .attributeSettings(Map.of("max_health", 2.5))
            .build();
        EffectiveRule effective = EffectiveRule.merge(List.of(rule));

        assertTrue(Double.isNaN(effective.projectileDamagePerLevel()));
        assertTrue(Double.isNaN(effective.explosionDamagePerLevel()));
    }

    @Test
    public void malformedRuleDamageValuesAreNoOverride() {
        assertTrue(Double.isNaN(EffectiveRule.damagePerLevel(Map.of("projectile_damage", "abc"), "projectile_damage")));
        assertTrue(Double.isNaN(EffectiveRule.damagePerLevel(Map.of("explosion_damage", -0.5), "explosion_damage")));
        assertTrue(Double.isNaN(EffectiveRule.damagePerLevel(Map.of("explosion_damage", Double.POSITIVE_INFINITY), "explosion_damage")));
        assertEquals(0.02, EffectiveRule.damagePerLevel(Map.of("explosion_damage", "0.02"), "explosion_damage"));
        assertEquals(0.0, EffectiveRule.damagePerLevel(Map.of("explosion_damage", 0), "explosion_damage"));
    }
}
