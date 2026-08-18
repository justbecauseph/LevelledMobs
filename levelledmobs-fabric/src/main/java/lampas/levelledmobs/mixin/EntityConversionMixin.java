package lampas.levelledmobs.mixin;

import lampas.levelledmobs.LevelledMobsModule;
import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Preserves LevelledMobData when mobs transform or convert (e.g. Zombie to Drowned, Villager to Zombie Villager).
 */
@Mixin(Mob.class)
public abstract class EntityConversionMixin {

    @Inject(
        method = "convertTo",
        at = @At("RETURN")
    )
    private <T extends Mob> void lampas$preserveLevelOnConversion(
        EntityType<T> entityType,
        ConversionParams conversionParams,
        CallbackInfoReturnable<T> cir
    ) {
        T converted = cir.getReturnValue();
        if (converted == null) return;

        Mob original = (Mob) (Object) this;
        if (original instanceof LevelledMobHolder origHolder && converted instanceof LevelledMobHolder convHolder) {
            LevelledMobData data = origHolder.lampas$getLevelData();
            if (data != null && data.levelled()) {
                convHolder.lampas$setLevelData(data);
                if (LevelledMobsModule.getMobLevelingService() != null) {
                    LevelledMobsModule.getMobLevelingService().setLevel(converted, data.level(), data.ruleSet());
                }
            }
        }
    }
}
