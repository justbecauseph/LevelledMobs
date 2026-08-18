package lampas.levelledmobs.mixin;

import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import lampas.levelledmobs.drops.XpScalingService;
import lampas.levelledmobs.drops.custom.CustomDropsHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin scaling death XP rewards and triggering custom drop tables upon mob death.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDeathMixin {

    @Inject(
        method = "getExperienceReward",
        at = @At("RETURN"),
        cancellable = true
    )
    private void lampas$scaleDeathXp(ServerLevel level, Entity attackingPlayer, CallbackInfoReturnable<Integer> cir) {
        int originalXp = cir.getReturnValue();
        if (originalXp > 0 && ((LivingEntity) (Object) this) instanceof LevelledMobHolder holder) {
            LevelledMobData data = holder.lampas$getLevelData();
            if (data != null && data.levelled() && data.level() > 1) {
                int scaledXp = XpScalingService.calculateXp(originalXp, data.level());
                cir.setReturnValue(scaledXp);
            }
        }
    }

    @Inject(
        method = "dropCustomDeathLoot",
        at = @At("TAIL")
    )
    private void lampas$handleCustomDeathDrops(ServerLevel level, DamageSource damageSource, boolean hitByPlayer, CallbackInfo ci) {
        CustomDropsHandler.handleDeathDrops((LivingEntity) (Object) this, level, damageSource);
    }
}
