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

    // Player overrides actuallyHurt without calling super, so damage is scaled on hurtServer, which every player and
    // mob hurt reaches. For players this runs after Player.hurtServer's difficulty scaling.
    @ModifyVariable(
        method = "hurtServer",
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
