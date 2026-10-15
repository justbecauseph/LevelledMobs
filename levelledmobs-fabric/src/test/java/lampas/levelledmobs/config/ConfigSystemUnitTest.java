package lampas.levelledmobs.config;

import lampas.levelledmobs.config.legacy.LegacyLevelledMobsConfigImporter;
import lampas.levelledmobs.level.BossClassifier;
import lampas.levelledmobs.nametag.NametagVisibility;
import lampas.levelledmobs.rules.LevelRule;
import lampas.levelledmobs.rules.RuleManager;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
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
