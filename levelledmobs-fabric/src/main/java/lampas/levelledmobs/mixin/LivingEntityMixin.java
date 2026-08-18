package lampas.levelledmobs.mixin;

import lampas.levelledmobs.data.LevelledMobData;
import lampas.levelledmobs.data.LevelledMobHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
    private void lampas$writeLevelDataToNbt(CompoundTag tag, CallbackInfo ci) {
        if (this.lampas$levelData != null && this.lampas$levelData.levelled()) {
            tag.putInt("lampas:level", this.lampas$levelData.level());
            tag.putBoolean("lampas:levelled", this.lampas$levelData.levelled());
            if (this.lampas$levelData.ruleSet() != null) {
                tag.putString("lampas:rule", this.lampas$levelData.ruleSet());
            }
            tag.putLong("lampas:generated_at", this.lampas$levelData.generatedAt());
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void lampas$readLevelDataFromNbt(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("lampas:level")) {
            int level = tag.getInt("lampas:level").orElse(0);
            boolean levelled = tag.getBoolean("lampas:levelled").orElse(false);
            String rule = tag.getString("lampas:rule").orElse("default");
            long generatedAt = tag.getLong("lampas:generated_at").orElse(System.currentTimeMillis());
            this.lampas$levelData = new LevelledMobData(level, levelled, rule, generatedAt);
        }
    }
}
