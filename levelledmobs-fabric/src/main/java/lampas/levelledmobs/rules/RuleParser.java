package lampas.levelledmobs.rules;

import lampas.levelledmobs.data.SpawnReason;
import lampas.levelledmobs.rules.conditions.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Parser transforming configuration maps / definitions into immutable LevelRule instances.
 */
public class RuleParser {
    private static final Logger LOGGER = LoggerFactory.getLogger("LevelledMobs");

    @SuppressWarnings("unchecked")
    public static LevelRule parseRule(String ruleId, Map<String, Object> map) {
        if (map == null) {
            return LevelRule.builder(ruleId).build();
        }

        LevelRule.Builder builder = LevelRule.builder(ruleId);

        // 1. Priority and Enabled
        if (map.containsKey("priority")) {
            builder.priority(getInt(map.get("priority"), 0));
        }
        if (map.containsKey("enabled")) {
            builder.enabled(getBoolean(map.get("enabled"), true));
        }

        // 2. Level Range
        int minLevel = 1;
        int maxLevel = 25;
        if (map.containsKey("min-level")) {
            minLevel = getInt(map.get("min-level"), 1);
        } else if (map.containsKey("min_level")) {
            minLevel = getInt(map.get("min_level"), 1);
        }
        if (map.containsKey("max-level")) {
            maxLevel = getInt(map.get("max-level"), 25);
        } else if (map.containsKey("max_level")) {
            maxLevel = getInt(map.get("max_level"), 25);
        }
        builder.levelRange(IntRange.of(minLevel, maxLevel));

        // 3. Strategy
        String strategyName = "RANDOM";
        Map<String, Object> strategyConfig = Collections.emptyMap();
        if (map.containsKey("strategy")) {
            Object stratObj = map.get("strategy");
            if (stratObj instanceof String str) {
                strategyName = str.toUpperCase(Locale.ROOT);
            } else if (stratObj instanceof Map<?, ?> smap) {
                if (smap.containsKey("name")) {
                    strategyName = String.valueOf(smap.get("name")).toUpperCase(Locale.ROOT);
                }
                strategyConfig = (Map<String, Object>) smap;
            }
        }
        builder.strategy(strategyName, strategyConfig);

        // 4. Conditions
        RulePredicate.Builder predicateBuilder = RulePredicate.builder();
        if (map.containsKey("conditions") && map.get("conditions") instanceof Map<?, ?> condMap) {
            parseConditions((Map<String, Object>) condMap, predicateBuilder);
        }
        builder.predicate(predicateBuilder.build());

        // 5. Settings (attributes, drops, xp, nametag)
        if (map.containsKey("attributes") && map.get("attributes") instanceof Map<?, ?> attrs) {
            builder.attributeSettings((Map<String, Object>) attrs);
        }
        if (map.containsKey("drops") && map.get("drops") instanceof Map<?, ?> drops) {
            builder.dropSettings((Map<String, Object>) drops);
        }
        if (map.containsKey("xp") && map.get("xp") instanceof Map<?, ?> xp) {
            builder.xpSettings((Map<String, Object>) xp);
        }
        if (map.containsKey("nametag") && map.get("nametag") instanceof Map<?, ?> nametag) {
            builder.nametagSettings((Map<String, Object>) nametag);
        }

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static void parseConditions(Map<String, Object> condMap, RulePredicate.Builder builder) {
        // Entity conditions
        if (condMap.containsKey("entities") && condMap.get("entities") instanceof Map<?, ?> entMap) {
            Map<String, Object> em = (Map<String, Object>) entMap;
            Set<Identifier> incEnts = parseIdentifiers(getList(em.get("include")));
            Set<Identifier> excEnts = parseIdentifiers(getList(em.get("exclude")));
            Set<TagKey<EntityType<?>>> incTags = parseTags(Registries.ENTITY_TYPE, getList(em.get("include-tags")));
            Set<TagKey<EntityType<?>>> excTags = parseTags(Registries.ENTITY_TYPE, getList(em.get("exclude-tags")));

            builder.add(new EntityCondition(incEnts, excEnts, incTags, excTags));
        }

        // Biome conditions
        if (condMap.containsKey("biomes") && condMap.get("biomes") instanceof Map<?, ?> bioMap) {
            Map<String, Object> bm = (Map<String, Object>) bioMap;
            Set<ResourceKey<Biome>> incBiomes = parseResourceKeys(Registries.BIOME, getList(bm.get("include")));
            Set<ResourceKey<Biome>> excBiomes = parseResourceKeys(Registries.BIOME, getList(bm.get("exclude")));
            Set<TagKey<Biome>> incTags = parseTags(Registries.BIOME, getList(bm.get("include-tags")));
            Set<TagKey<Biome>> excTags = parseTags(Registries.BIOME, getList(bm.get("exclude-tags")));

            builder.add(new BiomeCondition(incBiomes, excBiomes, incTags, excTags));
        }

        // Dimension / World conditions
        if (condMap.containsKey("dimensions") || condMap.containsKey("worlds")) {
            Object dimObj = condMap.containsKey("dimensions") ? condMap.get("dimensions") : condMap.get("worlds");
            if (dimObj instanceof List<?> list) {
                Set<ResourceKey<Level>> incDims = parseResourceKeys(Registries.DIMENSION, (List<Object>) list);
                builder.add(new DimensionCondition(incDims, Collections.emptySet()));
            } else if (dimObj instanceof Map<?, ?> dm) {
                Map<String, Object> dmap = (Map<String, Object>) dm;
                Set<ResourceKey<Level>> incDims = parseResourceKeys(Registries.DIMENSION, getList(dmap.get("include")));
                Set<ResourceKey<Level>> excDims = parseResourceKeys(Registries.DIMENSION, getList(dmap.get("exclude")));
                builder.add(new DimensionCondition(incDims, excDims));
            }
        }

        // Altitude conditions
        if (condMap.containsKey("altitude") || condMap.containsKey("y")) {
            int minY = Integer.MIN_VALUE;
            int maxY = Integer.MAX_VALUE;
            Object altObj = condMap.containsKey("altitude") ? condMap.get("altitude") : condMap.get("y");
            if (altObj instanceof Map<?, ?> am) {
                Map<String, Object> amap = (Map<String, Object>) am;
                if (amap.containsKey("min")) minY = getInt(amap.get("min"), Integer.MIN_VALUE);
                if (amap.containsKey("max")) maxY = getInt(amap.get("max"), Integer.MAX_VALUE);
                if (amap.containsKey("min-y")) minY = getInt(amap.get("min-y"), Integer.MIN_VALUE);
                if (amap.containsKey("max-y")) maxY = getInt(amap.get("max-y"), Integer.MAX_VALUE);
            }
            builder.add(new AltitudeCondition(minY, maxY));
        }

        // Spawn reason conditions
        if (condMap.containsKey("spawn-reasons") || condMap.containsKey("spawn_reasons")) {
            Object srObj = condMap.containsKey("spawn-reasons") ? condMap.get("spawn-reasons") : condMap.get("spawn_reasons");
            Set<SpawnReason> incReasons = new HashSet<>();
            Set<SpawnReason> excReasons = new HashSet<>();
            if (srObj instanceof List<?> list) {
                for (Object item : list) {
                    parseSpawnReason(item).ifPresent(incReasons::add);
                }
            } else if (srObj instanceof Map<?, ?> srm) {
                Map<String, Object> srmap = (Map<String, Object>) srm;
                for (Object item : getList(srmap.get("include"))) {
                    parseSpawnReason(item).ifPresent(incReasons::add);
                }
                for (Object item : getList(srmap.get("exclude"))) {
                    parseSpawnReason(item).ifPresent(excReasons::add);
                }
            }
            builder.add(new SpawnReasonCondition(incReasons, excReasons));
        }
    }

    private static Optional<SpawnReason> parseSpawnReason(Object obj) {
        if (obj == null) return Optional.empty();
        String str = String.valueOf(obj).toUpperCase(Locale.ROOT);
        try {
            return Optional.of(SpawnReason.valueOf(str));
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Unknown SpawnReason in configuration: {}", str);
            return Optional.empty();
        }
    }

    private static Set<Identifier> parseIdentifiers(List<Object> list) {
        if (list == null || list.isEmpty()) return Collections.emptySet();
        Set<Identifier> set = new HashSet<>();
        for (Object item : list) {
            if (item != null) {
                try {
                    String str = String.valueOf(item);
                    String[] parts = str.contains(":") ? str.split(":", 2) : new String[]{"minecraft", str};
                    set.add(Identifier.fromNamespaceAndPath(parts[0], parts[1]));
                } catch (Exception e) {
                    LOGGER.warn("Failed to parse identifier: {}", item, e);
                }
            }
        }
        return set;
    }

    private static <T> Set<ResourceKey<T>> parseResourceKeys(ResourceKey<? extends net.minecraft.core.Registry<T>> registryKey, List<Object> list) {
        if (list == null || list.isEmpty()) return Collections.emptySet();
        Set<ResourceKey<T>> set = new HashSet<>();
        for (Object item : list) {
            if (item != null) {
                try {
                    String str = String.valueOf(item);
                    String[] parts = str.contains(":") ? str.split(":", 2) : new String[]{"minecraft", str};
                    Identifier id = Identifier.fromNamespaceAndPath(parts[0], parts[1]);
                    set.add(ResourceKey.create(registryKey, id));
                } catch (Exception e) {
                    LOGGER.warn("Failed to parse resource key: {}", item, e);
                }
            }
        }
        return set;
    }

    private static <T> Set<TagKey<T>> parseTags(ResourceKey<? extends net.minecraft.core.Registry<T>> registryKey, List<Object> list) {
        if (list == null || list.isEmpty()) return Collections.emptySet();
        Set<TagKey<T>> set = new HashSet<>();
        for (Object item : list) {
            if (item != null) {
                try {
                    String str = String.valueOf(item);
                    if (str.startsWith("#")) str = str.substring(1);
                    String[] parts = str.contains(":") ? str.split(":", 2) : new String[]{"minecraft", str};
                    Identifier id = Identifier.fromNamespaceAndPath(parts[0], parts[1]);
                    set.add(TagKey.create(registryKey, id));
                } catch (Exception e) {
                    LOGGER.warn("Failed to parse tag: {}", item, e);
                }
            }
        }
        return set;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> getList(Object obj) {
        if (obj instanceof List<?> list) {
            return (List<Object>) list;
        }
        return Collections.emptyList();
    }

    private static int getInt(Object obj, int def) {
        if (obj instanceof Number num) return num.intValue();
        if (obj != null) {
            try { return Integer.parseInt(String.valueOf(obj)); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private static boolean getBoolean(Object obj, boolean def) {
        if (obj instanceof Boolean b) return b;
        if (obj != null) return Boolean.parseBoolean(String.valueOf(obj));
        return def;
    }
}
