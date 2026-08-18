package lampas.levelledmobs.drops.custom;

import lampas.levelledmobs.rules.IntRange;
import lampas.levelledmobs.rules.strategy.math.MinAndMax;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Definition of a custom item drop configured for levelled mobs.
 */
public record CustomDropItem(
    Identifier itemId,
    SlidingChance chance,
    IntRange levelRange,
    MinAndMax amountRange
) {
    public boolean isEligible(int mobLevel) {
        if (levelRange != null && !levelRange.contains(mobLevel)) {
            return false;
        }
        return chance != null && chance.roll(mobLevel);
    }

    public ItemStack createItemStack(int mobLevel) {
        Item item = BuiltInRegistries.ITEM.get(itemId)
            .map(net.minecraft.core.Holder.Reference::value)
            .orElse(Items.AIR);

        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }

        int count = 1;
        if (amountRange != null) {
            int min = amountRange.minAsInt();
            int max = amountRange.maxAsInt();
            count = (max > min) ? ThreadLocalRandom.current().nextInt(min, max + 1) : min;
        }

        return new ItemStack(item, Math.max(1, count));
    }
}
