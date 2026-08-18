package lampas.levelledmobs.drops.equipment;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service managing equipment assignments and drop chances for levelled mobs on spawn.
 */
public class MobEquipmentService {

    /**
     * Equips a mob with tier-appropriate gear based on level and equipment options map.
     */
    public static void applyEquipment(Mob mob, int mobLevel, Map<String, Object> equipmentConfig) {
        if (mob == null || mobLevel <= 1 || equipmentConfig == null || equipmentConfig.isEmpty()) {
            return;
        }

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            String slotKey = slot.getName().toLowerCase();
            if (equipmentConfig.containsKey(slotKey)) {
                Object slotDef = equipmentConfig.get(slotKey);
                if (slotDef instanceof Map<?, ?> itemMap) {
                    equipSlot(mob, slot, mobLevel, itemMap);
                }
            }
        }
    }

    private static void equipSlot(Mob mob, EquipmentSlot slot, int mobLevel, Map<?, ?> itemMap) {
        String itemStr = itemMap.containsKey("item") ? String.valueOf(itemMap.get("item")) : "";
        if (itemStr.isBlank()) return;

        double chance = getDouble(itemMap.get("chance"), 1.0);
        if (chance < 1.0 && ThreadLocalRandom.current().nextDouble() > chance) {
            return;
        }

        Identifier itemId = itemStr.contains(":") ?
            Identifier.fromNamespaceAndPath(itemStr.split(":", 2)[0], itemStr.split(":", 2)[1]) :
            Identifier.fromNamespaceAndPath("minecraft", itemStr);

        Item item = BuiltInRegistries.ITEM.get(itemId)
            .map(net.minecraft.core.Holder.Reference::value)
            .orElse(Items.AIR);

        if (item != Items.AIR) {
            ItemStack stack = new ItemStack(item);
            mob.setItemSlot(slot, stack);

            float dropChance = (float) getDouble(itemMap.get("drop_chance"), 0.085);
            mob.setDropChance(slot, dropChance);
        }
    }

    private static double getDouble(Object obj, double def) {
        if (obj instanceof Number num) return num.doubleValue();
        if (obj != null) {
            try { return Double.parseDouble(String.valueOf(obj)); } catch (NumberFormatException ignored) {}
        }
        return def;
    }
}
