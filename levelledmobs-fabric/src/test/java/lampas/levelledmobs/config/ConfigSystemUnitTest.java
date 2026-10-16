package lampas.levelledmobs.config;

import lampas.levelledmobs.config.legacy.LegacyLevelledMobsConfigImporter;
import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.drops.DropScalingService;
import lampas.levelledmobs.drops.XpScalingService;
import lampas.levelledmobs.events.CombatHandler;
import lampas.levelledmobs.level.BossClassifier;
import lampas.levelledmobs.nametag.NametagVisibility;
import lampas.levelledmobs.rules.LevelRule;
import lampas.levelledmobs.rules.RuleManager;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigSystemUnitTest {

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void testConfigLoaderGeneratesDefaultsAndLoads(@TempDir Path tempDir) {
        ConfigLoader loader = new ConfigLoader(tempDir);
        RuleManager ruleManager = new RuleManager();

        loader.load(ruleManager, null, null);

        assertNotNull(loader.getCurrentConfig());
        assertEquals(50, loader.getCurrentConfig().maxMobsPerTick());
        assertEquals(NametagVisibility.HOVER_ONLY, loader.getCurrentConfig().nametagVisibility());
        assertEquals(0.10, loader.getCurrentConfig().defaultXpMultiplier(), 0.001);

        assertTrue(tempDir.resolve("settings.json").toFile().exists());
        assertTrue(tempDir.resolve("rules.json").toFile().exists());
    }

    @Test
    public void allowBossLevelingReachesTheBossClassifierOnLoadAndReload(@TempDir Path tempDir) throws Exception {
        ConfigLoader loader = new ConfigLoader(tempDir);
        BossClassifier classifier = new BossClassifier();
        Path settings = tempDir.resolve("settings.json");

        // First load writes the defaults, which keep bosses unlevelled.
        loader.load(new RuleManager(), null, null, classifier);
        assertFalse(classifier.isBossLevelingAllowed());

        Files.writeString(settings, Files.readString(settings)
            .replace("\"allowBossLeveling\": false", "\"allowBossLeveling\": true"));
        loader.load(new RuleManager(), null, null, classifier);
        assertTrue(loader.getCurrentConfig().allowBossLeveling());
        assertTrue(classifier.isBossLevelingAllowed(), "a load must pass allowBossLeveling to the classifier");

        Files.writeString(settings, Files.readString(settings)
            .replace("\"allowBossLeveling\": true", "\"allowBossLeveling\": false"));
        loader.load(new RuleManager(), null, null, classifier);
        assertFalse(classifier.isBossLevelingAllowed(), "a reload must pass allowBossLeveling to the classifier");
    }

    @AfterEach
    public void restoreGlobalDefaults() {
        // A load pushes every settings value into static state, so each test puts the defaults back.
        CombatHandler.setProjectileDamageMultiplier(LevelledMobsConfig.DEFAULT_PROJECTILE_DAMAGE_MULTIPLIER);
        CombatHandler.setExplosionDamageMultiplier(LevelledMobsConfig.DEFAULT_EXPLOSION_DAMAGE_MULTIPLIER);
        XpScalingService.setMultiplierPerLevel(LevelledMobsConfig.DEFAULT.defaultXpMultiplier());
        DropScalingService.setItemDropMultiplierPerLevel(LevelledMobsConfig.DEFAULT.defaultDropMultiplier());
    }

    /** A complete settings.json: every key but the damage multipliers at its default, then the extra fields. */
    private static String settingsWith(int maxMobsPerTick, String extraFields) {
        return """
            {
              "maxMobsPerTick": %d,
              "nametagTemplate": "<gray>Lv. <yellow><level></yellow> <white><mob_name></white>",
              "nametagVisibility": "HOVER_ONLY",
              "allowBossLeveling": false,
              "debug": false,
              "defaultXpMultiplier": 0.10,
              "defaultDropMultiplier": 0.05%s
            }
            """.formatted(maxMobsPerTick, extraFields);
    }

    @Test
    public void damageMultipliersFallBackToTheirDefaultsWhenTheKeysAreMissing(@TempDir Path tempDir) throws Exception {
        ConfigLoader loader = new ConfigLoader(tempDir);
        Files.writeString(tempDir.resolve("settings.json"), settingsWith(50, ""));

        loader.load(new RuleManager(), null, null, null);

        assertEquals(0.05, loader.getCurrentConfig().defaultProjectileDamageMultiplier());
        assertEquals(0.01, loader.getCurrentConfig().defaultExplosionDamageMultiplier());
        assertEquals(0.05, CombatHandler.projectileDamagePerLevel(LevelledMobData.EMPTY),
            "a missing projectile key must reach the combat handler as the default");
        assertEquals(0.01, CombatHandler.explosionDamagePerLevel(LevelledMobData.EMPTY),
            "a missing explosion key must reach the combat handler as the default");
    }

    @Test
    public void configuredDamageMultipliersReachCombatHandlerOnLoadAndReload(@TempDir Path tempDir) throws Exception {
        ConfigLoader loader = new ConfigLoader(tempDir);
        Path settings = tempDir.resolve("settings.json");
        Files.writeString(settings, settingsWith(50,
            ", \"defaultProjectileDamageMultiplier\": 0.07, \"defaultExplosionDamageMultiplier\": 0.02"));

        loader.load(new RuleManager(), null, null, null);
        assertEquals(0.07, CombatHandler.projectileDamagePerLevel(LevelledMobData.EMPTY));
        assertEquals(0.02, CombatHandler.explosionDamagePerLevel(LevelledMobData.EMPTY));

        Files.writeString(settings, settingsWith(50,
            ", \"defaultProjectileDamageMultiplier\": 0.03, \"defaultExplosionDamageMultiplier\": 0.0"));
        loader.load(new RuleManager(), null, null, null);
        assertEquals(0.03, CombatHandler.projectileDamagePerLevel(LevelledMobData.EMPTY), "a reload must reach the combat handler");
        assertEquals(0.0, CombatHandler.explosionDamagePerLevel(LevelledMobData.EMPTY), "a reload may set explosions to zero");
    }

    @Test
    public void malformedDamageMultiplierFallsBackWithoutResettingOtherSettings(@TempDir Path tempDir) throws Exception {
        ConfigLoader loader = new ConfigLoader(tempDir);
        Files.writeString(tempDir.resolve("settings.json"), settingsWith(75,
            ", \"defaultProjectileDamageMultiplier\": \"lots\", \"defaultExplosionDamageMultiplier\": -1"));

        loader.load(new RuleManager(), null, null, null);

        assertEquals(LevelledMobsConfig.DEFAULT_PROJECTILE_DAMAGE_MULTIPLIER,
            loader.getCurrentConfig().defaultProjectileDamageMultiplier());
        assertEquals(LevelledMobsConfig.DEFAULT_EXPLOSION_DAMAGE_MULTIPLIER,
            loader.getCurrentConfig().defaultExplosionDamageMultiplier());
        assertEquals(75, loader.getCurrentConfig().maxMobsPerTick(), "the other keys are still read");
        assertEquals(LevelledMobsConfig.DEFAULT_PROJECTILE_DAMAGE_MULTIPLIER,
            CombatHandler.projectileDamagePerLevel(LevelledMobData.EMPTY));
    }

    @Test
    public void testLegacyConfigImporter() {
        String sampleLegacyYaml = """
            default_monsters:
              priority: 5
              strategy: RANDOM
              min-level: 5
              max-level: 50
              entities: [zombie, skeleton, spider]
            
            end_mobs:
              priority: 10
              strategy: DISTANCE
              min-level: 20
              max-level: 100
              entities: [enderman]
            """;

        List<LevelRule> imported = LegacyLevelledMobsConfigImporter.importYaml(sampleLegacyYaml);
        assertEquals(2, imported.size());

        LevelRule defaultRule = imported.stream()
            .filter(r -> r.id().equals("default_monsters"))
            .findFirst()
            .orElse(null);

        assertNotNull(defaultRule);
        assertEquals(5, defaultRule.priority());
        assertEquals("RANDOM", defaultRule.strategyName());
        assertEquals(5, defaultRule.levelRange().min());
        assertEquals(50, defaultRule.levelRange().max());

        LevelRule endRule = imported.stream()
            .filter(r -> r.id().equals("end_mobs"))
            .findFirst()
            .orElse(null);

        assertNotNull(endRule);
        assertEquals(10, endRule.priority());
        assertEquals("DISTANCE", endRule.strategyName());
        assertEquals(20, endRule.levelRange().min());
        assertEquals(100, endRule.levelRange().max());
    }
}
