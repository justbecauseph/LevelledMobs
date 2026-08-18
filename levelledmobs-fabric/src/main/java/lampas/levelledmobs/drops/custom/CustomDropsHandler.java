package lampas.levelledmobs.drops.custom;

import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles custom item drops generation upon mob death.
 */
public class CustomDropsHandler {
    private static final List<CustomDropItem> globalCustomDrops = new ArrayList<>();

    public static void registerGlobalDrop(CustomDropItem dropItem) {
        if (dropItem != null) {
            globalCustomDrops.add(dropItem);
        }
    }

    public static void clearGlobalDrops() {
        globalCustomDrops.clear();
    }

    public static List<ItemStack> generateCustomDrops(LivingEntity entity, int mobLevel) {
        if (entity == null || mobLevel <= 0) {
            return List.of();
        }

        List<ItemStack> drops = new ArrayList<>();
        for (CustomDropItem item : globalCustomDrops) {
            if (item.isEligible(mobLevel)) {
                ItemStack stack = item.createItemStack(mobLevel);
                if (!stack.isEmpty()) {
                    drops.add(stack);
                }
            }
        }
        return drops;
    }

    public static void handleDeathDrops(LivingEntity entity, ServerLevel level, DamageSource source) {
        if (entity == null || level == null) return;

        if (entity instanceof LevelledMobHolder holder) {
            LevelledMobData data = holder.lampas$getLevelData();
            if (data != null && data.levelled() && data.level() > 0) {
                List<ItemStack> customDrops = generateCustomDrops(entity, data.level());
                for (ItemStack stack : customDrops) {
                    entity.spawnAtLocation(level, stack);
                }
            }
        }
    }
}
