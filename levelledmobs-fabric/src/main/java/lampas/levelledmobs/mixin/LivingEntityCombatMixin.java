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
}
