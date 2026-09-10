package lampas.levelledmobs.mixin;

import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import lampas.levelledmobs.drops.XpScalingService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin scaling death XP rewards.
 *
 * <p>Configured LevelledMobs custom drops remain dormant by policy. There is
 * deliberately no injection into vanilla's custom-death-loot method, so the
 * ordinary vanilla drop pipeline remains responsible for ordinary drops.</p>
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

}
