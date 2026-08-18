package lampas.levelledmobs.mixin;

import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements LevelledMobHolder {
    @Unique
    private LevelledMobData lampas$levelData = LevelledMobData.EMPTY;

    @Override
    public LevelledMobData lampas$getLevelData() {
        return this.lampas$levelData;
    }

    @Override
    public void lampas$setLevelData(LevelledMobData data) {
        this.lampas$levelData = (data != null) ? data : LevelledMobData.EMPTY;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void lampas$writeLevelDataToNbt(ValueOutput output, CallbackInfo ci) {
        if (this.lampas$levelData != null && this.lampas$levelData.levelled()) {
            output.putInt("lampas:level", this.lampas$levelData.level());
            output.putBoolean("lampas:levelled", this.lampas$levelData.levelled());
            if (this.lampas$levelData.ruleSet() != null) {
                output.putString("lampas:rule", this.lampas$levelData.ruleSet());
            }
            output.putLong("lampas:generated_at", this.lampas$levelData.generatedAt());
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void lampas$readLevelDataFromNbt(ValueInput input, CallbackInfo ci) {
        Optional<Integer> levelOpt = input.getInt("lampas:level");
        if (levelOpt.isPresent()) {
            int level = levelOpt.get();
            boolean levelled = input.getBooleanOr("lampas:levelled", true);
            String rule = input.getStringOr("lampas:rule", "default");
            long generatedAt = input.getLongOr("lampas:generated_at", System.currentTimeMillis());
            this.lampas$levelData = new LevelledMobData(level, levelled, rule, generatedAt);
        }
    }
}
