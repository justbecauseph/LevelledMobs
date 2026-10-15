package lampas.levelledmobs.mixin;

import lampas.levelledmobs.LevelledMobsModule;
import lampas.levelledmobs.context.MobContext;
import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import lampas.levelledmobs.data.SpawnReason;
import lampas.levelledmobs.drops.XpScalingService;
import lampas.levelledmobs.level.SpawnReasonResolver;
import lampas.levelledmobs.rules.RuleManager;
import lampas.levelledmobs.rules.RuleResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

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
                int scaledXp = XpScalingService.calculateXp(originalXp, data.level(), lampas$xpSettings((LivingEntity) (Object) this));
                cir.setReturnValue(scaledXp);
            }
        }
    }

    /**
     * Resolves the rule the same way the levelling path does and returns its merged xp settings. An empty map makes
     * XpScalingService fall back to the global default multiplier.
     */
    @Unique
    private static Map<String, Object> lampas$xpSettings(LivingEntity entity) {
        RuleManager rules = LevelledMobsModule.getRuleManager();
        if (rules == null) {
            return Map.of();
        }
        RuleResult result = rules.resolve(MobContext.of(entity, SpawnReasonResolver.infer(entity, SpawnReason.NATURAL)));
        return result.matched() ? result.effectiveRule().xpSettings() : Map.of();
    }

}
