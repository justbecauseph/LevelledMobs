package lampas.levelledmobs.drops.custom;

import lampas.levelledmobs.rules.IntRange;
import lampas.levelledmobs.rules.strategy.math.MinAndMax;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses custom drop rules from configuration maps.
 */
public class CustomDropsParser {

    public static List<CustomDropItem> parseDrops(Object obj) {
        if (!(obj instanceof Map<?, ?> map)) {
            return List.of();
        }

        List<CustomDropItem> items = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> dropDef) {
                CustomDropItem item = parseSingleDrop(dropDef);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        return items;
    }

    public static CustomDropItem parseSingleDrop(Map<?, ?> map) {
        String itemStr = map.containsKey("item") ? String.valueOf(map.get("item")) : "";
        if (itemStr.isBlank()) {
            return null;
        }

        Identifier itemId;
        if (itemStr.contains(":")) {
            String[] parts = itemStr.split(":", 2);
            itemId = Identifier.fromNamespaceAndPath(parts[0], parts[1]);
        } else {
            itemId = Identifier.fromNamespaceAndPath("minecraft", itemStr);
        }

        double chanceVal = getDouble(map.get("chance"), 1.0);
        double chancePerLevel = getDouble(map.get("chance_per_level"), 0.0);
        SlidingChance chance = SlidingChance.scaling(chanceVal, chancePerLevel);

        int minLevel = getInt(map.get("min_level"), getInt(map.get("min-level"), 1));
        int maxLevel = getInt(map.get("max_level"), getInt(map.get("max-level"), Integer.MAX_VALUE));
        IntRange levelRange = IntRange.of(minLevel, maxLevel);

        String amountStr = map.containsKey("amount") ? String.valueOf(map.get("amount")) : "1";
        MinAndMax amountRange = MinAndMax.parse(amountStr);
        if (amountRange == null) {
            amountRange = new MinAndMax(1, 1);
        }

        return new CustomDropItem(itemId, chance, levelRange, amountRange);
    }

    private static double getDouble(Object obj, double def) {
        if (obj instanceof Number num) return num.doubleValue();
        if (obj != null) {
            try { return Double.parseDouble(String.valueOf(obj)); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private static int getInt(Object obj, int def) {
        if (obj instanceof Number num) return num.intValue();
        if (obj != null) {
            try { return Integer.parseInt(String.valueOf(obj)); } catch (NumberFormatException ignored) {}
        }
        return def;
    }
}
