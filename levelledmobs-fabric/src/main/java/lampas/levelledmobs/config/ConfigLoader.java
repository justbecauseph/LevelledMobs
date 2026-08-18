package lampas.levelledmobs.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lampas.levelledmobs.drops.DropScalingService;
import lampas.levelledmobs.drops.XpScalingService;
import lampas.levelledmobs.level.MobLevelingService;
import lampas.levelledmobs.level.MobProcessingQueue;
import lampas.levelledmobs.nametag.NametagService;
import lampas.levelledmobs.rules.IntRange;
import lampas.levelledmobs.rules.LevelRule;
import lampas.levelledmobs.rules.RuleManager;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads, writes, and validates LevelledMobs configuration files.
 */
public class ConfigLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger("LevelledMobs");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configDir;
    private LevelledMobsConfig currentConfig = LevelledMobsConfig.DEFAULT;

    public ConfigLoader() {
        Path base = FabricLoader.getInstance().getConfigDir();
        this.configDir = base.resolve("lampas").resolve("levelledmobs");
    }

    public ConfigLoader(Path customConfigDir) {
        this.configDir = customConfigDir;
    }

    /**
     * Initializes configuration directory, generates defaults if missing, and loads settings.
     */
    public void load(RuleManager ruleManager, NametagService nametagService, MobProcessingQueue queue) {
        try {
            File dir = configDir.toFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File settingsFile = configDir.resolve("settings.json").toFile();
            if (!settingsFile.exists()) {
                writeDefaultSettings(settingsFile);
            } else {
                readSettings(settingsFile);
            }

            File rulesFile = configDir.resolve("rules.json").toFile();
            if (!rulesFile.exists()) {
                writeDefaultRules(rulesFile);
            }

            // Apply loaded settings
            if (queue != null) {
                queue.setMaxMobsPerTick(currentConfig.maxMobsPerTick());
            }
            if (nametagService != null) {
                nametagService.setTemplate(currentConfig.nametagTemplate());
                nametagService.setVisibility(currentConfig.nametagVisibility());
            }
            XpScalingService.setMultiplierPerLevel(currentConfig.defaultXpMultiplier());
            DropScalingService.setItemDropMultiplierPerLevel(currentConfig.defaultDropMultiplier());

            // Load rules
            List<LevelRule> loadedRules = loadRules(rulesFile);
            if (ruleManager != null && !loadedRules.isEmpty()) {
                ruleManager.setRules(loadedRules);
            }

            LOGGER.info("LevelledMobs configuration loaded successfully from {}", configDir);
        } catch (Exception e) {
            LOGGER.error("Failed to load LevelledMobs configuration; using defaults.", e);
        }
    }

    private void writeDefaultSettings(File file) {
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(LevelledMobsConfig.DEFAULT, writer);
        } catch (Exception e) {
            LOGGER.error("Failed to write default settings.json", e);
        }
    }

    private void readSettings(File file) {
        try (FileReader reader = new FileReader(file)) {
            LevelledMobsConfig loaded = GSON.fromJson(reader, LevelledMobsConfig.class);
            if (loaded != null) {
                this.currentConfig = loaded;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read settings.json, falling back to defaults", e);
        }
    }

    private void writeDefaultRules(File file) {
        try (FileWriter writer = new FileWriter(file)) {
            JsonObject root = new JsonObject();
            JsonObject defaultRule = new JsonObject();
            defaultRule.addProperty("priority", 1);
            defaultRule.addProperty("strategy", "RANDOM");
            defaultRule.addProperty("min_level", 1);
            defaultRule.addProperty("max_level", 25);
            root.add("default_monsters", defaultRule);

            GSON.toJson(root, writer);
        } catch (Exception e) {
            LOGGER.error("Failed to write default rules.json", e);
        }
    }

    public List<LevelRule> loadRules(File file) {
        List<LevelRule> rules = new ArrayList<>();
        if (!file.exists()) {
            return rules;
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            for (String key : json.keySet()) {
                if (json.get(key).isJsonObject()) {
                    JsonObject def = json.getAsJsonObject(key);
                    int priority = def.has("priority") ? def.get("priority").getAsInt() : 1;
                    String strategy = def.has("strategy") ? def.get("strategy").getAsString() : "RANDOM";
                    int min = def.has("min_level") ? def.get("min_level").getAsInt() : 1;
                    int max = def.has("max_level") ? def.get("max_level").getAsInt() : 25;

                    LevelRule rule = LevelRule.builder(key)
                        .priority(priority)
                        .levelRange(IntRange.of(min, max))
                        .strategy(strategy, null)
                        .build();

                    rules.add(rule);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse rules file {}", file.getName(), e);
        }

        return rules;
    }

    public LevelledMobsConfig getCurrentConfig() {
        return currentConfig;
    }

    public Path getConfigDir() {
        return configDir;
    }
}
