package lampas.levelledmobs.mixin;

import lampas.levelledmobs.events.CombatHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityCombatMixin {

    @ModifyVariable(
        method = "actuallyHurt",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private float lampas$modifyIncomingDamage(float amount, ServerLevel level, DamageSource damageSource) {
        return CombatHandler.modifyDamage((LivingEntity) (Object) this, damageSource, amount);
    }

    @org.spongepowered.asm.mixin.injection.Inject(
        method = "actuallyHurt",
        at = @At("TAIL")
    )
    private void lampas$updateNametagOnDamage(ServerLevel level, DamageSource damageSource, float amount, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (lampas.levelledmobs.LevelledMobsModule.getInstance() != null && lampas.levelledmobs.LevelledMobsModule.getInstance().getNametagService() != null) {
            if (entity instanceof lampas.levelledmobs.data.LevelledMobHolder holder) {
                lampas.levelledmobs.data.LevelledMobData data = holder.lampas$getLevelData();
                if (data != null && data.levelled() && data.level() > 0) {
                    lampas.levelledmobs.LevelledMobsModule.getInstance().getNametagService().updateNametag(entity, data.level(), data.ruleSet());
                }
            }
        }
    }
}
