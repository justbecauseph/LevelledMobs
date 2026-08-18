package lampas.levelledmobs.config.legacy;

import lampas.levelledmobs.rules.IntRange;
import lampas.levelledmobs.rules.LevelRule;
import lampas.levelledmobs.rules.conditions.*;
import net.minecraft.resources.Identifier;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.*;

/**
 * Importer and translator that parses legacy Bukkit LevelledMobs rules.yml configs
 * into modern LampasCore LevelRule structures.
 */
public class LegacyLevelledMobsConfigImporter {

    public static List<LevelRule> importYaml(String yamlContent) {
        if (yamlContent == null || yamlContent.isBlank()) {
            return List.of();
        }

        List<LevelRule> imported = new ArrayList<>();
        Map<String, Map<String, Object>> parsedSections = parseSimpleYamlSections(yamlContent);

        for (Map.Entry<String, Map<String, Object>> entry : parsedSections.entrySet()) {
            String ruleId = entry.getKey();
            Map<String, Object> data = entry.getValue();

            LevelRule.Builder builder = LevelRule.builder(ruleId);

            // Priority
            if (data.containsKey("priority")) {
                builder.priority(getInt(data.get("priority"), 1));
            }

            // Strategy
            String strategy = String.valueOf(data.getOrDefault("strategy", "RANDOM")).toUpperCase();
            builder.strategy(strategy, null);

            // Min & Max level
            int minLevel = getInt(data.get("min-level"), getInt(data.get("min_level"), 1));
            int maxLevel = getInt(data.get("max-level"), getInt(data.get("max_level"), 25));
            builder.levelRange(IntRange.of(minLevel, maxLevel));

            // Entity condition
            if (data.containsKey("entities")) {
                List<String> entityNames = parseStringList(data.get("entities"));
                Set<Identifier> ids = new HashSet<>();
                for (String name : entityNames) {
                    if (name.contains(":")) {
                        ids.add(Identifier.fromNamespaceAndPath(name.split(":")[0], name.split(":")[1]));
                    } else {
                        ids.add(Identifier.fromNamespaceAndPath("minecraft", name.toLowerCase()));
                    }
                }
                if (!ids.isEmpty()) {
                    builder.condition(new EntityCondition(ids, Set.of(), Set.of(), Set.of()));
                }
            }

            imported.add(builder.build());
        }

        return imported;
    }

    private static Map<String, Map<String, Object>> parseSimpleYamlSections(String yaml) {
        Map<String, Map<String, Object>> sections = new LinkedHashMap<>();
        String currentSection = null;
        Map<String, Object> currentMap = null;

        try (BufferedReader reader = new BufferedReader(new StringReader(yaml))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.endsWith(":") && !line.startsWith("-")) {
                    currentSection = line.substring(0, line.length() - 1).trim();
                    currentMap = new LinkedHashMap<>();
                    sections.put(currentSection, currentMap);
                } else if (currentMap != null && line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    String key = parts[0].trim();
                    String val = parts[1].trim();
                    currentMap.put(key, val);
                }
            }
        } catch (Exception ignored) {}

        return sections;
    }

    private static List<String> parseStringList(Object obj) {
        if (obj instanceof List<?> list) {
            List<String> res = new ArrayList<>();
            for (Object item : list) res.add(String.valueOf(item));
            return res;
        }
        if (obj instanceof String str) {
            if (str.startsWith("[") && str.endsWith("]")) {
                str = str.substring(1, str.length() - 1);
            }
            return Arrays.stream(str.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        }
        return List.of();
    }

    private static int getInt(Object obj, int def) {
        if (obj instanceof Number n) return n.intValue();
        if (obj != null) {
            try { return Integer.parseInt(String.valueOf(obj)); } catch (NumberFormatException ignored) {}
        }
        return def;
    }
}
