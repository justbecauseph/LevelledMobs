package lampas.levelledmobs.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lampas.levelledmobs.drops.DropScalingService;
import lampas.levelledmobs.drops.XpScalingService;
import lampas.levelledmobs.level.MobProcessingQueue;
import lampas.levelledmobs.nametag.NametagService;
import lampas.levelledmobs.rules.LevelRule;
import lampas.levelledmobs.rules.RuleManager;
import lampas.levelledmobs.rules.RuleParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.*;

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

            File dropsFile = configDir.resolve("drops.json").toFile();
            if (!dropsFile.exists()) {
                writeDefaultDrops(dropsFile);
            }

            File messagesFile = configDir.resolve("messages.json").toFile();
            if (!messagesFile.exists()) {
                writeDefaultMessages(messagesFile);
            }

            // Apply loaded settings
            if (queue != null) {
                queue.setMaxMobsPerTick(currentConfig.maxMobsPerTick());
                queue.setMaxProcessTimeMs(currentConfig.effectiveMaxProcessTimeMs());
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
            Map<String, Object> root = new LinkedHashMap<>();

            // 1. Default Overworld Rule (Silver Challenge + Distance Scaling)
            Map<String, Object> overworld = new LinkedHashMap<>();
            overworld.put("priority", 1);
            overworld.put("strategy", "SPAWN_DISTANCE");
            overworld.put("min_level", 1);
            overworld.put("max_level", 50);
            overworld.put("conditions", Map.of(
                "dimensions", Map.of("include", List.of("minecraft:overworld"))
            ));
            overworld.put("attributes", Map.of(
                "max_health", 2.5,
                "attack_damage", 0.25,
                "movement_speed", 0.001
            ));
            overworld.put("xp", Map.of("multiplier", 0.05));
            overworld.put("drops", Map.of("item_multiplier", 0.03));
            root.put("default_overworld", overworld);

            // 2. Nether Rule (Y-Coordinate / Depth Strategy)
            Map<String, Object> nether = new LinkedHashMap<>();
            nether.put("priority", 10);
            nether.put("strategy", "Y_DISTANCE");
            nether.put("min_level", 10);
            nether.put("max_level", 60);
            nether.put("conditions", Map.of(
                "dimensions", Map.of("include", List.of("minecraft:the_nether"))
            ));
            nether.put("attributes", Map.of(
                "max_health", 5.0,
                "attack_damage", 0.40,
                "movement_speed", 0.002
            ));
            nether.put("xp", Map.of("multiplier", 0.08));
            nether.put("drops", Map.of("item_multiplier", 0.05));
            root.put("nether_scaling", nether);

            // 3. The End Rule (High Tier Distance Strategy)
            Map<String, Object> theEnd = new LinkedHashMap<>();
            theEnd.put("priority", 20);
            theEnd.put("strategy", "SPAWN_DISTANCE");
            theEnd.put("min_level", 25);
            theEnd.put("max_level", 100);
            theEnd.put("conditions", Map.of(
                "dimensions", Map.of("include", List.of("minecraft:the_end"))
            ));
            theEnd.put("attributes", Map.of(
                "max_health", 7.5,
                "attack_damage", 0.60,
                "movement_speed", 0.003
            ));
            theEnd.put("xp", Map.of("multiplier", 0.10));
            theEnd.put("drops", Map.of("item_multiplier", 0.07));
            root.put("the_end_scaling", theEnd);

            // 4. Passive Mobs (Vanilla stats)
            Map<String, Object> passive = new LinkedHashMap<>();
            passive.put("priority", 50);
            passive.put("strategy", "RANDOM");
            passive.put("min_level", 1);
            passive.put("max_level", 5);
            passive.put("conditions", Map.of(
                "entities", Map.of("include", List.of("minecraft:cow", "minecraft:pig", "minecraft:sheep", "minecraft:chicken", "minecraft:horse"))
            ));
            passive.put("attributes", Map.of(
                "max_health", 0.0,
                "attack_damage", 0.0
            ));
            root.put("passive_mobs_rule", passive);

            GSON.toJson(root, writer);
        } catch (Exception e) {
            LOGGER.error("Failed to write default rules.json", e);
        }
    }

    private void writeDefaultDrops(File file) {
        try (FileWriter writer = new FileWriter(file)) {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("chance_multiplier_per_level", 0.02);
            root.put("allow_equipment_drops", true);
            root.put("custom_drops", List.of(
                Map.of(
                    "item", "minecraft:iron_ingot",
                    "min_level", 15,
                    "chance", 0.10,
                    "amount", "1-2"
                ),
                Map.of(
                    "item", "minecraft:diamond",
                    "min_level", 40,
                    "chance", 0.02,
                    "amount", "1"
                ),
                Map.of(
                    "item", "minecraft:emerald",
                    "min_level", 25,
                    "chance", 0.05,
                    "amount", "1"
                )
            ));
            GSON.toJson(root, writer);
        } catch (Exception e) {
            LOGGER.error("Failed to write default drops.json", e);
        }
    }

    private void writeDefaultMessages(File file) {
        try (FileWriter writer = new FileWriter(file)) {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("prefix", "<yellow>[LevelledMobs]</yellow> ");
            root.put("reloaded", "<green>Configuration and rules successfully reloaded.</green>");
            root.put("inspect_format", "<yellow>Entity:</yellow> <white>%entity%</white> | <yellow>Level:</yellow> <gold>%level%</gold> | <yellow>Rule:</yellow> <aqua>%rule%</aqua>");
            root.put("no_permission", "<red>You do not have permission to execute this command.</red>");
            GSON.toJson(root, writer);
        } catch (Exception e) {
            LOGGER.error("Failed to write default messages.json", e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<LevelRule> loadRules(File file) {
        List<LevelRule> rules = new ArrayList<>();
        if (!file.exists()) {
            return rules;
        }

        try (FileReader reader = new FileReader(file)) {
            Map<String, Object> root = GSON.fromJson(reader, Map.class);
            if (root != null) {
                for (Map.Entry<String, Object> entry : root.entrySet()) {
                    if (entry.getValue() instanceof Map<?, ?> map) {
                        LevelRule rule = RuleParser.parseRule(entry.getKey(), (Map<String, Object>) map);
                        rules.add(rule);
                    }
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
